package com.thelads.core.v26_2.mixin;

import com.thelads.core.v26_2.feature.NativeOldAnimations;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Priority above the default: the return hook runs after other mods' setupAnim hooks (NotEnoughAnimations' arm animations).
@Mixin(value = PlayerModel.class, priority = 1100)
public class OldAnimationsArmPoseMixin {
    // 1.7 Animations' third person: the sword-blocking arm only pitches, as in 1.7 (right after vanilla's pose)...
    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V", at = @At(value = "INVOKE", shift = At.Shift.AFTER,
        target = "Lnet/minecraft/client/model/HumanoidModel;setupAnim(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V"), require = 1)
    private void lads$oldBlockingArm(AvatarRenderState state, CallbackInfo callback) {
        NativeOldAnimations.blockingArm(state, (PlayerModel) (Object) this);
    }

    // ...and stays so after other mods have posed the arms.
    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V", at = @At("RETURN"), require = 1)
    private void lads$keepBlockingArms(AvatarRenderState state, CallbackInfo callback) {
        NativeOldAnimations.reapplyBlockingArms((PlayerModel) (Object) this);
    }
}
