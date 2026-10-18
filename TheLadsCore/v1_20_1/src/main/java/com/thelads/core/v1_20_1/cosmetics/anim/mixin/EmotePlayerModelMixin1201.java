package com.thelads.core.v1_20_1.cosmetics.anim.mixin;

import com.thelads.core.client.cosmetics.anim.EmotePose;
import com.thelads.core.v1_20_1.cosmetics.anim.EmoteAnim1201;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Emotes in third person (every player, the inventory preview; armour copies the parts): the emote pose goes on last, after vanilla,
 * NotEnoughAnimations and 1.7 Animations (priority 1100). The model is shared by every player, so fields vanilla never resets are
 * put back before the next player once an emote touched them.
 */
@Mixin(value = PlayerModel.class, priority = 1200)
public class EmotePlayerModelMixin1201 {
    @Unique private boolean ladsEmoteDirty;

    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("HEAD"))
    private void lads$emoteRestore(LivingEntity entity, float a, float b, float c, float d, float e, CallbackInfo callback) {
        if (!ladsEmoteDirty) return;
        ladsEmoteDirty = false;
        EmoteAnim1201.restore((PlayerModel<?>) (Object) this);
    }

    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("RETURN"))
    private void lads$emote(LivingEntity entity, float a, float b, float c, float d, float e, CallbackInfo callback) {
        if (EmoteAnim1201.inHand) return;
        EmotePose pose = EmoteAnim1201.pose(entity);
        if (pose == null) return;
        EmoteAnim1201.apply((PlayerModel<?>) (Object) this, pose);
        ladsEmoteDirty = true;
    }
}
