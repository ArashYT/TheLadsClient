package com.thelads.smp.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Consolidates 'netherportalfix': Accessor for LivingEntity.lastPos to record destination portal coordinates.
 */
@Mixin(LivingEntity.class)
public interface MixinLivingEntityAccessor {
    @Accessor("lastPos")
    @Nullable
    BlockPos ladssmp$getLastPos();
}
