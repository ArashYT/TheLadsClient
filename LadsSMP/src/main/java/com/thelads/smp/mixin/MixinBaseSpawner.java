package com.thelads.smp.mixin;

import com.thelads.smp.features.ConsolidatedFeatures;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BaseSpawner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Consolidates 'betterspawnercontrol': Suppresses monster spawner server ticks when surrounded on 5 sides by torches/lanterns.
 */
@Mixin(BaseSpawner.class)
public abstract class MixinBaseSpawner {

    @Inject(method = "serverTick", at = @At("HEAD"), cancellable = true)
    private void ladssmp$suppressTorchSurroundedSpawner(ServerLevel level, BlockPos pos, CallbackInfo ci) {
        if (ConsolidatedFeatures.isSpawnerSuppressedByTorches(level, pos)) {
            ci.cancel();
        }
    }
}
