package com.thelads.smp.mixin;

import com.thelads.smp.config.LadsConfig;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Consolidates 'naturallychargedcreepers': naturally spawned creepers have a chance to be charged.
 * Creeper does not declare finalizeSpawn in 26.3 (it inherits Mob's), so this mixin adds the override.
 */
@Mixin(Creeper.class)
public abstract class MixinCreeper extends Monster {
    @Shadow @Final private static EntityDataAccessor<Boolean> DATA_IS_POWERED;

    protected MixinCreeper(EntityType<? extends Monster> type, Level level) {
        super(type, level);
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason spawnReason,
                                        SpawnGroupData spawnGroupData) {
        SpawnGroupData result = super.finalizeSpawn(level, difficulty, spawnReason, spawnGroupData);
        if (LadsConfig.get().features.naturallyChargedCreepers
                && (spawnReason == EntitySpawnReason.NATURAL || spawnReason == EntitySpawnReason.CHUNK_GENERATION
                    || spawnReason == EntitySpawnReason.STRUCTURE || spawnReason == EntitySpawnReason.EVENT
                    || spawnReason == EntitySpawnReason.PATROL)
                && ThreadLocalRandom.current().nextDouble() < LadsConfig.get().features.naturallyChargedCreepersChance) {
            this.getEntityData().set(DATA_IS_POWERED, true);
        }
        return result;
    }
}
