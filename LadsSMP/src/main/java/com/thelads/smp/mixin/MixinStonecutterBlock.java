package com.thelads.smp.mixin;

import com.thelads.smp.config.LadsConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.StonecutterBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Consolidates 'mr_stonecutter_damage': Entities walking or standing on active Stonecutters
 * suffer 1.0f physical damage using the stonecutter damage type, unless in the stonecutter_damage:blacklist tag.
 */
@Mixin(StonecutterBlock.class)
public abstract class MixinStonecutterBlock extends Block {
    private static final TagKey<EntityType<?>> STONECUTTER_BLACKLIST = TagKey.create(
            Registries.ENTITY_TYPE,
            Identifier.fromNamespaceAndPath("stonecutter_damage", "blacklist"));

    private static final ResourceKey<DamageType> STONECUTTER_DAMAGE_TYPE = ResourceKey.create(
            Registries.DAMAGE_TYPE,
            Identifier.fromNamespaceAndPath("stonecutter_damage", "stonecutter"));

    public MixinStonecutterBlock(Properties properties) {
        super(properties);
    }

    @Override
    public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
        super.stepOn(level, pos, state, entity);
        if (!LadsConfig.get().features.stonecutterDamage) return;
        if (!(level instanceof ServerLevel serverLevel)) return;
        if (serverLevel.getGameTime() % 20 != 0) return; // original datapack: 1 damage every second
        if (!entity.isAlive() || entity.getType().builtInRegistryHolder().is(STONECUTTER_BLACKLIST)) return;
        try {
            Holder<DamageType> damageType = serverLevel.registryAccess()
                    .lookupOrThrow(Registries.DAMAGE_TYPE)
                    .getOrThrow(STONECUTTER_DAMAGE_TYPE);
            entity.hurtServer(serverLevel, new DamageSource(damageType, Vec3.atCenterOf(pos)), 1.0F);
        } catch (Exception ignored) {
        }
    }
}
