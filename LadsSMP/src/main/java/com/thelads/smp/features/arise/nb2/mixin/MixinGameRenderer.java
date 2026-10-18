package com.thelads.smp.features.arise.nb2.mixin;

import com.thelads.smp.features.arise.nb2.TooManyEntitiesManager;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to GameRenderer to reset entity render counts at the beginning of each frame (Too Many Entities).
 */
@Environment(EnvType.CLIENT)
@Mixin(GameRenderer.class)
public abstract class MixinGameRenderer {

    @Inject(
            method = "renderLevel()V",
            at = @At("HEAD")
    )
    private void onRenderLevel(CallbackInfo ci) {
        TooManyEntitiesManager.reset();
    }
}
