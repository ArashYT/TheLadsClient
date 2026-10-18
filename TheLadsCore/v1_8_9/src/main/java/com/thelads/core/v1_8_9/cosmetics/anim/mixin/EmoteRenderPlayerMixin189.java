package com.thelads.core.v1_8_9.cosmetics.anim.mixin;

import com.thelads.core.client.cosmetics.anim.EmotePose;
import com.thelads.core.v1_8_9.cosmetics.anim.EmoteAnim189;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.RenderPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Emotes: the whole body's bounce moves the model space (armour, held items and the cape follow); the vanilla hand gets no emote. */
@Mixin(RenderPlayer.class)
public abstract class EmoteRenderPlayerMixin189 {
    @Inject(method = "preRenderCallback(Lnet/minecraft/client/entity/AbstractClientPlayer;F)V", at = @At("TAIL"), require = 1)
    private void ladsEmoteRoot(AbstractClientPlayer player, float partialTicks, CallbackInfo ci) {
        EmotePose emote = EmoteAnim189.pose(player);
        if (emote != null) GlStateManager.translate(emote.root(0) / 16F, emote.root(1) / 16F, emote.root(2) / 16F);
    }

    @Inject(method = {"renderRightArm", "renderLeftArm"}, at = @At("HEAD"), require = 2)
    private void ladsHandBegin(AbstractClientPlayer player, CallbackInfo ci) { EmoteAnim189.inHand = true; }

    @Inject(method = {"renderRightArm", "renderLeftArm"}, at = @At("RETURN"), require = 2)
    private void ladsHandEnd(AbstractClientPlayer player, CallbackInfo ci) { EmoteAnim189.inHand = false; }
}
