package com.thelads.core.v26_2.mixin.particlecore;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.thelads.core.client.ParticleCore;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Particle Core's per-type look (size, color, fade out) for flat particles, set at spawn by ParticleEngineMixin. Every flat
 * particle hands its quad to the frame's render state in one call; that call gets the styled size and color, and a fading particle
 * moves to the translucent layer of its atlas so its alpha shows. The particle's own fields are never touched.
 */
@Mixin(SingleQuadParticle.class)
public abstract class SingleQuadParticleStyleMixin extends Particle implements ParticleCore.Styled {
    @Unique private float ladsSize = 1, ladsFade;
    @Unique private int ladsColor;
    @Unique private boolean ladsStyled;

    protected SingleQuadParticleStyleMixin(ClientLevel level, double x, double y, double z) { super(level, x, y, z); }

    @Override
    public void lads$style(float size, int color, float fade) {
        ladsSize = size; ladsColor = color; ladsFade = fade;
        ladsStyled = size != 1 || color != 0 || fade > 0;
    }

    // require = 0: a modpack mod that wraps this same call (Zero SMP, the SMP pack) leaves the hook out instead of crashing the game.
    @WrapOperation(method = "extractRotatedQuad(Lnet/minecraft/client/renderer/state/level/QuadParticleRenderState;Lorg/joml/Quaternionf;FFFF)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/state/level/QuadParticleRenderState;add(Lnet/minecraft/client/particle/SingleQuadParticle$Layer;FFFFFFFFFFFFII)V"), require = 0)
    private void ladsStyle(QuadParticleRenderState state, SingleQuadParticle.Layer layer, float x, float y, float z, float qx, float qy, float qz, float qw,
                           float size, float u0, float u1, float v0, float v1, int color, int light, Operation<Void> original,
                           @Local(argsOnly = true, ordinal = 3) float partialTick) {
        if (ladsStyled && ParticleCore.styled()) {
            size *= ladsSize;
            if (ladsColor != 0) color = color & 0xFF000000 | ladsColor & 0xFFFFFF;
            if (ladsFade > 0) {
                int alpha = (int) ((color >>> 24) * ParticleCore.fadeAlpha(ladsFade, age + partialTick, lifetime));
                color = color & 0xFFFFFF | alpha << 24;
                layer = translucent(layer);
            }
        }
        original.call(state, layer, x, y, z, qx, qy, qz, qw, size, u0, u1, v0, v1, color, light);
    }

    @Unique
    private static SingleQuadParticle.Layer translucent(SingleQuadParticle.Layer layer) {
        if (layer == SingleQuadParticle.Layer.OPAQUE) return SingleQuadParticle.Layer.TRANSLUCENT;
        if (layer == SingleQuadParticle.Layer.OPAQUE_TERRAIN) return SingleQuadParticle.Layer.TRANSLUCENT_TERRAIN;
        return layer == SingleQuadParticle.Layer.OPAQUE_ITEMS ? SingleQuadParticle.Layer.TRANSLUCENT_ITEMS : layer;
    }
}
