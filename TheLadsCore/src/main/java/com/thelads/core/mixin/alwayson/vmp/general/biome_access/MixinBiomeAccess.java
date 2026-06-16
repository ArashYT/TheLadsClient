/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.core.BlockPos
 *  net.minecraft.core.Holder
 *  net.minecraft.util.Mth
 *  net.minecraft.world.level.biome.Biome
 *  net.minecraft.world.level.biome.BiomeManager
 *  net.minecraft.world.level.biome.BiomeManager$NoiseBiomeSource
 *  org.spongepowered.asm.mixin.Final
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.Overwrite
 *  org.spongepowered.asm.mixin.Shadow
 */
package com.thelads.core.mixin.alwayson.vmp.general.biome_access;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.util.Mth;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(value={BiomeManager.class})
public class MixinBiomeAccess {
    @Shadow
    @Final
    private long biomeZoomSeed;
    @Shadow
    @Final
    private BiomeManager.NoiseBiomeSource noiseBiomeSource;

    @Overwrite
    public Holder<Biome> getBiome(BlockPos pos) {
        int var0 = pos.getX() - 2;
        int var1 = pos.getY() - 2;
        int var2 = pos.getZ() - 2;
        int var3 = var0 >> 2;
        int var4 = var1 >> 2;
        int var5 = var2 >> 2;
        double var6 = (double)(var0 & 3) / 4.0;
        double var7 = (double)(var1 & 3) / 4.0;
        double var8 = (double)(var2 & 3) / 4.0;
        int var9 = 0;
        double var10 = Double.POSITIVE_INFINITY;
        for (int var11 = 0; var11 < 8; ++var11) {
            boolean var12 = (var11 & 4) == 0;
            boolean var13 = (var11 & 2) == 0;
            boolean var14 = (var11 & 1) == 0;
            long var15 = var12 ? (long)var3 : (long)(var3 + 1);
            long var16 = var13 ? (long)var4 : (long)(var4 + 1);
            long var17 = var14 ? (long)var5 : (long)(var5 + 1);
            double var18 = var12 ? var6 : var6 - 1.0;
            double var19 = var13 ? var7 : var7 - 1.0;
            double var20 = var14 ? var8 : var8 - 1.0;
            long var21 = this.biomeZoomSeed * (this.biomeZoomSeed * 6364136223846793005L + 1442695040888963407L) + var15;
            var21 = var21 * (var21 * 6364136223846793005L + 1442695040888963407L) + var16;
            var21 = var21 * (var21 * 6364136223846793005L + 1442695040888963407L) + var17;
            var21 = var21 * (var21 * 6364136223846793005L + 1442695040888963407L) + var15;
            var21 = var21 * (var21 * 6364136223846793005L + 1442695040888963407L) + var16;
            var21 = var21 * (var21 * 6364136223846793005L + 1442695040888963407L) + var17;
            double var22 = (double)(var21 >> 24 & 0x3FFL) / 1024.0;
            double var23 = (var22 - 0.5) * 0.9;
            var21 = var21 * (var21 * 6364136223846793005L + 1442695040888963407L) + this.biomeZoomSeed;
            double var24 = (double)(var21 >> 24 & 0x3FFL) / 1024.0;
            double var25 = (var24 - 0.5) * 0.9;
            double var26 = (double)((var21 = var21 * (var21 * 6364136223846793005L + 1442695040888963407L) + this.biomeZoomSeed) >> 24 & 0x3FFL) / 1024.0;
            double var27 = (var26 - 0.5) * 0.9;
            double var28 = Mth.square((double)(var20 + var27)) + Mth.square((double)(var19 + var25)) + Mth.square((double)(var18 + var23));
            if (!(var10 > var28)) continue;
            var9 = var11;
            var10 = var28;
        }
        int resX = (var9 & 4) == 0 ? var3 : var3 + 1;
        int resY = (var9 & 2) == 0 ? var4 : var4 + 1;
        int resZ = (var9 & 1) == 0 ? var5 : var5 + 1;
        return this.noiseBiomeSource.getNoiseBiome(resX, resY, resZ);
    }
}

