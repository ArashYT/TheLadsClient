// Adapted from Clumps 26.2.1 (Lads 26.3 copy), ported to Minecraft 1.20.1 by The Lads, Copyright (c) 2021 Jaredlll08, MIT.
package com.thelads.core.v1_20_1.feature.clumps.mixin;

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
    /** 1.20.1 names the periodic orb scan (and merge) scanForEntities. */
    @Invoker("scanForEntities") void ladsClumps$scan();
}
