package com.thelads.core.perf;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.junit.jupiter.api.Test;

class RawMouseBatchTest {
    /** Writes one record at at, as GetRawInputBuffer lays them out on 64-bit Windows; returns the next record's offset. */
    private static int record(ByteBuffer out, int at, int type, int size, int flags, int x, int y) {
        out.putInt(at, type).putInt(at + 4, size).putLong(at + 8, 0x1234L).putLong(at + 16, 0);
        if (type == RawMouseBatch.RIM_TYPEMOUSE) out.putShort(at + 24, (short) flags).putInt(at + 36, x).putInt(at + 40, y);
        return (at + size + 7) & ~7;
    }

    @Test
    void sumsRelativeMotionAndSkipsTheRest() {
        ByteBuffer buffer = ByteBuffer.allocateDirect(1024).order(ByteOrder.LITTLE_ENDIAN);
        int at = record(buffer, 0, 0, 48, 0, 5, -3);
        at = record(buffer, at, 1, 36, 0, 0, 0); // keyboard (RAWKEYBOARD is 12 bytes on x64 + padding to 8)
        at = record(buffer, at, 0, 48, 0, -2, 7);
        at = record(buffer, at, 0, 48, RawMouseBatch.MOUSE_MOVE_ABSOLUTE, 30000, 30000);
        record(buffer, at, 0, 48, 0, 1000, 1);
        RawMouseBatch batch = new RawMouseBatch();
        batch.add(buffer, 5);
        assertEquals(1003, batch.dx);
        assertEquals(5, batch.dy);
        assertEquals(3, batch.mouse);
        assertEquals(1, batch.absolute);
        assertEquals(1, batch.other);
        batch.add(buffer, 1);
        assertEquals(1008, batch.dx);
        batch.clear();
        assertEquals(0, batch.dx + batch.dy + batch.mouse + batch.absolute + batch.other);
    }

    @Test
    void stopsAtAMalformedRecord() {
        ByteBuffer buffer = ByteBuffer.allocateDirect(96).order(ByteOrder.LITTLE_ENDIAN);
        int at = record(buffer, 0, 0, 48, 0, 4, 4);
        buffer.putInt(at, 0).putInt(at + 4, 4000); // claims more bytes than the buffer holds
        RawMouseBatch batch = new RawMouseBatch();
        batch.add(buffer, 3);
        assertEquals(4, batch.dx);
        assertEquals(2, batch.other);
    }
}
