package com.thelads.core.v26_2.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ScreenEffectRenderer.class)
public class LowFireMixin {
    @Inject(method = "submitFire", at = @At("HEAD"))
    private static void ladsLowFire(PoseStack pose, SubmitNodeCollector collector, TextureAtlasSprite sprite, CallbackInfo ci) {
        if (com.thelads.core.config.ModuleManager.getInstance().getModule("OldAnimations") instanceof com.thelads.core.modules.OldAnimationsModule oam
                && oam.active(com.thelads.core.modules.OldAnimationsModule.Feature.LOW_FIRE, com.thelads.core.modules.OldAnimationsModule.Platform.MODERN)) {
            pose.translate(0.0f, -0.3f, 0.0f);
        }
    }
}
