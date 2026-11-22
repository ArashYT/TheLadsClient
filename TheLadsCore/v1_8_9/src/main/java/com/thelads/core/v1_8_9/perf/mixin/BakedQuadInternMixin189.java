package com.thelads.core.v1_8_9.perf.mixin;

import com.thelads.core.v1_8_9.perf.LoadPerf189;
import net.minecraft.client.renderer.block.model.BakedQuad;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Plain block quads baked with the same vertex numbers share one vertex array (LoadPerf189.intern, only while the models bake).
 * Subclasses are left alone: Forge's UnpackedBakedQuad packs into its array later (UnpackedBakedQuadInternMixin189 handles it).
 */
@Mixin(BakedQuad.class)
public abstract class BakedQuadInternMixin189 {
    @Shadow @Final @Mutable protected int[] vertexData;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void lads$intern(CallbackInfo ci) {
        if (((Object) this).getClass() == BakedQuad.class) vertexData = LoadPerf189.intern(vertexData);
    }
}
