/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.core.Holder
 *  net.minecraft.server.level.WorldGenRegion
 *  net.minecraft.world.level.biome.Biome
 */
package com.thelads.core.features.alwayson.vmp.common.general.cache_ops.biome;

import net.minecraft.core.Holder;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.biome.Biome;

public interface PreloadingBiome {
    public void vmp$tryPreloadBiome(WorldGenRegion var1);

    public void vmp$tryReloadBiome(WorldGenRegion var1);

    public Holder<Biome> vmp$getBiomeCached(int var1, int var2, int var3);
}

