package com.thelads.core.v26_2.mixin.pvp;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.thelads.core.client.PvpVisuals;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.effect.MobEffects;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Overlays' fire options and Totem Animation. The fire call is skipped whole (LowFireMixin's push and pop wrap the same call, so they
 * stay paired); its two quads share one color (alpha 0.9). The totem's 0.8 size is the one scale in the animation.
 */
@Mixin(ScreenEffectRenderer.class)
public class ScreenEffectPvpMixin26 {
    @WrapWithCondition(method = "submit", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ScreenEffectRenderer;submitFire("
        + "Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;)V"))
    private boolean lads$fire(PoseStack pose, SubmitNodeCollector collector, TextureAtlasSprite sprite) {
        var player = Minecraft.getInstance().player;
        return PvpVisuals.fireOpacity > 0 && !(PvpVisuals.hideFireWithResistance && player != null && player.hasEffect(MobEffects.FIRE_RESISTANCE));
    }

    @ModifyArg(method = "buildFireQuad", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ScreenEffectRenderer;buildSpriteQuad("
        + "Lcom/mojang/blaze3d/vertex/VertexConsumer;Lorg/joml/Matrix4f;Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;FFFFFI)V"), index = 8)
    private static int lads$fireOpacity(int color) {
        return PvpVisuals.alpha(color, PvpVisuals.fireOpacity);
    }

    @Inject(method = "renderItemActivationAnimation", at = @At("HEAD"), cancellable = true)
    private void lads$hideTotem(PlayerRenderState player, PoseStack pose, float partialTicks, SubmitNodeCollector collector, CallbackInfo ci) {
        if (PvpVisuals.hideTotem) ci.cancel();
    }

    @WrapOperation(method = "renderItemActivationAnimation", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;scale(FFF)V"))
    private void lads$totemScale(PoseStack pose, float x, float y, float z, Operation<Void> original) {
        float s = PvpVisuals.totemScale;
        original.call(pose, x * s, y * s, z * s);
    }
}
