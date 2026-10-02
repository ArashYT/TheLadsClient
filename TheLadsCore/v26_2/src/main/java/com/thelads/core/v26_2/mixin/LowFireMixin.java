package com.thelads.core.v26_2.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.thelads.core.v26_2.feature.NativeOldAnimations;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ScreenEffectRenderer.class)
public class LowFireMixin {
    // 1.7 Animations' Low Fire, in its own push so the totem animation drawn after the fire stays put.
    @WrapOperation(method = "submit", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ScreenEffectRenderer;submitFire("
        + "Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;)V"),
        require = 1)
    private void ladsLowFire(PoseStack pose, SubmitNodeCollector collector, TextureAtlasSprite sprite, Operation<Void> original) {
        boolean low = NativeOldAnimations.lowFire();
        if (low) {
            pose.pushPose();
            pose.translate(0.0f, -0.3f, 0.0f);
        }
        original.call(pose, collector, sprite);
        if (low) pose.popPose();
    }
}
