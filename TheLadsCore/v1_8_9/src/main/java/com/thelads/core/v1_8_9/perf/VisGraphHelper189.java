package com.thelads.core.v1_8_9.perf;

import java.util.BitSet;
import java.util.EnumSet;
import net.minecraft.util.EnumFacing;

/**
 * Helper for {@link com.thelads.core.v1_8_9.mixin.VisGraphMixin}.
 * Kept outside the {@code @Mixin} class so Mixin 0.7.11's class signature parser never inspects
 * {@code ThreadLocal<short[]>} ({@code [S}).
 */
public final class VisGraphHelper189 {
    private static final ThreadLocal<short[]> QUEUE = new ThreadLocal<short[]>() {
        @Override
        protected short[] initialValue() {
            return new short[4096];
        }
    };

    private VisGraphHelper189() {}

    public static int floodFill(BitSet visited, int startPos, EnumSet<EnumFacing> set) {
        short[] queue = QUEUE.get();
        int head = 0;
        int tail = 0;
        visited.set(startPos, true);
        queue[tail++] = (short) startPos;

        while (head < tail) {
            int pos = queue[head++] & 0xFFF;

            // X axis (bits 0..3, step +-1)
            if ((pos & 15) == 0) {
                set.add(EnumFacing.WEST);
            } else {
                int n = pos - 1;
                if (!visited.get(n)) {
                    visited.set(n, true);
                    queue[tail++] = (short) n;
                }
            }
            if ((pos & 15) == 15) {
                set.add(EnumFacing.EAST);
            } else {
                int n = pos + 1;
                if (!visited.get(n)) {
                    visited.set(n, true);
                    queue[tail++] = (short) n;
                }
            }

            // Y axis (bits 8..11, step +-256)
            if ((pos >> 8) == 0) {
                set.add(EnumFacing.DOWN);
            } else {
                int n = pos - 256;
                if (!visited.get(n)) {
                    visited.set(n, true);
                    queue[tail++] = (short) n;
                }
            }
            if ((pos >> 8) == 15) {
                set.add(EnumFacing.UP);
            } else {
                int n = pos + 256;
                if (!visited.get(n)) {
                    visited.set(n, true);
                    queue[tail++] = (short) n;
                }
            }

            // Z axis (bits 4..7, step +-16)
            if (((pos >> 4) & 15) == 0) {
                set.add(EnumFacing.NORTH);
            } else {
                int n = pos - 16;
                if (!visited.get(n)) {
                    visited.set(n, true);
                    queue[tail++] = (short) n;
                }
            }
            if (((pos >> 4) & 15) == 15) {
                set.add(EnumFacing.SOUTH);
            } else {
                int n = pos + 16;
                if (!visited.get(n)) {
                    visited.set(n, true);
                    queue[tail++] = (short) n;
                }
            }
        }

        return tail;
    }
}
