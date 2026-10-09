package com.thelads.core.perf;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Sums a batch of Windows raw input records (GetRawInputBuffer's output on 64-bit Windows) into one mouse motion, for the built-in
 * buffered raw mouse input (the Lads remake of Ixeris' safe part): one read per poll instead of one GetRawInputData call and one
 * JNI upcall per mouse event, which a 1000-8000 Hz mouse sends thousands of times a second.
 *
 * <p>Layout (x64): RAWINPUTHEADER {DWORD dwType; DWORD dwSize; HANDLE hDevice; WPARAM wParam} = 24 bytes, then RAWMOUSE {USHORT
 * usFlags; USHORT pad; USHORT usButtonFlags; USHORT usButtonData; ULONG ulRawButtons; LONG lLastX; LONG lLastY; ULONG extra}.
 * Records are dwSize long, each starting on an 8-byte boundary (NEXTRAWINPUTBLOCK).
 */
public final class RawMouseBatch {
    public static final int HEADER_BYTES = 24;
    static final int RIM_TYPEMOUSE = 0, MOUSE_MOVE_ABSOLUTE = 1;
    /** Relative motion summed since {@link #clear}. */
    public long dx, dy;
    /** Records seen: mouse motion, absolute-position mouse records (tablets, remote desktop; not summed), anything else. */
    public int mouse, absolute, other;

    public void clear() {
        dx = dy = 0;
        mouse = absolute = other = 0;
    }

    /** Adds count records laid out from index 0 of buffer (little-endian native memory). */
    public void add(ByteBuffer buffer, int count) {
        ByteBuffer in = buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN);
        int at = 0;
        for (int i = 0; i < count; i++) {
            int type = in.getInt(at), size = in.getInt(at + 4);
            if (size < HEADER_BYTES || at + size > in.limit()) { other += count - i; return; } // malformed: stop rather than misread
            if (type != RIM_TYPEMOUSE || size < HEADER_BYTES + 20) other++;
            else if ((in.getShort(at + HEADER_BYTES) & MOUSE_MOVE_ABSOLUTE) != 0) absolute++;
            else {
                mouse++;
                dx += in.getInt(at + HEADER_BYTES + 12);
                dy += in.getInt(at + HEADER_BYTES + 16);
            }
            at = (at + size + 7) & ~7;
        }
    }
}
