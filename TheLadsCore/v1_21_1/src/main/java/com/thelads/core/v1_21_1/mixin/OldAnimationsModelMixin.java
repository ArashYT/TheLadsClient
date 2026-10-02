package com.thelads.core.v1_21_1.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.thelads.core.v1_21_1.feature.NativeOldAnimations;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 1.7 third person: during the sword block the sword arm is the posed arm, and it blocks as 1.7's ModelBiped did. */
@Mixin(HumanoidModel.class)
public class OldAnimationsModelMixin {
    @Unique private static final String SETUP = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V";
    @Unique private HumanoidArm lads$swordArm;

    @Inject(method = SETUP, at = @At("HEAD"), require = 1)
    private void lads$swordBlock(LivingEntity entity, float limbSwing, float limbSwingAmount, float age, float headYaw, float headPitch, CallbackInfo ci) {
        lads$swordArm = NativeOldAnimations.swordArm(entity);
    }

    /** 1.21.1 poses only the arm of the hand in use: the shield's hand is in use, but the sword's arm is the one that blocks. */
    @ModifyExpressionValue(method = SETUP, at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getUsedItemHand()Lnet/minecraft/world/InteractionHand;"),
        require = 1)
    private InteractionHand lads$poseSwordArm(InteractionHand hand) {
        return lads$swordArm != null ? InteractionHand.MAIN_HAND : hand;
    }

    @Inject(method = "poseBlockingArm", at = @At("HEAD"), cancellable = true, require = 1)
    private void lads$blockingArm(ModelPart arm, boolean right, CallbackInfo ci) {
        if (lads$swordArm != (right ? HumanoidArm.RIGHT : HumanoidArm.LEFT)) return;
        NativeOldAnimations.blockingArm(arm, right);
        ci.cancel();
    }
}
