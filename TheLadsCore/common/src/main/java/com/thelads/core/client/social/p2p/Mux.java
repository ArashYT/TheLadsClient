package com.thelads.core.client.social.p2p;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Multiplexes multiple independent streams over a single underlying ReliableStream.
 * Stream IDs:
 *   0 = Minecraft connection
 *   1 = Control
 *   2+ = Resource pack transfers / other
 */
public final class Mux implements AutoCloseable {
    public static final int FRAME_DATA  = 0x01;
    public static final int FRAME_CLOSE = 0x02;
    public static final int FRAME_RESET = 0x03;

    public static final int MAX_CHUNK_SIZE = 4096;

    private final InputStream underIn;
    private final OutputStream underOut;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    private final ConcurrentHashMap<Integer, MuxStream> streams = new ConcurrentHashMap<>();
    private final Thread dispatcherThread;

    public Mux(InputStream underIn, OutputStream underOut) {
        this.underIn = underIn;
        this.underOut = underOut;

        dispatcherThread = new Thread(this::runDispatcher, "Mux-Dispatcher");
        dispatcherThread.setDaemon(true);
        dispatcherThread.setPriority(Thread.MIN_PRIORITY + 1);
        dispatcherThread.start();
    }

    private MuxStream getOrCreateStream(int streamId) {
        return streams.computeIfAbsent(streamId, id -> new MuxStream(id, this));
    }

    public InputStream in(int streamId) {
        return getOrCreateStream(streamId).getInputStream();
    }

    public OutputStream out(int streamId) {
        return getOrCreateStream(streamId).getOutputStream();
    }

    private void runDispatcher() {
        byte[] header = new byte[5];
        byte[] payloadBuf = new byte[MAX_CHUNK_SIZE * 2];

        while (!closed.get()) {
            try {
                // Read 5-byte header: streamId (2), type (1), len (2)
                readFully(underIn, header, 0, 5);

                int streamId = ((header[0] & 0xFF) << 8) | (header[1] & 0xFF);
                int type = header[2] & 0xFF;
                int len = ((header[3] & 0xFF) << 8) | (header[4] & 0xFF);

                if (len > payloadBuf.length) {
                    payloadBuf = new byte[len];
                }
                if (len > 0) {
                    readFully(underIn, payloadBuf, 0, len);
                }

                MuxStream stream = getOrCreateStream(streamId);
                if (type == FRAME_DATA) {
                    stream.receiveData(payloadBuf, 0, len);
                } else if (type == FRAME_CLOSE) {
                    stream.receiveClose();
                } else if (type == FRAME_RESET) {
                    stream.receiveReset();
                }
            } catch (Exception e) {
                // Underlying stream closed or EOF
                break;
            }
        }

        // Notify all streams of EOF
        for (MuxStream s : streams.values()) {
            s.receiveClose();
        }
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
            hdr[0] = (byte) ((streamId >> 8) & 0xFF);
            hdr[1] = (byte) (streamId & 0xFF);
            hdr[2] = (byte) (type & 0xFF);
            hdr[3] = (byte) ((len >> 8) & 0xFF);
            hdr[4] = (byte) (len & 0xFF);

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
            for (MuxStream s : streams.values()) {
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

        private final ReentrantLock lock = new ReentrantLock();
        private final Condition dataCond = lock.newCondition();
        private final Condition spaceCond = lock.newCondition();

        // Circular buffer (512 KB capacity)
        private final byte[] ring = new byte[512 * 1024];
        private int head = 0;
        private int tail = 0;
        private int count = 0;
        private boolean eof = false;
        private boolean reset = false;

        private final InputStream in = new InputStream() {
            @Override
            public int read() throws IOException {
                byte[] b = new byte[1];
                int n = read(b, 0, 1);
                return n == -1 ? -1 : (b[0] & 0xFF);
            }

            @Override
            public int read(byte[] b, int off, int len) throws IOException {
                if (len == 0) return 0;

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

                    int toRead = Math.min(len, count);
                    for (int i = 0; i < toRead; i++) {
                        b[off + i] = ring[head];
                        head = (head + 1) % ring.length;
                    }
                    count -= toRead;
                    spaceCond.signalAll();
                    return toRead;
                } finally {
                    lock.unlock();
                }
            }
        };

        private final OutputStream out = new OutputStream() {
            @Override
            public void write(int b) throws IOException {
                write(new byte[]{(byte) b}, 0, 1);
            }

            @Override
            public void write(byte[] b, int off, int len) throws IOException {
                int remaining = len;
                int curOff = off;
                while (remaining > 0) {
                    int chunk = Math.min(remaining, MAX_CHUNK_SIZE);
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

        void receiveData(byte[] data, int off, int len) {
            lock.lock();
            try {
                if (eof || reset) return;

                int remaining = len;
                int curOff = off;
                while (remaining > 0) {
                    while (count == ring.length) {
                        if (eof || reset || mux.closed.get()) return;
                        try {
                            spaceCond.await();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            return;
                        }
                    }

                    int space = ring.length - count;
                    int toCopy = Math.min(remaining, space);
                    for (int i = 0; i < toCopy; i++) {
                        ring[tail] = data[curOff + i];
                        tail = (tail + 1) % ring.length;
                    }
                    count += toCopy;
                    curOff += toCopy;
                    remaining -= toCopy;
                    dataCond.signalAll();
                }
            } finally {
                lock.unlock();
            }
        }

        void receiveClose() {
            lock.lock();
            try {
                eof = true;
                dataCond.signalAll();
                spaceCond.signalAll();
            } finally {
                lock.unlock();
            }
        }

        void receiveReset() {
            lock.lock();
            try {
                reset = true;
                dataCond.signalAll();
                spaceCond.signalAll();
            } finally {
                lock.unlock();
            }
        }
    }
}
