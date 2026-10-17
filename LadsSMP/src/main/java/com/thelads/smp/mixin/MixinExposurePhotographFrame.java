package com.thelads.smp.mixin;

import net.minecraft.network.syncher.SynchedEntityData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Exposure 1.9.28-beta.1 (26.3 port): PhotographFrameEntity overrides defineSynchedData without calling super, so 26.3's
 * HangingEntity.DATA_DIRECTION is never defined and every (glass) photograph frame fails to construct
 * ("has not defined synched data value"). Define it the way HangingEntity.defineSynchedData does.
 */
@Pseudo
@Mixin(targets = "io.github.mortuusars.exposure.world.entity.PhotographFrameEntity", remap = false)
public class MixinExposurePhotographFrame {
    @Inject(method = "defineSynchedData", at = @At("HEAD"))
    private void ladssmp$defineHangingDirection(SynchedEntityData.Builder builder, CallbackInfo ci) {
        builder.define(HangingEntityAccessor.ladssmp$dataDirection(), HangingEntityAccessor.ladssmp$defaultDirection());
    }
}
