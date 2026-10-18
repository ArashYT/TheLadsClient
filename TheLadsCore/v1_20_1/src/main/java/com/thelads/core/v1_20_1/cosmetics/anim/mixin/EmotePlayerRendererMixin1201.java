package com.thelads.core.v1_20_1.cosmetics.anim.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.thelads.core.client.cosmetics.anim.EmotePose;
import com.thelads.core.v1_20_1.cosmetics.anim.EmoteAnim1201;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Emotes: the whole body's bounce moves the model space (armour, held items and the cape follow); the vanilla hand gets no emote. */
@Mixin(PlayerRenderer.class)
public class EmotePlayerRendererMixin1201 {
    @Inject(method = "scale(Lnet/minecraft/client/player/AbstractClientPlayer;Lcom/mojang/blaze3d/vertex/PoseStack;F)V", at = @At("TAIL"))
    private void lads$emoteRoot(AbstractClientPlayer player, PoseStack pose, float partialTicks, CallbackInfo callback) {
        EmotePose emote = EmoteAnim1201.pose(player);
        if (emote != null) pose.translate(emote.root(0) / 16f, emote.root(1) / 16f, emote.root(2) / 16f);
    }

    @Inject(method = "renderHand", at = @At("HEAD"))
    private void lads$handBegin(CallbackInfo callback) { EmoteAnim1201.inHand = true; }

    @Inject(method = "renderHand", at = @At("RETURN"))
    private void lads$handEnd(CallbackInfo callback) { EmoteAnim1201.inHand = false; }
}
