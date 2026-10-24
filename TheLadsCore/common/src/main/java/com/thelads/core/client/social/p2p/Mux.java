package com.thelads.core.client.social.p2p;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Multiplexes multiple independent streams over a single underlying ReliableStream.
 * Stream IDs: 0 = Minecraft connection, 1 = control (high-priority, unthrottled). No other id exists (Phase 3's pack transfers will
 * open more, up to 8 in all, through a control-stream handshake). A frame for another id, a frame longer than MAX_CHUNK_SIZE or data
 * beyond a stream's buffer is a protocol violation: the Mux closes, which ends the session. Receive buffers are allocated on first data.
 *
 * Implements per-stream credit-based flow control (512 KB window) to prevent
 * head-of-line blocking in the dispatcher thread.
 */
public final class Mux implements AutoCloseable {
    public static final int FRAME_DATA          = 0x01;
    public static final int FRAME_CLOSE         = 0x02;
    public static final int FRAME_RESET         = 0x03;
    public static final int FRAME_WINDOW_UPDATE = 0x04;

    public static final int MAX_CHUNK_SIZE = 4096;
    public static final int INITIAL_WINDOW_SIZE = 512 * 1024; // 512 KB
    public static final int WINDOW_UPDATE_THRESHOLD = 64 * 1024; // 64 KB
    static final int STREAMS = 2;
    static final int CONTROL_BUFFER = 64 * 1024; // the control stream has no credit flow: lines are tiny

    private final InputStream underIn;
    private final OutputStream underOut;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    private final MuxStream[] streams = new MuxStream[STREAMS];
    private final Thread dispatcherThread;

    public Mux(InputStream underIn, OutputStream underOut) {
        this.underIn = underIn;
        this.underOut = underOut;
        for (int id = 0; id < STREAMS; id++) streams[id] = new MuxStream(id, this);

        dispatcherThread = new Thread(this::runDispatcher, "Mux-Dispatcher");
        dispatcherThread.setDaemon(true);
        dispatcherThread.setPriority(Thread.MIN_PRIORITY + 1);
        dispatcherThread.start();
    }

    private MuxStream stream(int streamId) {
        if (streamId < 0 || streamId >= STREAMS) throw new IllegalArgumentException("No mux stream " + streamId);
        return streams[streamId];
    }

    public InputStream in(int streamId) {
        return stream(streamId).getInputStream();
    }

    public OutputStream out(int streamId) {
        return stream(streamId).getOutputStream();
    }

    private void runDispatcher() {
        byte[] header = new byte[5];
        byte[] payloadBuf = new byte[MAX_CHUNK_SIZE];

        while (!closed.get()) {
            try {
                // Read 5-byte header: streamId (2), type (1), len (2)
                readFully(underIn, header, 0, 5);

                int streamId = StunMessage.getShortBE(header, 0) & 0xFFFF;
                int type = header[2] & 0xFF;
                int len = StunMessage.getShortBE(header, 3) & 0xFFFF;
                if (streamId >= STREAMS || len > MAX_CHUNK_SIZE) break; // protocol violation: nothing allocated, the session ends

                if (len > 0) {
                    readFully(underIn, payloadBuf, 0, len);
                }

                MuxStream stream = streams[streamId];
                if (type == FRAME_DATA) {
                    if (!stream.receiveData(payloadBuf, 0, len)) break; // more than the peer's credit (or the control buffer) allows
                } else if (type == FRAME_CLOSE) {
                    stream.receiveClose();
                } else if (type == FRAME_RESET) {
                    stream.receiveReset();
                } else if (type == FRAME_WINDOW_UPDATE) {
                    if (len >= 4) {
                        int credit = StunMessage.getIntBE(payloadBuf, 0);
                        stream.addSendCredit(credit);
                    }
                }
            } catch (Exception e) {
                // Underlying stream closed or EOF
                break;
            }
        }

        close(); // EOF, error or violation: every stream ends
    }

    private static void readFully(InputStream in, byte[] b, int off, int len) throws IOException {
        int n = 0;
        while (n < len) {
            int count = in.read(b, off + n, len - n);
            if (count < 0) {
                throw new EOFException("Premature EOF reading Mux frame");
            }
            n += count;
        }
    }

