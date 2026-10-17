package com.thelads.smp.mixin;

import com.thelads.smp.config.LadsConfig;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.level.ServerLevelAccessor;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Consolidates 'naturallychargedcreepers': Naturally spawned creepers have a configurable
 * chance (default 0.1 / 10%) to spawn charged.
 */
@Mixin(Creeper.class)
public abstract class MixinCreeper {

    @Shadow
    @Final
    private static EntityDataAccessor<Boolean> DATA_IS_POWERED;

    @Inject(method = "finalizeSpawn", at = @At("RETURN"))
    private void ladssmp$naturallyChargeCreeper(
            ServerLevelAccessor level,
            DifficultyInstance difficulty,
            EntitySpawnReason spawnReason,
            SpawnGroupData spawnGroupData,
            CallbackInfoReturnable<SpawnGroupData> cir) {
        if (!LadsConfig.get().features.naturallyChargedCreepers) return;
        if (spawnReason == EntitySpawnReason.NATURAL
                || spawnReason == EntitySpawnReason.CHUNK_GENERATION
                || spawnReason == EntitySpawnReason.STRUCTURE
                || spawnReason == EntitySpawnReason.EVENT
                || spawnReason == EntitySpawnReason.PATROL) {
            if (ThreadLocalRandom.current().nextDouble() < LadsConfig.get().features.naturallyChargedCreepersChance) {
                ((Creeper) (Object) this).getEntityData().set(DATA_IS_POWERED, true);
            }
        }
    }
}
