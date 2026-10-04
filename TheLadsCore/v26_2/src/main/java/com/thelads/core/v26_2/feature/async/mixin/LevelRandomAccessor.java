package com.thelads.core.v26_2.feature.async.mixin;

import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Level.class)
public interface LevelRandomAccessor {
    @Mutable @Accessor("random") void lads$setRandom(RandomSource random);
}
