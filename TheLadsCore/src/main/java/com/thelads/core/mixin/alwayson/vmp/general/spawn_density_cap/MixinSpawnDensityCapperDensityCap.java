/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  it.unimi.dsi.fastutil.objects.Object2IntMap
 *  net.minecraft.world.entity.MobCategory
 *  net.minecraft.world.level.LocalMobCapCalculator$MobCounts
 *  org.spongepowered.asm.mixin.Final
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.Mutable
 *  org.spongepowered.asm.mixin.Overwrite
 *  org.spongepowered.asm.mixin.Shadow
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
package com.thelads.core.mixin.alwayson.vmp.general.spawn_density_cap;

import com.thelads.core.features.alwayson.vmp.common.general.spawn_density_cap.SpawnDensityCapperDensityCapDelegate;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.LocalMobCapCalculator;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={LocalMobCapCalculator.MobCounts.class})
public class MixinSpawnDensityCapperDensityCap {
    @Mutable
    @Shadow
    @Final
    private Object2IntMap<MobCategory> counts;
    private final int[] spawnGroupDensities = new int[MobCategory.values().length];

    @Inject(method={"<init>"}, at={@At(value="RETURN")})
    private void onInit(CallbackInfo ci) {
        this.counts = SpawnDensityCapperDensityCapDelegate.delegateSpawnGroupDensities(this.spawnGroupDensities);
    }

    @Overwrite
    public void add(MobCategory spawnGroup) {
        int n = spawnGroup.ordinal();
        this.spawnGroupDensities[n] = this.spawnGroupDensities[n] + 1;
    }

    @Overwrite
    public boolean canSpawn(MobCategory spawnGroup) {
        return this.spawnGroupDensities[spawnGroup.ordinal()] < spawnGroup.getMaxInstancesPerChunk();
    }
}

