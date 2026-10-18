package com.thelads.smp.features.arise.nb1.mixin;

import com.thelads.smp.features.arise.nb1.YouShallNotSpawnFeature;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.level.ServerLevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin target: net.minecraft.world.entity.Mob
 * Method: public net.minecraft.world.entity.SpawnGroupData finalizeSpawn(net.minecraft.world.level.ServerLevelAccessor, net.minecraft.world.DifficultyInstance, net.minecraft.world.entity.EntitySpawnReason, net.minecraft.world.entity.SpawnGroupData);
 */
@Mixin(Mob.class)
public abstract class MixinMobSpawn {

    @Shadow public abstract boolean isPersistenceRequired();
    @Shadow public abstract boolean requiresCustomPersistence();

    @Inject(method = "finalizeSpawn", at = @At("RETURN"))
    private void ladssmp$onFinalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason, SpawnGroupData spawnData, CallbackInfoReturnable<SpawnGroupData> cir) {
        if (!YouShallNotSpawnFeature.isEnabled()) return;
        if (reason == EntitySpawnReason.LOAD || this.isPersistenceRequired() || this.requiresCustomPersistence()) {
            return;
        }

        Mob mob = (Mob) (Object) this;
        if (mob.hasCustomName()) {
            return;
        }

        String dim = level.getLevel().dimension().identifier().toString();
        if (YouShallNotSpawnFeature.shouldBlockSpawn(mob, dim, level.getRandom())) {
            mob.discard();
        }
    }
}
