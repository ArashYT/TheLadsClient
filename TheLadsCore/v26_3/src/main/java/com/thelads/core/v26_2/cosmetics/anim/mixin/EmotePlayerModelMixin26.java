package com.thelads.core.v26_2.cosmetics.anim.mixin;

import com.thelads.core.v26_2.cosmetics.anim.EmoteAnim26;
import com.thelads.core.v26_2.cosmetics.anim.EmoteState26;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Emotes in third person (every player, the inventory preview and armour models too): the emote pose goes on last, after vanilla,
 * NotEnoughAnimations and 1.7 Animations (priority 1100) posed the model. Sleeves, pants, jacket and hat are children of their parts.
 */
@Mixin(value = PlayerModel.class, priority = 1200)
public class EmotePlayerModelMixin26 {
    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V", at = @At("RETURN"))
    private void lads$emote(AvatarRenderState state, CallbackInfo callback) {
        EmoteAnim26.apply((PlayerModel) (Object) this, ((EmoteState26) state).lads$emote());
    }
}
