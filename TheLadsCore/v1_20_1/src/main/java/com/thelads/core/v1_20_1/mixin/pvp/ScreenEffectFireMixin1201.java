package com.thelads.core.v1_20_1.mixin.pvp;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.mojang.blaze3d.vertex.PoseStack;
import com.thelads.core.client.PvpVisuals;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.world.effect.MobEffects;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Overlays: the fire overlay at Fire Opacity, none at 0% or with Fire Resistance while Hide Fire with Fire Resistance is on. The call
 * is skipped whole, so 1.7 Animations' Low Fire (LowFireMixin, at renderFire's head) never pushes. Forge keeps the call after its event.
 */
@Mixin(ScreenEffectRenderer.class)
public class ScreenEffectFireMixin1201 {
    @WrapWithCondition(method = "renderScreenEffect", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/ScreenEffectRenderer;renderFire(Lnet/minecraft/client/Minecraft;Lcom/mojang/blaze3d/vertex/PoseStack;)V"))
    private static boolean lads$fire(Minecraft minecraft, PoseStack pose) {
        return PvpVisuals.fireOpacity > 0 && !(PvpVisuals.hideFireWithResistance && minecraft.player.hasEffect(MobEffects.FIRE_RESISTANCE));
    }

    @ModifyArg(method = "renderFire", at = @At(value = "INVOKE",
        target = "Lcom/mojang/blaze3d/vertex/VertexConsumer;color(FFFF)Lcom/mojang/blaze3d/vertex/VertexConsumer;"), index = 3)
    private static float lads$fireOpacity(float alpha) {
        return alpha * PvpVisuals.fireOpacity;
    }
}
