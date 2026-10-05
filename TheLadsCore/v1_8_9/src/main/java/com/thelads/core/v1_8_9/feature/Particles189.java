package com.thelads.core.v1_8_9.feature;

import com.thelads.core.client.ClientTools;
import com.thelads.core.v1_8_9.mixin.EntityFXAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.EntityFX;
import net.minecraft.client.renderer.culling.ClippingHelperImpl;
import net.minecraft.util.EnumParticleTypes;

/**
 * Particles on 1.8.9 (EffectRendererMixin). With Entity Culling on, a particle wholly outside this frame's view frustum is not
 * drawn: the planes come from the matrices the particles are drawn with, so nothing on screen changes, also while turning.
 * ParticleBudget (off by default, as on 26.x) limits only decorative particles (smoke, mycelium spores, void and underwater
 * motes) to its "Particles per tick" within its "Distance"; crits, hits, potions and other gameplay particles are never limited.
 */
public final class Particles189 {
    private static final ClippingHelperImpl FRUSTUM = new ClippingHelperImpl();
    private static final ClientTools.ParticleBudget BUDGET = new ClientTools.ParticleBudget();
    private static final int[] DECORATIVE = {EnumParticleTypes.SMOKE_NORMAL.getParticleID(), EnumParticleTypes.SMOKE_LARGE.getParticleID(),
        EnumParticleTypes.TOWN_AURA.getParticleID(), EnumParticleTypes.SUSPENDED.getParticleID(), EnumParticleTypes.SUSPENDED_DEPTH.getParticleID()};
    private static boolean culling;
    /** QA (Probe173Cull): particles drawn and skipped since the last reset. */
    static int drawn, skipped;

    private Particles189() {}

    /** EffectRenderer.renderParticles HEAD: this frame's planes, read from the GL matrices the particles use. */
    public static void beginFrame() {
        culling = Options189.enabled(EntityCulling189.MODULE);
        if (culling) FRUSTUM.init();
    }

    public static boolean inView(EntityFX particle, float partialTicks) {
        if (!culling) return true;
        double x = particle.prevPosX + (particle.posX - particle.prevPosX) * partialTicks - EntityFX.interpPosX;
        double y = particle.prevPosY + (particle.posY - particle.prevPosY) * partialTicks - EntityFX.interpPosY;
        double z = particle.prevPosZ + (particle.posZ - particle.prevPosZ) * partialTicks - EntityFX.interpPosZ;
        double r = 1 + 0.15 * Math.abs(((EntityFXAccessor) particle).ladsScale()); // the quad's half-diagonal, with room to spare
        for (int i = 0; i < 6; i++) {
            float[] plane = FRUSTUM.frustum[i];
            if (plane[0] * x + plane[1] * y + plane[2] * z + plane[3] <= -r) { skipped++; return false; }
        }
        drawn++;
        return true;
    }

    /** EffectRenderer.spawnEffectParticle: false drops a decorative particle over the ParticleBudget. */
    public static boolean allow(int id, double x, double y, double z) {
        if (!Options189.enabled("ParticleBudget")) return true;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.theWorld == null) return true;
        boolean decorative = false;
        for (int d : DECORATIVE) decorative |= d == id;
        return !decorative || BUDGET.allow(mc.theWorld.getTotalWorldTime(), mc.thePlayer.getDistanceSq(x, y, z),
            Options189.number("ParticleBudget", "Distance", 48), (int) Options189.number("ParticleBudget", "Particles per tick", 64));
    }
}
