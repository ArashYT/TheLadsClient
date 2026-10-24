package com.thelads.core.v1_20_1.mixin.pvp;

import com.thelads.core.client.PvpVisuals;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 1.7 Animations, Constant swing speed: every arm swing takes vanilla's 6 ticks, Haste or Mining Fatigue. Client world only. */
@Mixin(LivingEntity.class)
public class LivingEntitySwingMixin1201 {
    @Inject(method = "getCurrentSwingDuration", at = @At("HEAD"), cancellable = true)
    private void lads$constantSwing(CallbackInfoReturnable<Integer> cir) {
        if (PvpVisuals.constantSwing && ((Entity) (Object) this).level().isClientSide) cir.setReturnValue(6);
    }
}
