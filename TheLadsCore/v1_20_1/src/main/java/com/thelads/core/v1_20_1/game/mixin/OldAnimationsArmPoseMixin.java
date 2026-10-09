package com.thelads.core.v1_20_1.game.mixin;

import com.thelads.core.v1_20_1.game.anim.NativeOldAnimations1201;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Priority above the default: the return hook runs after other mods' setupAnim hooks (NotEnoughAnimations' arm animations).
@Mixin(value = PlayerModel.class, priority = 1100)
public class OldAnimationsArmPoseMixin {
    // 1.7 Animations' third person: the sword-blocking arm only pitches, as in 1.7 (right after vanilla's pose)...
    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At(value = "INVOKE", shift = At.Shift.AFTER,
        target = "Lnet/minecraft/client/model/HumanoidModel;setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V"))
    private void lads$oldBlockingArm(LivingEntity entity, float a, float b, float c, float d, float e, CallbackInfo callback) {
        NativeOldAnimations1201.blockingArm(entity, (PlayerModel<?>) (Object) this);
    }

    // ...and stays so after other mods have posed the arms.
    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("RETURN"))
    private void lads$keepBlockingArms(LivingEntity entity, float a, float b, float c, float d, float e, CallbackInfo callback) {
        NativeOldAnimations1201.reapplyBlockingArms((PlayerModel<?>) (Object) this);
    }
}
