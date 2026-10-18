package com.thelads.smp.features.arise.nb7;

import com.thelads.smp.features.arise.nb7.mixin.CreeperAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Clean-room entity implementation for all 15 Creeper Overhaul biome variants.
 * Inherits vanilla Creeper mechanics while delivering signature elemental behavior twists.
 */
public class BiomeCreeperEntity extends Creeper {
    private final BiomeCreeperVariant variant;

    public BiomeCreeperEntity(EntityType<? extends BiomeCreeperEntity> type, Level level, BiomeCreeperVariant variant) {
        super(type, level);
        this.variant = variant;

        if (variant == BiomeCreeperVariant.BAMBOO && this instanceof CreeperAccessor accessor) {
            // Bamboo Creeper has a quick, agile fuse: 20 ticks (1.0s) vs standard 30 ticks (1.5s)
            accessor.ladssmp$setMaxSwell(20);
        }
    }

    public BiomeCreeperVariant getVariant() {
        return variant;
    }

    public void setPowered(boolean powered) {
        this.getEntityData().set(CreeperAccessor.ladssmp$getDataIsPowered(), powered);
    }

    public static AttributeSupplier.Builder createAttributes(BiomeCreeperVariant variant) {
        AttributeSupplier.Builder builder = Creeper.createAttributes();
        if (variant == BiomeCreeperVariant.CAVE) {
            // Stony hide grants natural armor and toughness
            builder.add(Attributes.ARMOR, 6.0);
            builder.add(Attributes.ARMOR_TOUGHNESS, 2.0);
        } else if (variant == BiomeCreeperVariant.BAMBOO) {
            // Agile runner in bamboo forests
            builder.add(Attributes.MOVEMENT_SPEED, 0.32);
        } else if (variant == BiomeCreeperVariant.OCEAN) {
            // Adapted for oceanic mobility
            builder.add(Attributes.WATER_MOVEMENT_EFFICIENCY, 1.0);
        }
        return builder;
    }

    @Override
    public boolean canBreatheUnderwater() {
        return this.variant == BiomeCreeperVariant.OCEAN || super.canBreatheUnderwater();
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putString("Variant", this.variant.getId());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
    }

    @Override
    public void tick() {
        super.tick();

        if (level() instanceof ServerLevel serverLevel && isAlive()) {
            BlockPos pos = blockPosition();

            // 1. Jungle Creeper: natural leaf camouflage (gains Invisibility when in or under canopy leaves)
            if (variant == BiomeCreeperVariant.JUNGLE) {
                if (serverLevel.getBlockState(pos).is(BlockTags.LEAVES) || serverLevel.getBlockState(pos.below()).is(BlockTags.LEAVES)) {
                    this.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 40, 0, false, false, false));
                }
            }

