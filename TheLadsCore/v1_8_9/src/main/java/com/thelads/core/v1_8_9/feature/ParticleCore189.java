package com.thelads.core.v1_8_9.feature;

import com.thelads.core.client.ParticleCore;
import com.thelads.core.config.ModuleSupport;
import com.thelads.core.config.ParticleReductionOption;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.EntityFX;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

/**
 * Particle Core on 1.8.9, through EffectRendererMixin (view test, spawn rates) and EntityFXLightMixin (lightmap lookups). With
 * Culling on, a particle wholly outside this frame's view frustum is not drawn: the planes come from the matrices the particles
 * are drawn with, so nothing on screen changes, also while turning. 1.8.9 already builds a particle's four corners from five
 * precomputed vectors, so Optimized rendering here is the cheaper lightmap lookup alone. Ids are the names /particle takes,
 * lower-case ("largesmoke", "mobspell"; the ones that take arguments without their trailing underscore).
 */
public final class ParticleCore189 {
    private static final FloatBuffer PROJECTION = BufferUtils.createFloatBuffer(16), MODELVIEW = BufferUtils.createFloatBuffer(16);
    private static final float[] PROJECTION_ARRAY = new float[16], MODELVIEW_ARRAY = new float[16];
    private static final Map<Integer, String> NAMES = new HashMap<Integer, String>();
    private static Object world;

    static {
        for (EnumParticleTypes type : EnumParticleTypes.values()) NAMES.put(type.getParticleID(), name(type));
    }

    private ParticleCore189() {}

    private static String name(EnumParticleTypes type) {
        String name = type.getParticleName().toLowerCase(Locale.ROOT);
        return name.endsWith("_") ? name.substring(0, name.length() - 1) : name;
    }

    public static void register() {
        ModuleSupport.registerBuiltIn(ParticleCore.MODULE);
        ((ParticleReductionOption) ParticleCore.module().getOption(ParticleCore.RATES)).setCatalog(ParticleCore189::catalog);
        MinecraftForge.EVENT_BUS.register(new ParticleCore189());
    }

    @SubscribeEvent
    public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        ParticleCore.refresh();
        Minecraft mc = Minecraft.getMinecraft();
        if (world != mc.theWorld) { world = mc.theWorld; ParticleCore.worldChanged(); }
    }

    /** EffectRenderer.renderParticles HEAD: this frame's planes, read from the GL matrices the particles use. */
    public static void beginFrame() {
        ParticleCore.timeBegin();
        ParticleCore.refresh();
        if (!ParticleCore.culling()) { ParticleCore.FRUSTUM.clear(); return; }
        PROJECTION.clear();
        MODELVIEW.clear();
        GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, PROJECTION);
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, MODELVIEW);
        PROJECTION.get(PROJECTION_ARRAY);
        MODELVIEW.get(MODELVIEW_ARRAY);
        ParticleCore.FRUSTUM.set(PROJECTION_ARRAY, MODELVIEW_ARRAY);
    }

    /** EffectRenderer.renderParticles end: nothing is culled outside the particle pass. */
    public static void endFrame() { ParticleCore.FRUSTUM.clear(); ParticleCore.timeEnd(); }

    public static boolean inView(EntityFX particle, float partialTicks) {
        if (!ParticleCore.culling()) { if (ParticleCore.timing) ParticleCore.drawn++; return true; } // (counted for the QA numbers only)
        double x = particle.prevPosX + (particle.posX - particle.prevPosX) * partialTicks - EntityFX.interpPosX;
        double y = particle.prevPosY + (particle.posY - particle.prevPosY) * partialTicks - EntityFX.interpPosY;
        double z = particle.prevPosZ + (particle.posZ - particle.prevPosZ) * partialTicks - EntityFX.interpPosZ;
        double r = 0.5 + 0.15 * Math.abs(((com.thelads.core.v1_8_9.mixin.EntityFXAccessor) particle).ladsScale()); // the quad's half-diagonal, with room to spare
        if (ParticleCore.FRUSTUM.visible(x, y, z, r)) { ParticleCore.drawn++; return true; }
        ParticleCore.culled++;
        return false;
    }

    /** EffectRenderer.spawnEffectParticle: false drops the particle. */
    public static boolean allow(int id, double x, double y, double z) {
        if (!ParticleCore.active()) return true;
        Minecraft mc = Minecraft.getMinecraft();
        String name = NAMES.get(id);
        if (name == null) return true;
        if (mc.thePlayer == null || mc.theWorld == null) return ParticleCore.allowSpawn(name, false, 0, 0);
        boolean own = false;
        if (ParticleCore.isPotion(name)) {
            AxisAlignedBB box = mc.thePlayer.getEntityBoundingBox().expand(0.6, 0.6, 0.6);
            own = box.isVecInside(new Vec3(x, y, z));
        }
        return ParticleCore.allowSpawn(name, own, mc.thePlayer.getDistanceSq(x, y, z), mc.theWorld.getTotalWorldTime());
    }

    private static List<String> catalog() {
        List<String> ids = new ArrayList<String>();
        for (EnumParticleTypes type : EnumParticleTypes.values()) if (!ids.contains(name(type))) ids.add(name(type));
        return ids;
    }
}