    void writeFrame(int streamId, int type, byte[] data, int off, int len) throws IOException {
        if (closed.get()) throw new IOException("Mux is closed");

        synchronized (underOut) {
            byte[] hdr = new byte[5];
            StunMessage.putShortBE(hdr, 0, (short) streamId);
            hdr[2] = (byte) (type & 0xFF);
            StunMessage.putShortBE(hdr, 3, (short) len);

            underOut.write(hdr);
            if (len > 0) {
                underOut.write(data, off, len);
            }
            underOut.flush();
        }
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            for (MuxStream s : streams) {
                s.receiveClose();
            }
            try {
                underIn.close();
            } catch (Exception ignored) {
            }
            try {
                underOut.close();
            } catch (Exception ignored) {
            }
            dispatcherThread.interrupt();
        }
    }

    private static class MuxStream {
        private final int streamId;
        private final Mux mux;

        // RX state
        private final ReentrantLock lock = new ReentrantLock();
        private final Condition dataCond = lock.newCondition();
        // Ring buffer, allocated on first data: the 512 KB credit window + headroom (control: CONTROL_BUFFER)
        private byte[] ring;
        private int head = 0;
        private int tail = 0;
        private int count = 0;
        private int unackConsumed = 0;
        private boolean eof = false;
        private boolean reset = false;

        // TX flow control credit
        private final ReentrantLock sendLock = new ReentrantLock();
        private final Condition creditCond = sendLock.newCondition();
        private long sendCredit = INITIAL_WINDOW_SIZE;

        private final byte[] singleReadBuf = new byte[1];
        private final byte[] singleWriteBuf = new byte[1];

        private final InputStream in = new InputStream() {
            @Override
            public int read() throws IOException {
                synchronized (singleReadBuf) {
                    int n = read(singleReadBuf, 0, 1);
                    return n == -1 ? -1 : (singleReadBuf[0] & 0xFF);
                }
            }

            @Override
            public int read(byte[] b, int off, int len) throws IOException {
                if (len == 0) return 0;

                int updateToSend = 0;
                int toRead;

                lock.lock();
                try {
                    while (count == 0) {
                        if (reset) throw new IOException("Stream " + streamId + " was reset");
                        if (eof) return -1;
                        if (mux.closed.get()) return -1;
                        try {
                            dataCond.await();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new IOException("Read interrupted", e);
                        }
                    }

                    toRead = Math.min(len, count);
                    for (int i = 0; i < toRead; i++) {
                        b[off + i] = ring[head];
                        head = (head + 1) % ring.length;
                    }
                    count -= toRead;
                    unackConsumed += toRead;

                    if (unackConsumed >= WINDOW_UPDATE_THRESHOLD || (count == 0 && unackConsumed > 0)) {
                        updateToSend = unackConsumed;
                        unackConsumed = 0;
                    }
                } finally {
                    lock.unlock();
                }

                // Send WINDOW_UPDATE outside rx lock
                if (updateToSend > 0) {
                    byte[] creditPayload = new byte[4];
                    StunMessage.putIntBE(creditPayload, 0, updateToSend);
                    mux.writeFrame(streamId, FRAME_WINDOW_UPDATE, creditPayload, 0, 4);
                }

                return toRead;
            }
        };

        private final OutputStream out = new OutputStream() {
            @Override
            public void write(int b) throws IOException {
                synchronized (singleWriteBuf) {
                    singleWriteBuf[0] = (byte) b;
                    write(singleWriteBuf, 0, 1);
                }
            }

            @Override
            public void write(byte[] b, int off, int len) throws IOException {
                if (len == 0) return;
                int remaining = len;
                int curOff = off;
                while (remaining > 0) {
                    int chunk = Math.min(remaining, MAX_CHUNK_SIZE);

                    // Flow control: wait for credit unless control stream 1
                    if (streamId != 1) {
                        sendLock.lock();
                        try {
                            while (sendCredit < chunk) {
                                if (mux.closed.get() || reset) {
                                    throw new IOException("Stream " + streamId + " closed or reset");
                                }
                                try {
                                    creditCond.await();
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                    throw new IOException("Write interrupted", e);
                                }
                            }
                            sendCredit -= chunk;
                        } finally {
                            sendLock.unlock();
                        }
                    }

                    mux.writeFrame(streamId, FRAME_DATA, b, curOff, chunk);
                    curOff += chunk;
                    remaining -= chunk;
                }
            }

            @Override
            public void close() throws IOException {
                mux.writeFrame(streamId, FRAME_CLOSE, null, 0, 0);
            }
        };

        MuxStream(int streamId, Mux mux) {
            this.streamId = streamId;
            this.mux = mux;
        }

        InputStream getInputStream() {
            return in;
        }

        OutputStream getOutputStream() {
            return out;
        }

        void addSendCredit(int delta) {
            sendLock.lock();
            try {
                sendCredit += delta;
                creditCond.signalAll();
            } finally {
                sendLock.unlock();
            }
        }

        /** False when the data does not fit: the peer ignored its credit (or flooded the control stream). */
        boolean receiveData(byte[] data, int off, int len) {
            lock.lock();
            try {
                if (eof || reset) return true;
                if (ring == null) ring = new byte[streamId == 1 ? CONTROL_BUFFER : INITIAL_WINDOW_SIZE + MAX_CHUNK_SIZE * 2];
                if (len > ring.length - count) return false;
                for (int i = 0; i < len; i++) {
                    ring[tail] = data[off + i];
                    tail = (tail + 1) % ring.length;
                }
                count += len;
                dataCond.signalAll();
                return true;
            } finally {
                lock.unlock();
            }
        }

        void receiveClose() {
            lock.lock();
            try {
                eof = true;
                dataCond.signalAll();
            } finally {
                lock.unlock();
            }
            sendLock.lock();
            try {
                creditCond.signalAll();
            } finally {
                sendLock.unlock();
            }
        }

        void receiveReset() {
            lock.lock();
            try {
                reset = true;
                dataCond.signalAll();
            } finally {
                lock.unlock();
            }
            sendLock.lock();
            try {
                creditCond.signalAll();
            } finally {
                sendLock.unlock();
            }
        }
    }
}
