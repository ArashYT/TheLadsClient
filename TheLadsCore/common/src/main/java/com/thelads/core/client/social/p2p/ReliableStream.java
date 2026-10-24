package com.thelads.core.client.social.p2p;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * ARQ reliable ordered byte stream over datagrams.
 * Features:
 * - 1200-byte max datagram payload
 * - Sliding window of 256 packets
 * - Selective ACK (SACK) with fast retransmit
 * - Jacobson/Karels RTO estimation from RTT (min 50 ms)
 * - Keepalive every 5 s, dead after 15 s silence
 * - Zero allocations per packet in steady state (reusable buffer slots)
 * - One reader/receiver thread + one timer thread per session (MIN_PRIORITY + 1, daemon)
 */
public final class ReliableStream implements AutoCloseable {
    public static final int MAX_DATAGRAM_SIZE = 1200;
    public static final int WINDOW_SIZE = 256;
    public static final int WINDOW_MASK = WINDOW_SIZE - 1;
    public static final int BASE_HEADER_SIZE = 16;
    public static final int MAX_SACK_LONGS = 4;
    public static final int MAX_PAYLOAD_SIZE = MAX_DATAGRAM_SIZE - (BASE_HEADER_SIZE + MAX_SACK_LONGS * 8); // 1152 bytes

    public static final int FLAG_PROTOCOL = 0x80;
    public static final int FLAG_DATA     = 0x01;
    public static final int FLAG_ACK      = 0x02;
    public static final int FLAG_PING     = 0x04;
    public static final int FLAG_PONG     = 0x08;
    public static final int FLAG_FIN      = 0x10;

    @FunctionalInterface
    public interface DatagramSender {
        void send(byte[] data, int offset, int length) throws IOException;
    }

    private final DatagramSender sender;
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final AtomicBoolean dead = new AtomicBoolean(false);
    private volatile String closeReason = "Stream closed";

    private static class TxSlot {
        boolean inUse = false;
        boolean sacked = false;
        int seq = 0;
        int dataLen = 0;
        int packetLen = 0;
        long sendTimeMs = 0;
        long slotRto = 50;
        int retransmits = 0;
        final byte[] packetData = new byte[MAX_DATAGRAM_SIZE];
    }

    private static class RxSlot {
        boolean received = false;
        int seq = 0;
        int dataLen = 0;
        int readOffset = 0;
        final byte[] payload = new byte[MAX_PAYLOAD_SIZE];
    }

    private final TxSlot[] txSlots = new TxSlot[WINDOW_SIZE];
    private final RxSlot[] rxSlots = new RxSlot[WINDOW_SIZE];

    // Sender state
    private final ReentrantLock txLock = new ReentrantLock();
    private final Condition txWindowCond = txLock.newCondition();
    private int sndUna = 0;
    private int sndNxt = 0;
    private volatile long lastSentTime = System.currentTimeMillis();

    // RTO state (Jacobson/Karels)
    private long srtt = 0;
    private long rttvar = 0;
    private long baseRto = 50; // ms, min 50

    // Receiver state
    private final ReentrantLock rxLock = new ReentrantLock();
    private final Condition rxDataCond = rxLock.newCondition();
    private int rcvNxt = 0;
    private int rcvRead = 0;
    private boolean finReceived = false;
    private volatile long lastReceivedTime = System.currentTimeMillis();

    // Preallocated buffers for zero-allocation send & receive
    private final long[] rxSackMask = new long[MAX_SACK_LONGS];
    private final long[] txSackMask = new long[MAX_SACK_LONGS];
    private final int[] txRetransmitBuf = new int[32];
    private final int[] timerRetransmitBuf = new int[64];
    private final byte[] preallocatedAckBuf = new byte[BASE_HEADER_SIZE + MAX_SACK_LONGS * 8];
    private final byte[] preallocatedPingBuf = new byte[BASE_HEADER_SIZE];
    private final byte[] preallocatedFinBuf = new byte[BASE_HEADER_SIZE];

    private final ReliableOutputStream outputStream = new ReliableOutputStream();
    private final ReliableInputStream inputStream = new ReliableInputStream();

    private final Thread timerThread;
    private final AtomicLong totalBytesSent = new AtomicLong(0);
    private final AtomicLong totalBytesReceived = new AtomicLong(0);

