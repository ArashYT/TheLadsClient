package com.thelads.core.v1_20_1.perf.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.thelads.core.v1_20_1.perf.SkyFogColor1201;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.CubicSampler;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** The sky colour's biome blend, once per tick (SkyFogColor1201). Wraps Sodium's/Embeddium's redirect of the same call. */
@Mixin(ClientLevel.class)
abstract class SkyColorMixin {
    @WrapOperation(method = "getSkyColor", require = 0, at = @At(value = "INVOKE",
        target = "Lnet/minecraft/util/CubicSampler;gaussianSampleVec3(Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/util/CubicSampler$Vec3Fetcher;)Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 lads$tickSampledSky(Vec3 quart, CubicSampler.Vec3Fetcher fetcher, Operation<Vec3> original) {
        return SkyFogColor1201.sample(SkyFogColor1201.SKY, quart, fetcher, original);
    }
}
