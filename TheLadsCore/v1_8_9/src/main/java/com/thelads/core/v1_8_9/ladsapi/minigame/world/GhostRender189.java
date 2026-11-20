package com.thelads.core.v1_8_9.ladsapi.minigame.world;

import com.mojang.authlib.GameProfile;
import com.thelads.core.client.ladsapi.minigame.GhostState;
import com.thelads.core.client.ladsapi.minigame.Minigame;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.world.World;
import org.lwjgl.opengl.GL11;

/**
 * Ghosts: a dummy player that is never added to the world (no collision, no packets, no tick, seen by nobody else) is posed from the
 * recorded frame and drawn by the normal player renderer, which MgRenderLivingMixin189 makes half translucent and nameless
 * while {@link #rendering} is set. The skin is whatever the client already has for the
 * uuid (a connected player's), else the default one; nothing is fetched. Drawn in RenderWorldLastEvent.
 */
public final class GhostRender189 {
    /** True only while a ghost is being drawn; MgRenderLivingMixin189 reads it. */
    public static boolean rendering;

    private static final Map<String, Dummy> DUMMIES = new HashMap<>();
    private static World dummyWorld;

    private static final class Dummy {
        EntityOtherPlayerMP player;
        double lastX = Double.NaN, lastZ = Double.NaN;
    }

    private GhostRender189() {}

    static void clear() {
        DUMMIES.clear();
    }

    private static Dummy dummy(Minecraft mc, GhostState.Pose p) {
        if (dummyWorld != mc.theWorld) {
            DUMMIES.clear();
            dummyWorld = mc.theWorld;
        }
        Dummy d = DUMMIES.get(p.id());
        if (d == null) {
            d = new Dummy();
            UUID id = p.skin() != null ? p.skin() : new UUID(0x9057L, p.id().hashCode());
            d.player = new EntityOtherPlayerMP(mc.theWorld, new GameProfile(id, "Ghost"));
            DUMMIES.put(p.id(), d);
        }
        return d;
    }

    /** Client tick: steps each ghost's walk animation once per game tick from how far it moved. */
    static void tick(Minecraft mc, long now) {
        if (mc.theWorld == null || Minigame.ghosts.armedCount() == 0) {
            if (!DUMMIES.isEmpty()) DUMMIES.clear();
            return;
        }
        for (GhostState.Pose p : Minigame.ghosts.visible(Minigame.run, now)) {
            Dummy d = dummy(mc, p);
            EntityOtherPlayerMP e = d.player;
            double moved = Double.isNaN(d.lastX) ? 0 : Math.hypot(p.x() - d.lastX, p.z() - d.lastZ);
            e.prevLimbSwingAmount = e.limbSwingAmount;
            e.limbSwingAmount += ((float) Math.min(1.0, moved * 4) - e.limbSwingAmount) * 0.4f;
            e.limbSwing += e.limbSwingAmount;
            d.lastX = p.x();
            d.lastZ = p.z();
        }
    }

    static void render(Minecraft mc, long now, float partial, double vx, double vy, double vz) {
        if (mc.theWorld == null || Minigame.ghosts.armedCount() == 0) return;
        List<GhostState.Pose> poses = Minigame.ghosts.visible(Minigame.run, now);
        if (poses.isEmpty()) return;
        RenderManager rm = mc.getRenderManager();
        mc.entityRenderer.enableLightmap();
        RenderHelper.enableStandardItemLighting();
        rendering = true;
        try {
            for (GhostState.Pose p : poses) {
                EntityOtherPlayerMP e = dummy(mc, p).player;
                e.posX = e.prevPosX = e.lastTickPosX = p.x();
                e.posY = e.prevPosY = e.lastTickPosY = p.y();
                e.posZ = e.prevPosZ = e.lastTickPosZ = p.z();
                e.rotationYaw = e.prevRotationYaw = (float) p.yaw();
                e.rotationPitch = e.prevRotationPitch = (float) p.pitch();
                e.rotationYawHead = e.prevRotationYawHead = (float) p.yaw();
                e.renderYawOffset = e.prevRenderYawOffset = (float) p.yaw();
                rm.renderEntityStatic(e, partial, false);
            }
        } finally {
            rendering = false;
            RenderHelper.disableStandardItemLighting();
            mc.entityRenderer.disableLightmap();
        }
        for (GhostState.Pose p : poses) {
            if (p.name() == null || p.name().isEmpty()) continue;
            label(mc, rm, p.name(), p.x() - vx, p.y() - vy + 2.3, p.z() - vz);
        }
    }

    /** The name above a ghost: a camera-facing label like vanilla's name tag, without its background. */
    private static void label(Minecraft mc, RenderManager rm, String name, double x, double y, double z) {
        net.minecraft.client.gui.FontRenderer font = mc.fontRendererObj;
        GlStateManager.pushMatrix();
        GlStateManager.translate((float) x, (float) y, (float) z);
        GL11.glNormal3f(0f, 1f, 0f);
        GlStateManager.rotate(-rm.playerViewY, 0f, 1f, 0f);
        GlStateManager.rotate(rm.playerViewX, 1f, 0f, 0f);
        GlStateManager.scale(-0.025f, -0.025f, 0.025f);
        GlStateManager.disableLighting();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        font.drawStringWithShadow(name, -font.getStringWidth(name) / 2f, 0, 0xFFFFFFFF);
        GlStateManager.disableBlend();
        GlStateManager.color(1f, 1f, 1f, 1f);
        GlStateManager.popMatrix();
    }
}
