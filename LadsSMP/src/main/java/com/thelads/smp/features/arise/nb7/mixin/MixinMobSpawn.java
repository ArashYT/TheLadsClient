package com.thelads.smp.features.arise.nb7.mixin;

import com.thelads.smp.features.arise.nb7.AriseNB7Config;
import com.thelads.smp.features.arise.nb7.BiomeCreeperEntity;
import com.thelads.smp.features.arise.nb7.BiomeCreeperVariant;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Natural spawn replacement for vanilla creepers in biome-specific regions.
 *
 * Mixin target: net.minecraft.world.entity.Mob
 * Method: public net.minecraft.world.entity.SpawnGroupData finalizeSpawn(net.minecraft.world.level.ServerLevelAccessor, net.minecraft.world.DifficultyInstance, net.minecraft.world.entity.EntitySpawnReason, net.minecraft.world.entity.SpawnGroupData);
 * Bytecode verified with javap on net/minecraft/world/entity/Mob.class (26.3).
 */
@Mixin(Mob.class)
public abstract class MixinMobSpawn {

    @Shadow public abstract boolean isPersistenceRequired();
    @Shadow public abstract boolean requiresCustomPersistence();

    @Inject(method = "finalizeSpawn", at = @At("RETURN"))
    private void ladssmp$replaceBiomeCreeperOnSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason, SpawnGroupData spawnData, CallbackInfoReturnable<SpawnGroupData> cir) {
        AriseNB7Config config = AriseNB7Config.get();
        if (!config.replaceDefaultCreeper || !config.allowSpawning) {
            return;
        }

        // Restrict replacement strictly to natural world spawns and chunk generation.
        // Exclude COMMAND, SPAWNER, SPAWN_ITEM_USE, and all other artificial or event-driven spawns.
        if (reason != EntitySpawnReason.NATURAL && reason != EntitySpawnReason.CHUNK_GENERATION) {
            return;
        }

        if (this.isPersistenceRequired() || this.requiresCustomPersistence()) {
            return;
        }

        // Only replace vanilla Creepers, not already-converted Biome Creepers
        if (!((Object) this instanceof Creeper creeper) || (Object) this instanceof BiomeCreeperEntity) {
            return;
        }

        if (creeper.hasCustomName()) {
            return;
        }

        BlockPos pos = creeper.blockPosition();
        Holder<Biome> biomeHolder = level.getBiome(pos);
        BiomeCreeperVariant variant = BiomeCreeperVariant.findVariantForBiome(biomeHolder, pos, level);
        if (variant == null || !config.isVariantEnabled(variant)) {
            return;
        }

        Level world = level.getLevel();
        BiomeCreeperEntity variantEntity = variant.getEntityType().create(world, EntitySpawnReason.CONVERSION);
        if (variantEntity != null) {
            variantEntity.setPos(creeper.getX(), creeper.getY(), creeper.getZ());
            variantEntity.setYRot(creeper.getYRot());
            variantEntity.setXRot(creeper.getXRot());
            variantEntity.setDeltaMovement(creeper.getDeltaMovement());
            if (creeper.isPowered()) {
                variantEntity.setPowered(true);
            }
            world.addFreshEntity(variantEntity);
            if (world instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                serverLevel.getServer().execute(creeper::discard);
            } else {
                creeper.discard();
            }
        }
    }
}
