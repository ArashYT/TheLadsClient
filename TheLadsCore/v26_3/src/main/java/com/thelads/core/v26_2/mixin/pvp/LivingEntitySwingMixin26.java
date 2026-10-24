package com.thelads.core.v26_2.mixin.pvp;

import com.thelads.core.client.PvpVisuals;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.component.SwingAnimation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 1.7 Animations, Constant swing speed: a swing takes its item's own duration, Haste or Mining Fatigue. Client world only. */
@Mixin(LivingEntity.class)
public class LivingEntitySwingMixin26 {
    @Inject(method = "getModifiedSwingDuration", at = @At("HEAD"), cancellable = true)
    private void lads$constantSwing(SwingAnimation animation, CallbackInfoReturnable<Integer> cir) {
        if (PvpVisuals.constantSwing && ((Entity) (Object) this).level().isClientSide()) cir.setReturnValue(animation.duration());
    }
}
