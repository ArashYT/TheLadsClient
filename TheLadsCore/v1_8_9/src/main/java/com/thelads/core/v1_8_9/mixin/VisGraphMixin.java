package com.thelads.core.v1_8_9.mixin;

import java.util.BitSet;
import java.util.EnumSet;
import java.util.Set;
import net.minecraft.client.renderer.chunk.VisGraph;
import net.minecraft.util.EnumFacing;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Replaces VisGraph's LinkedList<Integer> + Integer.valueOf flood-fill with a ThreadLocal<short[]> queue and
 * inline bit-checks, eliminating thousands of boxed Integer and LinkedList.Node allocations per chunk rebuild.
 */
@Mixin(VisGraph.class)
public abstract class VisGraphMixin {
    @Shadow @Final private BitSet field_178612_d;
    @Shadow private int field_178611_f;

    @Unique
    private static final ThreadLocal<short[]> LADS_QUEUE = new ThreadLocal<short[]>() {
        @Override
        protected short[] initialValue() {
            return new short[4096];
        }
    };

    @Inject(method = "func_178604_a(I)Ljava/util/Set;", at = @At("HEAD"), cancellable = true, require = 0)
    private void ladsFloodFill(int startPos, CallbackInfoReturnable<Set<EnumFacing>> cir) {
        EnumSet<EnumFacing> set = EnumSet.noneOf(EnumFacing.class);
        short[] queue = LADS_QUEUE.get();
        int head = 0;
        int tail = 0;
        this.field_178612_d.set(startPos, true);
        queue[tail++] = (short) startPos;

        while (head < tail) {
            int pos = queue[head++] & 0xFFF;
            --this.field_178611_f;

            // X axis (bits 0..3, step +-1)
            if ((pos & 15) == 0) {
                set.add(EnumFacing.WEST);
            } else {
                int n = pos - 1;
                if (!this.field_178612_d.get(n)) {
                    this.field_178612_d.set(n, true);
                    queue[tail++] = (short) n;
                }
            }
            if ((pos & 15) == 15) {
                set.add(EnumFacing.EAST);
            } else {
                int n = pos + 1;
                if (!this.field_178612_d.get(n)) {
                    this.field_178612_d.set(n, true);
                    queue[tail++] = (short) n;
                }
            }

            // Y axis (bits 8..11, step +-256)
            if ((pos >> 8) == 0) {
                set.add(EnumFacing.DOWN);
            } else {
                int n = pos - 256;
                if (!this.field_178612_d.get(n)) {
                    this.field_178612_d.set(n, true);
                    queue[tail++] = (short) n;
                }
            }
            if ((pos >> 8) == 15) {
                set.add(EnumFacing.UP);
            } else {
                int n = pos + 256;
                if (!this.field_178612_d.get(n)) {
                    this.field_178612_d.set(n, true);
                    queue[tail++] = (short) n;
                }
            }

            // Z axis (bits 4..7, step +-16)
            if (((pos >> 4) & 15) == 0) {
                set.add(EnumFacing.NORTH);
            } else {
                int n = pos - 16;
                if (!this.field_178612_d.get(n)) {
                    this.field_178612_d.set(n, true);
                    queue[tail++] = (short) n;
                }
            }
            if (((pos >> 4) & 15) == 15) {
                set.add(EnumFacing.SOUTH);
            } else {
                int n = pos + 16;
                if (!this.field_178612_d.get(n)) {
                    this.field_178612_d.set(n, true);
                    queue[tail++] = (short) n;
                }
            }
        }

        cir.setReturnValue(set);
    }
}