    public ReliableStream(DatagramSender sender) {
        this.sender = sender;

        for (int i = 0; i < WINDOW_SIZE; i++) {
            txSlots[i] = new TxSlot();
            rxSlots[i] = new RxSlot();
        }

        timerThread = new Thread(this::runTimerLoop, "ReliableStream-Timer");
        timerThread.setDaemon(true);
        timerThread.setPriority(Thread.MIN_PRIORITY + 1);
        timerThread.start();
    }

    public OutputStream getOutputStream() {
        return outputStream;
    }

    public InputStream getInputStream() {
        return inputStream;
    }

    public long getTotalBytesSent() {
        return totalBytesSent.get();
    }

    public long getTotalBytesReceived() {
        return totalBytesReceived.get();
    }

    public boolean isClosed() {
        return closed.get();
    }

    public void onDatagramReceived(byte[] data, int offset, int length) {
        if (length < BASE_HEADER_SIZE) return;

        int flags = data[offset] & 0xFF;
        if ((flags & 0x80) == 0) return;

        lastReceivedTime = System.currentTimeMillis();
        totalBytesReceived.addAndGet(length);

        int sackCount = data[offset + 1] & 0xFF;
        int dataLen = ((data[offset + 2] & 0xFF) << 8) | (data[offset + 3] & 0xFF);
        int seq = StunMessage.getIntBE(data, offset + 4);
        int ack = StunMessage.getIntBE(data, offset + 8);
        int timestamp = StunMessage.getIntBE(data, offset + 12);

        int headerLen = BASE_HEADER_SIZE + sackCount * 8;
        if (dataLen > MAX_PAYLOAD_SIZE || headerLen + dataLen > length) return;

        int actualSacks = Math.min(sackCount, MAX_SACK_LONGS);
        for (int i = 0; i < actualSacks; i++) {
            rxSackMask[i] = StunMessage.getLongBE(data, offset + BASE_HEADER_SIZE + i * 8);
        }

        if ((flags & (FLAG_ACK | FLAG_DATA | FLAG_FIN | FLAG_PONG)) != 0) {
            handleAck(ack, rxSackMask, actualSacks, timestamp);
        }

        if ((flags & FLAG_FIN) != 0) {
            rxLock.lock();
            try {
                finReceived = true;
                rxDataCond.signalAll();
            } finally {
                rxLock.unlock();
            }
            sendAck();
        }

        if ((flags & FLAG_DATA) != 0 && dataLen > 0) {
            handleIncomingData(seq, data, offset + headerLen, dataLen);
        } else if ((flags & FLAG_PING) != 0) {
            sendAck();
        }
    }

    private void handleIncomingData(int seq, byte[] data, int off, int len) {
        if (len > MAX_PAYLOAD_SIZE) return;
        boolean shouldAck = false;
        rxLock.lock();
        try {
            int diff = seq - rcvNxt;
            if (diff >= 0 && diff < WINDOW_SIZE) {
                int slotIdx = seq & WINDOW_MASK;
                RxSlot slot = rxSlots[slotIdx];
                if (!slot.received) {
                    slot.received = true;
                    slot.seq = seq;
                    slot.dataLen = len;
                    slot.readOffset = 0;
                    System.arraycopy(data, off, slot.payload, 0, len);

                    while (true) {
                        RxSlot nextSlot = rxSlots[rcvNxt & WINDOW_MASK];
                        if (nextSlot.received && nextSlot.seq == rcvNxt) {
                            rcvNxt++;
                        } else {
                            break;
                        }
                    }
                    rxDataCond.signalAll();
                    shouldAck = true;
                }
            } else if (diff < 0) {
                // Duplicate packet, acknowledge so sender knows we already received it!
                shouldAck = true;
            }
        } finally {
            rxLock.unlock();
        }

        if (shouldAck) {
            sendAck();
        }
    }

