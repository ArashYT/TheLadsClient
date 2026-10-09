package com.thelads.core.v1_8_9.mixin.ebe;

import com.thelads.core.client.ebe.BakedBlockEntities;
import com.thelads.core.v1_8_9.feature.ebe.BakedBlockEntitiesProbe189;
import net.minecraft.client.renderer.EntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Frame counter for the baked/renderer hand-over. */
@Mixin(EntityRenderer.class)
public abstract class EbeEntityRendererMixin {
    @Inject(method = "renderWorld", at = @At("HEAD"))
    private void lads$ebeFrame(float partialTicks, long finishTimeNano, CallbackInfo ci) {
        BakedBlockEntities.nextFrame();
        if (BakedBlockEntitiesProbe189.recording) BakedBlockEntitiesProbe189.frame();
    }
}
