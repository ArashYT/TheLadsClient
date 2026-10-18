package com.thelads.core.v1_8_9.cosmetics.anim.mixin;

import com.thelads.core.client.cosmetics.anim.EmotePose;
import com.thelads.core.v1_8_9.cosmetics.anim.EmoteAnim189;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Emotes in third person (every player, the inventory preview, armour models): the emote pose goes on after vanilla and 1.7
 * Animations' blocking arm (ModelBipedMixin, default priority); ModelPlayer copies it to the outer layer when this returns. The model
 * is shared, so fields vanilla never resets are saved before an emote touches them and put back before the next entity.
 */
@Mixin(value = ModelBiped.class, priority = 1100)
public abstract class EmoteModelBipedMixin189 {
    @Unique private float[] ladsEmoteRest;
    @Unique private boolean ladsEmoteDirty;

    @Inject(method = "setRotationAngles", at = @At("HEAD"), require = 1)
    private void ladsEmoteRestore(float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch,
                                  float scaleFactor, Entity entity, CallbackInfo ci) {
        if (!ladsEmoteDirty) return;
        ladsEmoteDirty = false;
        EmoteAnim189.restore((ModelBiped) (Object) this, ladsEmoteRest);
    }

    @Inject(method = "setRotationAngles", at = @At("TAIL"), require = 1)
    private void ladsEmote(float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch,
                           float scaleFactor, Entity entity, CallbackInfo ci) {
        if (EmoteAnim189.inHand || !(entity instanceof EntityPlayer)) return;
        EmotePose pose = EmoteAnim189.pose(entity);
        if (pose == null) return;
        if (ladsEmoteRest == null) ladsEmoteRest = new float[13];
        EmoteAnim189.save((ModelBiped) (Object) this, ladsEmoteRest);
        EmoteAnim189.apply((ModelBiped) (Object) this, pose);
        ladsEmoteDirty = true;
    }
}
