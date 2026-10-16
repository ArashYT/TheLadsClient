package com.thelads.core.v26_2.embedded.entityculling.mixin;

import com.thelads.core.v26_2.embedded.entityculling.EntityCulling;
import net.minecraft.client.renderer.extract.LevelExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The frame's clock, read once before its entities and block entities are extracted (not once per entity). */
@Mixin(LevelExtractor.class)
public abstract class LevelExtractorMixin {
    @Inject(method = "extract(Lnet/minecraft/client/DeltaTracker;Lnet/minecraft/client/Camera;F)V", at = @At("HEAD"))
    private void lads$frameClock(CallbackInfo ci) {
        EntityCulling.frame();
    }
}
