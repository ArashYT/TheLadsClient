// Adapted from Capes 1.5.4+1.21 by Cael (LGPL-2.1-only); modified by The Lads: remapped to Mojang mappings and repackaged into Lads Core.
package com.thelads.core.v1_21_1.embedded.capes.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.layers.CapeLayer;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(CapeLayer.class)
public class MixinCapeFeatureRenderer {

    // Upstream "render*", pinned to the method its refmap resolved to.
    @Redirect(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/player/AbstractClientPlayer;FFFFFF)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/RenderType;entitySolid(Lnet/minecraft/resources/ResourceLocation;)Lnet/minecraft/client/renderer/RenderType;"))
    private RenderType fixCapeTransparency(ResourceLocation texture) {
        return RenderType.armorCutoutNoCull(texture);
    }

    // Fixes https://bugs.mojang.com/browse/MC-127749
    @ModifyVariable(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/player/AbstractClientPlayer;FFFFFF)V", at = @At("STORE"), ordinal = 6)
    private float fixCapeInterpolation(float bodyRotation, @Local(argsOnly = true) AbstractClientPlayer playerEntity, @Local(ordinal = 2, argsOnly = true) float partialTicks) {
        return playerEntity.yBodyRotO + (playerEntity.yBodyRot - playerEntity.yBodyRotO) * partialTicks;
    }
}
