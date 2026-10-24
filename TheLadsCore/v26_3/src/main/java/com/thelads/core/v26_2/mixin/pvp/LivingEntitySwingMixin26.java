package com.thelads.core.v26_2.mixin.pvp;

import com.thelads.core.client.PvpVisuals;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.component.SwingAnimation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 1.7 Animations, Constant swing speed & haste slider: a swing takes its item's own duration or haste slider duration. Client world only. */
@Mixin(LivingEntity.class)
public class LivingEntitySwingMixin26 {
    @Inject(method = "getModifiedSwingDuration", at = @At("HEAD"), cancellable = true)
    private void lads$constantSwing(SwingAnimation animation, CallbackInfoReturnable<Integer> cir) {
        LivingEntity living = (LivingEntity) (Object) this;
        if (living.level().isClientSide()) {
            if (PvpVisuals.constantSwing) {
                cir.setReturnValue(animation.duration());
            } else if (PvpVisuals.swingHasteInfluence < 0.999f) {
                int haste = living.hasEffect(MobEffects.HASTE) ? 1 + living.getEffect(MobEffects.HASTE).getAmplifier() : 0;
                int fatigue = living.hasEffect(MobEffects.MINING_FATIGUE) ? 1 + living.getEffect(MobEffects.MINING_FATIGUE).getAmplifier() : 0;
                cir.setReturnValue(PvpVisuals.getSwingDuration(haste, fatigue, animation.duration()));
            }
        }
    }
}