    private void handleAck(int ack, long[] sackMask, int sackCount, int timestamp) {
        int toRetransmitCount = 0;

        txLock.lock();
        try {
            if (timestamp > 0) {
                int nowTs = (int) (System.currentTimeMillis() & 0xFFFFFFFFL);
                long rtt = nowTs - timestamp;
                if (rtt >= 0 && rtt < 10000) {
                    if (srtt == 0) {
                        srtt = rtt;
                        rttvar = rtt / 2;
                    } else {
                        rttvar = (3 * rttvar + Math.abs(srtt - rtt)) / 4;
                        srtt = (7 * srtt + rtt) / 8;
                    }
                    baseRto = Math.max(50, Math.min(3000, srtt + 4 * rttvar));
                }
            }

            int diff = ack - sndUna;
            int unacked = sndNxt - sndUna;

            // Finding 2: Sequence-wraparound-safe ACK range check: sndUna <= ack < sndNxt
            if (unacked > 0 && diff >= 0 && diff < unacked) {
                int countAcked = diff + 1;
                for (int i = 0; i < countAcked; i++) {
                    int seqToFree = sndUna + i;
                    int slotIdx = seqToFree & WINDOW_MASK;
                    TxSlot slot = txSlots[slotIdx];
                    if (slot.inUse && slot.seq == seqToFree) {
                        slot.inUse = false;
                        slot.sacked = false;
                    }
                }
                sndUna = ack + 1;
                txWindowCond.signalAll();
            }

            int highestSacked = ack;
            for (int longIdx = 0; longIdx < sackCount; longIdx++) {
                long mask = sackMask[longIdx];
                if (mask == 0) continue;
                for (int bit = 0; bit < 64; bit++) {
                    if ((mask & (1L << bit)) != 0) {
                        int sackedSeq = ack + 1 + (longIdx * 64) + bit;
                        int sackDiff = sackedSeq - sndUna;
                        int currentUnacked = sndNxt - sndUna;
                        if (currentUnacked > 0 && sackDiff >= 0 && sackDiff < currentUnacked) {
                            TxSlot s = txSlots[sackedSeq & WINDOW_MASK];
                            if (s.inUse && s.seq == sackedSeq) {
                                s.sacked = true;
                                if (sackedSeq - highestSacked > 0) {
                                    highestSacked = sackedSeq;
                                }
                            }
                        }
                    }
                }
            }

            // Immediate Fast Retransmit: only retransmit un-sacked packets that haven't been retransmitted yet!
            if (highestSacked - ack > 0) {
                long now = System.currentTimeMillis();
                for (int s = sndUna; (highestSacked - s) > 0 && toRetransmitCount < txRetransmitBuf.length; s++) {
                    TxSlot slot = txSlots[s & WINDOW_MASK];
                    if (slot.inUse && slot.seq == s && !slot.sacked) {
                        if (slot.retransmits == 0) {
                            txRetransmitBuf[toRetransmitCount++] = s;
                            slot.sendTimeMs = now;
                            slot.retransmits = 1;
                        }
                    }
                }
            }
        } finally {
            txLock.unlock();
        }

        if (toRetransmitCount > 0) {
            for (int i = 0; i < toRetransmitCount; i++) {
                int seq = txRetransmitBuf[i];
                TxSlot slot;
                txLock.lock();
                try {
                    slot = txSlots[seq & WINDOW_MASK];
                    if (!slot.inUse || slot.seq != seq) continue;
                } finally {
                    txLock.unlock();
                }
                try {
                    sender.send(slot.packetData, 0, slot.packetLen);
                    totalBytesSent.addAndGet(slot.packetLen);
                } catch (Exception ignored) {
                }
            }
        }
    }

    private void sendAck() {
        int ack;
        int numSackLongs = 0;

        rxLock.lock();
        try {
            ack = rcvNxt - 1;
            for (int l = 0; l < MAX_SACK_LONGS; l++) {
                txSackMask[l] = 0L;
            }
            for (int i = 0; i < WINDOW_SIZE; i++) {
                int testSeq = rcvNxt + i;
                RxSlot slot = rxSlots[testSeq & WINDOW_MASK];
                if (slot.received && slot.seq == testSeq) {
                    int longIdx = i / 64;
                    int bitIdx = i % 64;
                    txSackMask[longIdx] |= (1L << bitIdx);
                    if (longIdx + 1 > numSackLongs) {
                        numSackLongs = longIdx + 1;
                    }
                }
            }
        } finally {
            rxLock.unlock();
        }

        int len = BASE_HEADER_SIZE + numSackLongs * 8;
        synchronized (preallocatedAckBuf) {
            preallocatedAckBuf[0] = (byte) (FLAG_PROTOCOL | FLAG_ACK);
            preallocatedAckBuf[1] = (byte) numSackLongs;
            preallocatedAckBuf[2] = 0;
            preallocatedAckBuf[3] = 0;

            StunMessage.putIntBE(preallocatedAckBuf, 4, 0);
            StunMessage.putIntBE(preallocatedAckBuf, 8, ack);
            int nowTs = (int) (System.currentTimeMillis() & 0xFFFFFFFFL);
            StunMessage.putIntBE(preallocatedAckBuf, 12, nowTs);

            for (int i = 0; i < numSackLongs; i++) {
                StunMessage.putLongBE(preallocatedAckBuf, BASE_HEADER_SIZE + i * 8, txSackMask[i]);
            }

            try {
                sender.send(preallocatedAckBuf, 0, len);
                totalBytesSent.addAndGet(len);
            } catch (Exception ignored) {
            }
        }
    }

