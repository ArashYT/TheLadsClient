package com.thelads.core.v1_20_1.cosmetics.anim.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.thelads.core.client.cosmetics.anim.EmotePose;
import com.thelads.core.v1_20_1.cosmetics.anim.EmoteAnim1201;
import com.thelads.core.v1_20_1.cosmetics.wearables.CosmeticsWearablesLayer1201;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Emotes: the whole body's bounce moves the model space (armour, held items and the cape follow); the vanilla hand gets no emote; wearables layer attached. */
@Mixin(PlayerRenderer.class)
public abstract class EmotePlayerRendererMixin1201 extends LivingEntityRenderer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private EmotePlayerRendererMixin1201() { super(null, null, 0.0f); }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void lads$addWearablesLayer(EntityRendererProvider.Context context, boolean useSlimModel, CallbackInfo callback) {
        this.addLayer(new CosmeticsWearablesLayer1201(this));
    }

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
