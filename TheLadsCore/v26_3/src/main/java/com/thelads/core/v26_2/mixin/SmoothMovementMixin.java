package com.thelads.core.v26_2.mixin;

import com.thelads.core.v26_2.SmoothMovementHandler;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Smooth Movement: 26.3's stepped interpolation lasts updateInterval ticks (2 for players), so it runs out before the next
 * move packet and the entity stands still for a tick (MC-311974). Client entities get a handler that can use the 26.2
 * linear one (3 ticks) instead. Based on Interpolation-Fix by Lodestone (MIT).
 */
@Mixin(LivingEntity.class)
public abstract class SmoothMovementMixin {
    @Inject(method = "createInterpolationHandler", at = @At("HEAD"), cancellable = true, require = 0)
    private void ladsSmoothMovement(CallbackInfoReturnable<InterpolationHandler> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (self.level().isClientSide()) cir.setReturnValue(new SmoothMovementHandler(self));
    }
}