    private void runTimerLoop() {
        while (!closed.get() && !dead.get()) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                break;
            }

            long now = System.currentTimeMillis();

            if (now - lastReceivedTime > 15000) {
                dead.set(true);
                closeReason = "Connection timed out (15s silence)";
                rxLock.lock();
                try {
                    rxDataCond.signalAll();
                } finally {
                    rxLock.unlock();
                }
                txLock.lock();
                try {
                    txWindowCond.signalAll();
                } finally {
                    txLock.unlock();
                }
                break;
            }

            int count = 0;
            txLock.lock();
            try {
                for (int s = sndUna; s < sndNxt && count < timerRetransmitBuf.length; s++) {
                    TxSlot slot = txSlots[s & WINDOW_MASK];
                    if (slot.inUse && slot.seq == s && !slot.sacked) {
                        if (now - slot.sendTimeMs >= slot.slotRto) {
                            timerRetransmitBuf[count++] = s;
                            slot.sendTimeMs = now;
                            slot.slotRto = Math.min(3000, slot.slotRto * 2);
                            slot.retransmits++;
                        }
                    }
                }
            } finally {
                txLock.unlock();
            }

            if (count > 0) {
                for (int i = 0; i < count; i++) {
                    int seq = timerRetransmitBuf[i];
                    TxSlot slot;
                    txLock.lock();
                    try {
                        slot = txSlots[seq & WINDOW_MASK];
                        if (!slot.inUse || slot.seq != seq) continue;
                    } finally {
                        txLock.unlock();
                    }
                    try {
                        sender.send(slot.packetData, 0, slot.packetLen);
                        totalBytesSent.addAndGet(slot.packetLen);
                    } catch (Exception ignored) {
                    }
                }
            }

