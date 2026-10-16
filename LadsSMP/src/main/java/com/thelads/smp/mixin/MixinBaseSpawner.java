package com.thelads.smp.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.thelads.smp.features.ConsolidatedFeatures;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BaseSpawner;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Consolidates 'betterspawnercontrol': Suppresses monster spawner server ticks when surrounded on 5 sides by torches/lanterns.
 * The torch check rides on vanilla's player-range check, so a spawner with nobody near returns before it, as in vanilla.
 */
@Mixin(BaseSpawner.class)
public abstract class MixinBaseSpawner {

    @WrapOperation(method = "serverTick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/BaseSpawner;isNearPlayer(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)Z"))
    private boolean ladssmp$suppressTorchSurroundedSpawner(BaseSpawner spawner, Level level, BlockPos pos, Operation<Boolean> original) {
        return original.call(spawner, level, pos) && !ConsolidatedFeatures.isSpawnerSuppressedByTorches(level, pos);
    }
}
