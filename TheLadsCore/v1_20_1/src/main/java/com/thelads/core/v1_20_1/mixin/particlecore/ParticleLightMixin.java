package com.thelads.core.v1_20_1.mixin.particlecore;

import com.thelads.core.client.ParticleCore;
import net.minecraft.client.particle.Particle;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Cheaper lightmap lookups: a particle asks the world for its light every frame. It now asks again only once it has moved to
 * another block or four ticks have passed (light barely changes in 200 ms), and otherwise reuses its last answer.
 */
@Mixin(Particle.class)
public abstract class ParticleLightMixin {
    @Shadow protected double x;
    @Shadow protected double y;
    @Shadow protected double z;
    @Shadow protected int age;
    @Unique private long ladsLightBlock = Long.MIN_VALUE;
    @Unique private int ladsLight, ladsLightAge = -1;

    @Inject(method = "getLightColor", at = @At("HEAD"), cancellable = true)
    private void ladsLightCached(float partialTick, CallbackInfoReturnable<Integer> cir) {
        if (!ParticleCore.optimized()) return;
        if (this.age == this.ladsLightAge) { ParticleCore.reused++; cir.setReturnValue(this.ladsLight); return; }
        int since = this.age - this.ladsLightAge;
        if (since >= 0 && since < 4 && BlockPos.asLong(Mth.floor(this.x), Mth.floor(this.y), Mth.floor(this.z)) == this.ladsLightBlock)
            { this.ladsLightAge = this.age; ParticleCore.reused++; cir.setReturnValue(this.ladsLight); }
    }

    @Inject(method = "getLightColor", at = @At("RETURN"))
    private void ladsLightRemember(float partialTick, CallbackInfoReturnable<Integer> cir) {
        if (!ParticleCore.optimized()) return;
        this.ladsLightBlock = BlockPos.asLong(Mth.floor(this.x), Mth.floor(this.y), Mth.floor(this.z));
        this.ladsLight = cir.getReturnValue();
        this.ladsLightAge = this.age;
    }
}
