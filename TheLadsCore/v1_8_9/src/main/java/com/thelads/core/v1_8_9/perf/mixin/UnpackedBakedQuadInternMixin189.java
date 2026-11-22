package com.thelads.core.v1_8_9.perf.mixin;

import com.thelads.core.v1_8_9.perf.LoadPerf189;
import net.minecraftforge.client.model.pipeline.UnpackedBakedQuad;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Forge's item quads keep every vertex element as its own small float array (position, color, uv, normal, padding), most of them
 * the same numbers over and over: share the equal ones while the models bake (LoadPerf189.intern). They are only read afterwards.
 */
@Mixin(value = UnpackedBakedQuad.class, remap = false)
public abstract class UnpackedBakedQuadInternMixin189 {
    @Shadow @Final protected float[][][] unpackedData;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void lads$intern(CallbackInfo ci) {
        LoadPerf189.intern(unpackedData);
    }
}
