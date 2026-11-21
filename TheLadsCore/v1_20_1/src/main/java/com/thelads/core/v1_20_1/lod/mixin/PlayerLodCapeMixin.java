package com.thelads.core.v1_20_1.lod.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.thelads.core.perf.PlayerLod;
import com.thelads.core.v1_20_1.lod.PlayerLod1201;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.CapeLayer;
import net.minecraft.client.player.AbstractClientPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Player LOD: capes beyond the cape distance. */
@Mixin(CapeLayer.class)
abstract class PlayerLodCapeMixin {
    @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/player/AbstractClientPlayer;FFFFFF)V", at = @At("HEAD"), cancellable = true)
    private void lads$lod(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer entity, float a, float b, float c, float d, float e, float f, CallbackInfo ci) {
        if (PlayerLod1201.has(PlayerLod.CAPE)) ci.cancel();
    }
}
