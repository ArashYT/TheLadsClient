package com.thelads.core.v1_20_1.world.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.thelads.core.v1_20_1.world.World1201;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.layers.CapeLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Wavy Capes: the cape mesh instead of the rigid cloak, once vanilla has decided the cape shows (World1201.cape). */
@Mixin(CapeLayer.class)
public class CapeLayerWavyMixin {
    // the descriptor keeps the synthetic bridge render(..., Entity, ...) out of the match
    @WrapOperation(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/player/AbstractClientPlayer;FFFFFF)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/PlayerModel;renderCloak(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;II)V"))
    private void lads$wavyCape(PlayerModel<?> model, PoseStack pose, VertexConsumer buffer, int light, int overlay, Operation<Void> original,
                               @Local(argsOnly = true) AbstractClientPlayer player, @Local(argsOnly = true, ordinal = 2) float partial) {
        if (!World1201.cape(player, partial, pose, buffer, light, overlay)) original.call(model, pose, buffer, light, overlay);
    }
}
