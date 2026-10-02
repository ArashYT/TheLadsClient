package com.thelads.core.v26_2.mixin;

import com.thelads.core.v26_2.feature.NativeOldAnimations;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HumanoidModel.class)
public class OldAnimationsArmPoseMixin {
    // 1.7 Animations' third person: the sword-blocking arm only pitches, as in 1.7.
    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V", at = @At("TAIL"), require = 1)
    private void lads$oldBlockingArm(HumanoidRenderState state, CallbackInfo callback) {
        NativeOldAnimations.blockingArm(state, (HumanoidModel<?>) (Object) this);
    }
}
