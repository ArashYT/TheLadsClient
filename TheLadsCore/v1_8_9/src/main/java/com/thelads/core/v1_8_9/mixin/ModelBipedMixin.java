package com.thelads.core.v1_8_9.mixin;

import com.thelads.core.client.OldAnimations;
import com.thelads.core.modules.OldAnimationsModule.Feature;
import com.thelads.core.v1_8_9.feature.OldAnimations189;
import com.thelads.core.v1_8_9.feature.OldAnimations189.Hook;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 1.7 Animations, the 1.7 blocking arm (1.7 third-person items): 1.8 turns the blocking arm 30 degrees inward (heldItemRight 3),
 * 1.7 only pitched it. The swing's body turn is added after, so it stays. ModelPlayer copies the arm to its sleeve afterwards.
 */
@Mixin(ModelBiped.class)
public abstract class ModelBipedMixin {
    @Shadow public ModelRenderer bipedRightArm;
    @Shadow public int heldItemRight;

    @Inject(method = "setRotationAngles", at = @At("TAIL"), require = 1)
    private void ladsBlockingArm(float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch,
                                 float scaleFactor, Entity entity, CallbackInfo ci) {
        if (heldItemRight != 3 || !OldAnimations189.active(Feature.THIRD_PERSON)) return;
        bipedRightArm.rotateAngleY += OldAnimations.BLOCKING_ARM_YAW + 0.5235988f; // 1.8.9 set -0.5235988f before the body turn
        OldAnimations189.hit(Hook.TP_ARM);
    }
}
