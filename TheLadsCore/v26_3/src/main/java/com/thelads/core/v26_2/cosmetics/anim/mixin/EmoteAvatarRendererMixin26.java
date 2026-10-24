package com.thelads.core.v26_2.cosmetics.anim.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.thelads.core.client.cosmetics.anim.EmotePose;
import com.thelads.core.v26_2.cosmetics.anim.EmoteAnim26;
import com.thelads.core.v26_2.cosmetics.anim.EmoteState26;
import com.thelads.core.v26_2.cosmetics.wearables.CosmeticsWearablesLayer26;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Emotes: each player's pose is sampled once per extraction into its render state; wearables layer registered. */
@Mixin(AvatarRenderer.class)
public abstract class EmoteAvatarRendererMixin26 {
    @Inject(method = "<init>", at = @At("RETURN"))
    private void lads$addWearablesLayer(EntityRendererProvider.Context context, boolean useSlimModel, CallbackInfo callback) {
        ((LivingEntityRendererInvoker26) (Object) this).lads$addLayer(new CosmeticsWearablesLayer26((AvatarRenderer) (Object) this));
    }

    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
        at = @At("TAIL"))
    private void lads$emotePose(Avatar entity, AvatarRenderState state, float partialTicks, CallbackInfo callback) {
        EmoteState26 es = (EmoteState26) state;
        es.lads$setEmote(EmoteAnim26.pose(entity));
        es.lads$setUuid(entity.getUUID());
    }

    @Inject(method = "scale(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;)V", at = @At("TAIL"))
    private void lads$emoteRoot(AvatarRenderState state, PoseStack pose, CallbackInfo callback) {
        EmotePose emote = ((EmoteState26) state).lads$emote();
        if (emote != null) pose.translate(emote.root(0) / 16f, emote.root(1) / 16f, emote.root(2) / 16f);
    }
}
