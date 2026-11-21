package com.thelads.core.v1_20_1.lod.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.thelads.core.perf.PlayerLod;
import com.thelads.core.v1_20_1.lod.PlayerLod1201;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Player LOD: picks the cut-offs for the player being drawn, hides the flat outer skin parts and name tags (layers have their own mixins). */
@Mixin(PlayerRenderer.class)
abstract class PlayerLodRendererMixin {
    @Inject(method = "render(Lnet/minecraft/client/player/AbstractClientPlayer;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", at = @At("HEAD"))
    private void lads$lodBegin(AbstractClientPlayer player, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light, CallbackInfo ci) {
        PlayerLod1201.begin(player);
    }

    @Inject(method = "render(Lnet/minecraft/client/player/AbstractClientPlayer;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", at = @At("RETURN"))
    private void lads$lodEnd(AbstractClientPlayer player, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light, CallbackInfo ci) {
        PlayerLod1201.bits = 0;
    }

    @Inject(method = "setModelProperties", at = @At("TAIL"))
    private void lads$lodOuter(AbstractClientPlayer player, CallbackInfo ci) {
        if (!PlayerLod1201.has(PlayerLod.OUTER)) return;
        net.minecraft.client.model.PlayerModel<AbstractClientPlayer> model = ((PlayerRenderer) (Object) this).getModel();
        model.hat.visible = false;
        model.jacket.visible = false;
        model.leftSleeve.visible = false;
        model.rightSleeve.visible = false;
        model.leftPants.visible = false;
        model.rightPants.visible = false;
    }

    @Inject(method = "renderNameTag(Lnet/minecraft/client/player/AbstractClientPlayer;Lnet/minecraft/network/chat/Component;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", at = @At("HEAD"), cancellable = true)
    private void lads$lodName(AbstractClientPlayer player, Component name, PoseStack pose, MultiBufferSource buffers, int light, CallbackInfo ci) {
        if (PlayerLod1201.has(PlayerLod.NAME)) ci.cancel();
    }
}