            // 2. Dark Oak Creeper: stalks prey in darkness, plunging nearby players into darkness while priming
            if (variant == BiomeCreeperVariant.DARK_OAK) {
                if (getSwellDir() > 0) {
                    List<ServerPlayer> players = serverLevel.getPlayers(p -> p.distanceToSqr(this) <= 64.0);
                    for (ServerPlayer player : players) {
                        player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 40, 0, false, false));
                    }
                }
            }

            // 3. Savanna Creeper: gains swiftness in direct hot sunlight
            if (variant == BiomeCreeperVariant.SAVANNAH) {
                if (serverLevel.dimensionType().hasSkyLight() && serverLevel.canSeeSky(pos) && serverLevel.getSkyDarken() < 4) {
                    this.addEffect(new MobEffectInstance(MobEffects.SPEED, 40, 0, false, false, false));
                }
            }
        }
    }

    /**
     * Executes the custom explosion with griefing controls and signature biome elemental twists.
     */
    public void customExplode() {
        if (!(level() instanceof ServerLevel serverLevel) || this.dead) {
            return;
        }

        this.dead = true;
        float power = this.isPowered() ? 2.0F : 1.0F;
        int baseRadius = ((CreeperAccessor) this).ladssmp$getExplosionRadius();
        float explosionRadius = (float) baseRadius * power;

        boolean allowDestroy = AriseNB7Config.get().destroyBlocks;
        Level.ExplosionInteraction interaction = allowDestroy ? Level.ExplosionInteraction.MOB : Level.ExplosionInteraction.NONE;
        boolean mobGriefing = serverLevel.getGameRules().get(GameRules.MOB_GRIEFING);

        double x = getX();
        double y = getY();
        double z = getZ();
        AABB effectBounds = new AABB(x - 6.0, y - 4.0, z - 6.0, x + 6.0, y + 4.0, z + 6.0);
        List<LivingEntity> nearbyLiving = serverLevel.getEntitiesOfClass(LivingEntity.class, effectBounds, e -> e != this && e.isAlive());

        // Signature Biome Behavior Twists
        switch (variant) {
            case SNOWY -> {
                // Frost Nova: inflicts Slowness II (8s) and scatters snow
                for (LivingEntity target : nearbyLiving) {
                    target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 160, 1));
                }
                if (allowDestroy && mobGriefing) {
                    BlockPos center = blockPosition();
                    for (int dx = -2; dx <= 2; dx++) {
                        for (int dz = -2; dz <= 2; dz++) {
                            BlockPos targetPos = center.offset(dx, 0, dz);
                            if (serverLevel.getBlockState(targetPos).isAir() && serverLevel.getBlockState(targetPos.below()).isSolid()) {
                                serverLevel.setBlockAndUpdate(targetPos, Blocks.SNOW.defaultBlockState());
                            }
                        }
                    }
                }
            }
            case DESERT -> {
                // Sand Burst: blinding sand cloud inflicts Blindness (5s) and Darkness (4s)
                for (LivingEntity target : nearbyLiving) {
                    target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 100, 0));
                    target.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 80, 0));
                }
            }
            case SWAMP -> {
                // Poison Cloud: lingering toxic swamp gas cloud
                AreaEffectCloud cloud = new AreaEffectCloud(serverLevel, x, y, z);
                cloud.setRadius(3.0F * power);
                cloud.setRadiusOnUse(-0.5F);
                cloud.setWaitTime(10);
                cloud.setDuration(240);
                cloud.setRadiusPerTick(-cloud.getRadius() / 240.0F);
                cloud.addEffect(new MobEffectInstance(MobEffects.POISON, 140, 0));
                serverLevel.addFreshEntity(cloud);
            }
            case BADLANDS -> {
                // Searing Ignition: catches surviving entities on fire for 6s
                for (LivingEntity target : nearbyLiving) {
                    target.igniteForSeconds(6.0F * power);
                }
                if (allowDestroy && mobGriefing) {
                    BlockPos center = blockPosition();
                    for (int dx = -1; dx <= 1; dx++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            BlockPos targetPos = center.offset(dx, 0, dz);
                            if (serverLevel.getBlockState(targetPos).isAir() && serverLevel.getBlockState(targetPos.below()).isSolid()) {
                                serverLevel.setBlockAndUpdate(targetPos, Blocks.FIRE.defaultBlockState());
                            }
                        }
                    }
                }
            }
            case OCEAN -> {
                // Aquatic Concussion: underwater shockwave deals concussive force through water
                for (LivingEntity target : nearbyLiving) {
                    if (target.isInWater()) {
                        Vec3 push = target.position().subtract(position()).normalize().scale(1.2 * power);
                        target.push(push.x, 0.2 * power, push.z);
                    }
                }
            }
            case BAMBOO -> {
                // Bamboo Shrapnel: intense outward kinetic knockback
                for (LivingEntity target : nearbyLiving) {
                    Vec3 push = target.position().subtract(position()).normalize().scale(1.5 * power);
                    target.push(push.x, 0.4 * power, push.z);
                }
            }
            case CAVE -> {
                // Stone Shrapnel: Mining Fatigue II (10s) to nearby subterranean miners
                for (LivingEntity target : nearbyLiving) {
                    target.addEffect(new MobEffectInstance(MobEffects.MINING_FATIGUE, 200, 1));
                }
            }
            case DRIPSTONE -> {
                // Pointed Stalactite Spikes: armor-piercing shrapnel damage
                for (LivingEntity target : nearbyLiving) {
                    target.hurtServer(serverLevel, damageSources().explosion(this, this), 5.0F * power);
                }
            }
            case MUSHROOM -> {
                // Spore Blast: disorienting spores inflicting Nausea (12s) and Hunger II (8s)
                for (LivingEntity target : nearbyLiving) {
                    target.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 240, 0));
                    target.addEffect(new MobEffectInstance(MobEffects.HUNGER, 160, 1));
                }
            }
            case BEACH -> {
                // Wet Sand Splash: weakens victims while dousing burning flames
                for (LivingEntity target : nearbyLiving) {
                    target.clearFire();
                    target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 120, 0));
                }
            }
            case SPRUCE -> {
                // Pine Needle Splinters: sharp thorns inflicting Wither I (4s)
                for (LivingEntity target : nearbyLiving) {
                    target.addEffect(new MobEffectInstance(MobEffects.WITHER, 80, 0));
                }
            }
            case SAVANNAH -> {
                // Arid Heatstroke: intense sun exhaustion inflicting Weakness II (6s)
                for (LivingEntity target : nearbyLiving) {
                    target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 120, 1));
                }
            }
            case DARK_OAK -> {
                // Deep Shadows: plunges caught players into Darkness (8s)
                for (LivingEntity target : nearbyLiving) {
                    target.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 160, 0));
                }
            }
            case HILLS -> {
                // Mountain Updraft: vertical wind vortex tossing victims high into the air
                for (LivingEntity target : nearbyLiving) {
                    target.push(0, 1.25 * power, 0);
                }
            }
            case JUNGLE -> {
                // Toxic Canopy Spores: inflicts Poison I (4s)
                for (LivingEntity target : nearbyLiving) {
                    target.addEffect(new MobEffectInstance(MobEffects.POISON, 80, 0));
                }
            }
        }

        // Standard explosion
        serverLevel.explode(this, x, y, z, explosionRadius, interaction);
        this.triggerOnDeathMobEffects(serverLevel, Entity.RemovalReason.KILLED);
        this.discard();
    }
}