            if (now - lastSentTime >= 5000) {
                sendPing();
                lastSentTime = now;
            }
        }
    }

    private void sendPing() {
        int ack;
        rxLock.lock();
        try {
            ack = rcvNxt - 1;
        } finally {
            rxLock.unlock();
        }

        synchronized (preallocatedPingBuf) {
            preallocatedPingBuf[0] = (byte) (FLAG_PROTOCOL | FLAG_PING | FLAG_ACK);
            preallocatedPingBuf[1] = 0;
            preallocatedPingBuf[2] = 0;
            preallocatedPingBuf[3] = 0;
            StunMessage.putIntBE(preallocatedPingBuf, 4, 0);
            StunMessage.putIntBE(preallocatedPingBuf, 8, ack);
            int nowTs = (int) (System.currentTimeMillis() & 0xFFFFFFFFL);
            StunMessage.putIntBE(preallocatedPingBuf, 12, nowTs);

            try {
                sender.send(preallocatedPingBuf, 0, BASE_HEADER_SIZE);
                totalBytesSent.addAndGet(BASE_HEADER_SIZE);
            } catch (Exception ignored) {
            }
        }
    }

    private class ReliableOutputStream extends OutputStream {
        private final byte[] stagingBuf = new byte[MAX_PAYLOAD_SIZE];
        private final byte[] singleByteBuf = new byte[1];
        private int stagingPos = 0;

        @Override
        public void write(int b) throws IOException {
            synchronized (singleByteBuf) {
                singleByteBuf[0] = (byte) b;
                write(singleByteBuf, 0, 1);
            }
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            if (closed.get()) throw new IOException(closeReason);
            if (dead.get()) throw new IOException(closeReason);

            int remaining = len;
            int curOff = off;

            while (remaining > 0) {
                int toCopy = Math.min(remaining, MAX_PAYLOAD_SIZE - stagingPos);
                System.arraycopy(b, curOff, stagingBuf, stagingPos, toCopy);
                stagingPos += toCopy;
                curOff += toCopy;
                remaining -= toCopy;

                if (stagingPos == MAX_PAYLOAD_SIZE) {
                    flushPacket();
                }
            }
        }

        @Override
        public void flush() throws IOException {
            if (stagingPos > 0) {
                flushPacket();
            }
        }

        private void flushPacket() throws IOException {
            if (closed.get()) throw new IOException(closeReason);
            if (dead.get()) throw new IOException(closeReason);

            int seq;
            TxSlot slot;
            int payloadLen;

            txLock.lock();
            try {
                while (sndNxt - sndUna >= WINDOW_SIZE) {
                    if (closed.get()) throw new IOException(closeReason);
                    if (dead.get()) throw new IOException(closeReason);
                    try {
                        txWindowCond.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IOException("Write interrupted", e);
                    }
                }

                seq = sndNxt++;
                slot = txSlots[seq & WINDOW_MASK];
                slot.inUse = true;
                slot.sacked = false;
                slot.seq = seq;
                slot.dataLen = stagingPos;
                slot.retransmits = 0;
                slot.slotRto = baseRto;
                slot.sendTimeMs = System.currentTimeMillis();
                payloadLen = stagingPos;

                slot.packetData[0] = (byte) (FLAG_PROTOCOL | FLAG_DATA | FLAG_ACK);
                slot.packetData[1] = 0;
                slot.packetData[2] = (byte) ((payloadLen >> 8) & 0xFF);
                slot.packetData[3] = (byte) (payloadLen & 0xFF);

                StunMessage.putIntBE(slot.packetData, 4, seq);

                int ack;
                rxLock.lock();
                try {
                    ack = rcvNxt - 1;
                } finally {
                    rxLock.unlock();
                }
                StunMessage.putIntBE(slot.packetData, 8, ack);

                int nowTs = (int) (slot.sendTimeMs & 0xFFFFFFFFL);
                StunMessage.putIntBE(slot.packetData, 12, nowTs);

                System.arraycopy(stagingBuf, 0, slot.packetData, BASE_HEADER_SIZE, payloadLen);
                slot.packetLen = BASE_HEADER_SIZE + payloadLen;

                lastSentTime = slot.sendTimeMs;
                stagingPos = 0;
            } finally {
                txLock.unlock();
            }

            sender.send(slot.packetData, 0, slot.packetLen);
            totalBytesSent.addAndGet(slot.packetLen);
        }

        @Override
        public void close() throws IOException {
            flush();
            synchronized (preallocatedFinBuf) {
                preallocatedFinBuf[0] = (byte) (FLAG_PROTOCOL | FLAG_FIN | FLAG_ACK);
                preallocatedFinBuf[1] = 0;
                preallocatedFinBuf[2] = 0;
                preallocatedFinBuf[3] = 0;

                txLock.lock();
                try {
                    StunMessage.putIntBE(preallocatedFinBuf, 4, sndNxt);
                    int nowTs = (int) (System.currentTimeMillis() & 0xFFFFFFFFL);
                    StunMessage.putIntBE(preallocatedFinBuf, 12, nowTs);
                    int ack;
                    rxLock.lock();
                    try {
                        ack = rcvNxt - 1;
                    } finally {
                        rxLock.unlock();
                    }
                    StunMessage.putIntBE(preallocatedFinBuf, 8, ack);
                } finally {
                    txLock.unlock();
                }
                try {
                    sender.send(preallocatedFinBuf, 0, BASE_HEADER_SIZE);
                } catch (Exception ignored) {
                }
            }
        }
    }

    private class ReliableInputStream extends InputStream {
        private final byte[] singleReadBuf = new byte[1];

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

            rxLock.lock();
            try {
                while (true) {
                    if (rcvRead < rcvNxt) {
                        RxSlot slot = rxSlots[rcvRead & WINDOW_MASK];
                        if (slot.received && slot.seq == rcvRead) {
                            int avail = slot.dataLen - slot.readOffset;
                            int toRead = Math.min(len, avail);
                            System.arraycopy(slot.payload, slot.readOffset, b, off, toRead);
                            slot.readOffset += toRead;

                            if (slot.readOffset >= slot.dataLen) {
                                slot.received = false;
                                slot.readOffset = 0;
                                rcvRead++;
                            }
                            return toRead;
                        }
                    }

                    if (finReceived && rcvRead >= rcvNxt) {
                        return -1;
                    }
                    if (closed.get() || dead.get()) {
                        return -1;
                    }

                    try {
                        rxDataCond.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IOException("Read interrupted", e);
                    }
                }
            } finally {
                rxLock.unlock();
            }
        }
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            closeReason = "Stream closed";
            timerThread.interrupt();
            rxLock.lock();
            try {
                rxDataCond.signalAll();
            } finally {
                rxLock.unlock();
            }
            txLock.lock();
            try {
                txWindowCond.signalAll();
            } finally {
                txLock.unlock();
            }
        }
    }
}
