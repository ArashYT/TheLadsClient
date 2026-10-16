package com.thelads.smp.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.sugar.Local;
import com.thelads.smp.config.LadsConfig;
import com.thelads.smp.features.ZombieImprovementsManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Consolidates 'zombieimprovements': Leader zombie attributes (scale, bonus damage, full heal on leader promotion),
 * reinforcement spawn sounds, spawner restrictions, and unique leader sounds.
 */
@Mixin(Zombie.class)
public abstract class MixinZombie extends Mob {

    @Unique
    private int ladssmp$soundTimer = 0;

    @Unique
    private Zombie ladssmp$soundSource = null;

    @Unique
    private EntitySpawnReason ladssmp$spawnType = null;

    protected MixinZombie(EntityType<? extends Mob> entityType, Level level) {
        super(entityType, level);
    }

    @Inject(
            method = "hurtServer",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerLevel;addFreshEntityWithPassengers(Lnet/minecraft/world/entity/Entity;)V"
            )
    )
    private void ladssmp$playSoundOnReinforcementsSpawn(
            ServerLevel level,
            DamageSource source,
            float damage,
            CallbackInfoReturnable<Boolean> cir,
            @Local Zombie reinforcement
    ) {
        LadsConfig.FeatureSettings cfg = LadsConfig.get().features;
        if (cfg.zombieImprovements && cfg.zombieReinforcementSpawnSounds) {
            this.ladssmp$soundTimer = 40;
            this.ladssmp$soundSource = reinforcement;
        }
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void ladssmp$tickReinforcementSound(CallbackInfo ci) {
        if (!LadsConfig.get().features.zombieImprovements) return;
        if (this.level() instanceof ServerLevel
                && this.isAlive()
                && !this.isNoAi()
                && this.ladssmp$soundTimer > 0
                && this.ladssmp$soundSource != null) {
            if (this.ladssmp$soundTimer % 8 == 0) {
                this.playSound(
                        this.ladssmp$soundSource.getBlockStateOn().getSoundType().getBreakSound(),
                        LadsConfig.get().features.zombieSpawnSoundVolume,
                        1.0f
                );
            }
            this.ladssmp$soundTimer--;
        }
    }

    @Inject(
            method = "handleAttributes",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/monster/zombie/Zombie;setCanBreakDoors(Z)V"
            )
    )
    private void ladssmp$onLeaderAttributeAssignment(float difficultyModifier, EntitySpawnReason spawnReason, CallbackInfo ci) {
        LadsConfig.FeatureSettings cfg = LadsConfig.get().features;
        if (!cfg.zombieImprovements) return;

        Zombie zombie = (Zombie) (Object) this;
        this.heal(this.getMaxHealth());
        ZombieImprovementsManager.setLeader(zombie);

        AttributeInstance scaleAttr = zombie.getAttribute(Attributes.SCALE);
        if (scaleAttr != null) {
            scaleAttr.addOrReplacePermanentModifier(new AttributeModifier(
                    ZombieImprovementsManager.id("leader_scale"),
                    cfg.zombieLeaderSizeIncrease,
                    AttributeModifier.Operation.ADD_VALUE
            ));
        }

        if (cfg.zombieLeaderIncreasedDamage) {
            double r = this.random.nextDouble();
            AttributeInstance dmgAttr = zombie.getAttribute(Attributes.ATTACK_DAMAGE);
            if (dmgAttr != null) {
                dmgAttr.addOrReplacePermanentModifier(new AttributeModifier(
                        ZombieImprovementsManager.id("leader_attack_damage"),
                        ZombieImprovementsManager.computeLeaderDamageBonus(r),
                        AttributeModifier.Operation.ADD_VALUE
                ));
            }
        }
    }

    @Inject(method = "finalizeSpawn", at = @At("HEAD"))
    private void ladssmp$captureSpawnReason(
            ServerLevelAccessor level,
            DifficultyInstance difficulty,
            EntitySpawnReason spawnReason,
            SpawnGroupData groupData,
            CallbackInfoReturnable<SpawnGroupData> cir
    ) {
        this.ladssmp$spawnType = spawnReason;
    }

    @ModifyExpressionValue(
            method = "handleAttributes",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/RandomSource;nextFloat()F"
            )
    )
    private float ladssmp$noLeaderIfFromSpawner(float original) {
        LadsConfig.FeatureSettings cfg = LadsConfig.get().features;
        if (cfg.zombieImprovements && cfg.zombieNoLeaderFromSpawners && EntitySpawnReason.isSpawner(this.ladssmp$spawnType)) {
            return 1.0f;
        }
        return original;
    }

    @WrapWithCondition(
            method = "handleAttributes",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/ai/attributes/AttributeInstance;addOrReplacePermanentModifier(Lnet/minecraft/world/entity/ai/attributes/AttributeModifier;)V",
                    ordinal = 2
            )
    )
    private boolean ladssmp$noReinforcementsModifierIfFromSpawner(AttributeInstance instance, AttributeModifier modifier) {
        LadsConfig.FeatureSettings cfg = LadsConfig.get().features;
        return !(cfg.zombieImprovements && cfg.zombieNoReinforcementsFromSpawners && EntitySpawnReason.isSpawner(this.ladssmp$spawnType));
    }

    @WrapWithCondition(
            method = "handleAttributes",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/monster/zombie/Zombie;randomizeReinforcementsChance()V"
            )
    )
    private boolean ladssmp$noRandomizeReinforcementsIfFromSpawner(Zombie instance) {
        LadsConfig.FeatureSettings cfg = LadsConfig.get().features;
        return !(cfg.zombieImprovements && cfg.zombieNoReinforcementsFromSpawners && EntitySpawnReason.isSpawner(this.ladssmp$spawnType));
    }

    @ModifyExpressionValue(
            method = "handleAttributes",
            at = @At(
                    value = "CONSTANT",
                    args = "floatValue=0.05"
            )
    )
    private float ladssmp$modifyLeaderMaxSpawnChance(float original) {
        LadsConfig.FeatureSettings cfg = LadsConfig.get().features;
        return cfg.zombieImprovements ? cfg.zombieLeaderMaxSpawnChance : original;
    }

    @ModifyExpressionValue(
            method = "hurtServer",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerLevel;isSpawningMonsters()Z"
            )
    )
    private boolean ladssmp$onlyLeaderSpawnsReinforcements(boolean original) {
        LadsConfig.FeatureSettings cfg = LadsConfig.get().features;
        if (cfg.zombieImprovements && cfg.zombieOnlyLeaderSpawnsReinforcements) {
            return original && ZombieImprovementsManager.isLeader((Zombie) (Object) this);
        }
        return original;
    }

    @ModifyReturnValue(method = "getAmbientSound", at = @At("RETURN"))
    private SoundEvent ladssmp$modifyAmbientSound(SoundEvent original) {
        LadsConfig.FeatureSettings cfg = LadsConfig.get().features;
        if (cfg.zombieImprovements && cfg.zombieLeaderUniqueSounds && ZombieImprovementsManager.isLeader(this)) {
            return ZombieImprovementsManager.AMBIENT;
        }
        return original;
    }

    @ModifyReturnValue(method = "getHurtSound", at = @At("RETURN"))
    private SoundEvent ladssmp$modifyHurtSound(SoundEvent original) {
        LadsConfig.FeatureSettings cfg = LadsConfig.get().features;
        if (cfg.zombieImprovements && cfg.zombieLeaderUniqueSounds && ZombieImprovementsManager.isLeader(this)) {
            return ZombieImprovementsManager.HURT;
        }
        return original;
    }

    @ModifyReturnValue(method = "getDeathSound", at = @At("RETURN"))
    private SoundEvent ladssmp$modifyDeathSound(SoundEvent original) {
        LadsConfig.FeatureSettings cfg = LadsConfig.get().features;
        if (cfg.zombieImprovements && cfg.zombieLeaderUniqueSounds && ZombieImprovementsManager.isLeader(this)) {
            return ZombieImprovementsManager.DEATH;
        }
        return original;
    }
}
