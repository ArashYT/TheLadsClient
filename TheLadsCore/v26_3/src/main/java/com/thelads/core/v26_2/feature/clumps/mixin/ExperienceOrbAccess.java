// Adapted from Clumps 26.2.1, Copyright (c) 2021 Jaredlll08, MIT.
package com.thelads.core.v26_2.feature.clumps.mixin;

import net.minecraft.world.entity.ExperienceOrb;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ExperienceOrb.class)
public interface ExperienceOrbAccess {
    @Accessor("age") int ladsClumps$age();
    @Accessor("age") void ladsClumps$age(int value);
    @Accessor("count") int ladsClumps$count();
    @Accessor("count") void ladsClumps$count(int value);
    @Invoker("canMerge") boolean ladsClumps$canMerge(ExperienceOrb other);
    @Invoker("merge") void ladsClumps$merge(ExperienceOrb other);
    @Invoker("scanForMerges") void ladsClumps$scan();
}
