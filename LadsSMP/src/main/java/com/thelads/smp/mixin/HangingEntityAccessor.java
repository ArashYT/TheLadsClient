package com.thelads.smp.mixin;

import net.minecraft.core.Direction;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.decoration.HangingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(HangingEntity.class)
public interface HangingEntityAccessor {
    @Accessor("DATA_DIRECTION")
    static EntityDataAccessor<Direction> ladssmp$dataDirection() { throw new AssertionError(); }

    @Accessor("DEFAULT_DIRECTION")
    static Direction ladssmp$defaultDirection() { throw new AssertionError(); }
}
