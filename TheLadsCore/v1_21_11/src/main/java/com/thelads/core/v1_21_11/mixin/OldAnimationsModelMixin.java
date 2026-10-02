package com.thelads.core.v1_21_11.mixin;

import com.thelads.core.v1_21_11.feature.NativeOldAnimations;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.world.entity.HumanoidArm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 1.7 third person: a player's blocking sword arm (OldAnimationsAvatarMixin's pose) blocks as 1.7's ModelBiped did. */
@Mixin(HumanoidModel.class)
public class OldAnimationsModelMixin {
    @Unique private HumanoidArm lads$swordArm;

    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V", at = @At("HEAD"), require = 1)
    private void lads$swordBlock(HumanoidRenderState state, CallbackInfo ci) {
        lads$swordArm = NativeOldAnimations.swordArm(state);
    }

    @Inject(method = "poseBlockingArm", at = @At("HEAD"), cancellable = true, require = 1)
    private void lads$blockingArm(ModelPart arm, boolean right, CallbackInfo ci) {
        if (lads$swordArm != (right ? HumanoidArm.RIGHT : HumanoidArm.LEFT)) return;
        NativeOldAnimations.blockingArm(arm, right);
        ci.cancel();
    }
}
