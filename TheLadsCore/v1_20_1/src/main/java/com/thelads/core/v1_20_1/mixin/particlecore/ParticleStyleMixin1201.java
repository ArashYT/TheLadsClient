package com.thelads.core.v1_20_1.mixin.particlecore;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.thelads.core.client.ParticleCore;
import com.thelads.core.v1_20_1.feature.ParticleCore1201;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.SingleQuadParticle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Particle Core's per-type look (size, color, fade out) for flat particles, set at spawn by ParticleEngineMixin. The head of render
 * swaps the styled size, color and alpha in, ahead of Particle Core's and Sodium's own render hooks (priority 400), so every way
 * the quad gets built reads them; its return, or the next tick when a later hook cancelled the method, puts the particle's own
 * values back, so the particle's animation never sees the change.
 */
@Mixin(value = SingleQuadParticle.class, priority = 400)
public abstract class ParticleStyleMixin1201 extends Particle implements ParticleCore.Styled, ParticleCore1201.Look {
    @Shadow protected float quadSize;
    @Unique private float ladsSize = 1, ladsFade;
    @Unique private int ladsColor;
    @Unique private boolean ladsStyled, ladsApplied;
    @Unique private float ladsQuad, ladsRed, ladsGreen, ladsBlue, ladsAlpha;

    protected ParticleStyleMixin1201(ClientLevel level, double x, double y, double z) { super(level, x, y, z); }

    @Override
    public void lads$style(float size, int color, float fade) {
        ladsSize = size; ladsColor = color; ladsFade = fade;
        ladsStyled = size != 1 || color != 0 || fade > 0;
    }

    @Override public boolean lads$fades() { return ladsFade > 0; }

    @Override
    public void lads$end() {
        if (!ladsApplied) return;
        ladsApplied = false;
        quadSize = ladsQuad; rCol = ladsRed; gCol = ladsGreen; bCol = ladsBlue; alpha = ladsAlpha;
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void ladsStyleBegin(VertexConsumer buffer, Camera camera, float partialTick, CallbackInfo ci) {
        lads$end();
        if (!ladsStyled || !ParticleCore.styled()) return;
        ladsApplied = true;
        ladsQuad = quadSize; ladsRed = rCol; ladsGreen = gCol; ladsBlue = bCol; ladsAlpha = alpha;
        quadSize *= ladsSize;
        if (ladsColor != 0) {
            rCol = (ladsColor >> 16 & 255) / 255f;
            gCol = (ladsColor >> 8 & 255) / 255f;
            bCol = (ladsColor & 255) / 255f;
        }
        alpha *= ParticleCore.fadeAlpha(ladsFade, age + partialTick, lifetime);
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void ladsStyleEnd(VertexConsumer buffer, Camera camera, float partialTick, CallbackInfo ci) { lads$end(); }
}
