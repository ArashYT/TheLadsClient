package com.thelads.core.v1_20_1.mixin.pvp;

import com.thelads.core.client.PvpVisuals;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 1.7 Animations, Constant swing speed & haste slider: arm swing duration based on haste influence. Client world only. */
@Mixin(LivingEntity.class)
public class LivingEntitySwingMixin1201 {
    @Inject(method = "getCurrentSwingDuration", at = @At("HEAD"), cancellable = true)
    private void lads$constantSwing(CallbackInfoReturnable<Integer> cir) {
        LivingEntity living = (LivingEntity) (Object) this;
        if (living.level().isClientSide) {
            if (PvpVisuals.constantSwing) {
                cir.setReturnValue(6);
            } else if (PvpVisuals.swingHasteInfluence < 0.999f) {
                int haste = living.hasEffect(MobEffects.DIG_SPEED) ? 1 + living.getEffect(MobEffects.DIG_SPEED).getAmplifier() : 0;
                int fatigue = living.hasEffect(MobEffects.DIG_SLOWDOWN) ? 1 + living.getEffect(MobEffects.DIG_SLOWDOWN).getAmplifier() : 0;
                cir.setReturnValue(PvpVisuals.getSwingDuration(haste, fatigue, 6));
            }
        }
    }
}
