package com.thelads.core.v1_8_9.mixin;

import com.thelads.core.v1_8_9.perf.VisGraphHelper189;
import java.util.BitSet;
import java.util.EnumSet;
import java.util.Set;
import net.minecraft.client.renderer.chunk.VisGraph;
import net.minecraft.util.EnumFacing;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
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

    @Inject(method = "func_178604_a(I)Ljava/util/Set;", at = @At("HEAD"), cancellable = true, require = 0)
    private void ladsFloodFill(int startPos, CallbackInfoReturnable<Set<EnumFacing>> cir) {
        EnumSet<EnumFacing> set = EnumSet.noneOf(EnumFacing.class);
        this.field_178611_f -= VisGraphHelper189.floodFill(this.field_178612_d, startPos, set);
        cir.setReturnValue(set);
    }
}
