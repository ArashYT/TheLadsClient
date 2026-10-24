package com.thelads.core.v1_8_9.mixin.particlecore;

import com.thelads.core.client.ParticleCore;
import net.minecraft.client.particle.EntityFX;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Particle Core's per-type look (size, color, fade out), set at spawn by EffectRendererMixin. The base renderParticle draws every
 * quad (subclasses such as the crit particle compute their scale and then call it), so its head swaps in the styled scale, color
 * and alpha and its return puts the particle's own values back: the particle's own animation never sees the change.
 */
@Mixin(EntityFX.class)
public abstract class EntityFXStyleMixin implements ParticleCore.Styled {
    @Shadow protected int particleAge;
    @Shadow protected int particleMaxAge;
    @Shadow protected float particleScale;
    @Shadow protected float particleRed;
    @Shadow protected float particleGreen;
    @Shadow protected float particleBlue;
    @Shadow protected float particleAlpha;
    @Unique private float ladsSize = 1, ladsFade;
    @Unique private int ladsColor;
    @Unique private boolean ladsStyled, ladsApplied;
    @Unique private float ladsScale, ladsRed, ladsGreen, ladsBlue, ladsAlpha;

    @Override
    public void lads$style(float size, int color, float fade) {
        ladsSize = size; ladsColor = color; ladsFade = fade;
        ladsStyled = size != 1 || color != 0 || fade > 0;
    }

    @Inject(method = "renderParticle", at = @At("HEAD"))
    private void ladsStyleBegin(WorldRenderer buffer, Entity entity, float partialTicks, float rx, float rz, float ryz, float rxy, float rxz, CallbackInfo ci) {
        ladsApplied = ladsStyled && ParticleCore.styled();
        if (!ladsApplied) return;
        ladsScale = particleScale; ladsRed = particleRed; ladsGreen = particleGreen; ladsBlue = particleBlue; ladsAlpha = particleAlpha;
        particleScale *= ladsSize;
        if (ladsColor != 0) {
            particleRed = (ladsColor >> 16 & 255) / 255f;
            particleGreen = (ladsColor >> 8 & 255) / 255f;
            particleBlue = (ladsColor & 255) / 255f;
        }
        particleAlpha *= ParticleCore.fadeAlpha(ladsFade, particleAge + partialTicks, particleMaxAge);
    }

    @Inject(method = "renderParticle", at = @At("RETURN"))
    private void ladsStyleEnd(WorldRenderer buffer, Entity entity, float partialTicks, float rx, float rz, float ryz, float rxy, float rxz, CallbackInfo ci) {
        if (!ladsApplied) return;
        ladsApplied = false;
        particleScale = ladsScale; particleRed = ladsRed; particleGreen = ladsGreen; particleBlue = ladsBlue; particleAlpha = ladsAlpha;
    }
}
