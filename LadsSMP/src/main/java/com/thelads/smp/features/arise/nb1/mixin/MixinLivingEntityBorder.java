package com.thelads.smp.features.arise.nb1.mixin;

import com.thelads.smp.features.arise.nb1.ForgivingBorderFeature;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin target: net.minecraft.world.entity.LivingEntity
 * Method: public void baseTick();
 */
@Mixin(LivingEntity.class)
public abstract class MixinLivingEntityBorder {

    @Inject(method = "baseTick", at = @At("HEAD"))
    private void ladssmp$forgivingBorderCheck(CallbackInfo ci) {
        if ((Object) this instanceof ServerPlayer player) {
            ForgivingBorderFeature.handlePlayerOutsideBorder(player);
        }
    }
}
