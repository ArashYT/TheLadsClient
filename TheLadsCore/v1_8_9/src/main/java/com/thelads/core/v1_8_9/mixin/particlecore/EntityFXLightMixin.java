package com.thelads.core.v1_8_9.mixin.particlecore;

import com.thelads.core.client.ParticleCore;
import net.minecraft.client.particle.EntityFX;
import net.minecraft.util.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Particle Core, Optimized rendering: a particle asks the world for its light (two neighbour-light lookups) every frame it is
 * drawn. It now asks again only once it has moved to another block or four ticks have passed, and otherwise reuses its last
 * answer. OptiFine patches EntityFX: require = 0 leaves vanilla if the call moves.
 */
@Mixin(EntityFX.class)
public abstract class EntityFXLightMixin {
    @Unique private long ladsLightBlock = Long.MIN_VALUE;
    @Unique private int ladsLight;
    @Unique private long ladsLightTick;

    @Redirect(method = "renderParticle", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/particle/EntityFX;getBrightnessForRender(F)I"), require = 0)
    private int ladsLight(EntityFX particle, float partialTicks) {
        if (!ParticleCore.optimized()) return particle.getBrightnessForRender(partialTicks);
        long block = ((long) MathHelper.floor_double(particle.posX) & 0x3FFFFFFL) << 38 | ((long) MathHelper.floor_double(particle.posY) & 0xFFFL) << 26
            | (long) MathHelper.floor_double(particle.posZ) & 0x3FFFFFFL;
        long now = particle.worldObj.getTotalWorldTime(), since = now - this.ladsLightTick;
        if (block == this.ladsLightBlock && since >= 0 && since < 4) { ParticleCore.reused++; return this.ladsLight; }
        int light = particle.getBrightnessForRender(partialTicks);
        this.ladsLightBlock = block;
        this.ladsLight = light;
        this.ladsLightTick = now;
        return light;
    }
}
