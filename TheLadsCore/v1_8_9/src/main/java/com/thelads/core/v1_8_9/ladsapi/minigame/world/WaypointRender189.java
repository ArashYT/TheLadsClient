package com.thelads.core.v1_8_9.ladsapi.minigame.world;

import com.thelads.core.client.ladsapi.minigame.Hooks;
import com.thelads.core.client.ladsapi.minigame.Minigame;
import com.thelads.core.client.ladsapi.minigame.WaypointState;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Vec3;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

/**
 * Waypoints: icon, label and distance are screen markers projected from the world position (one behind the view is skipped); a
 * marker without through_walls also needs a clear line of sight. Projection happens in RenderWorldLastEvent, where the camera
 * matrices (the shot's too) are the ones the world was drawn with; the markers are drawn with the HUD. The light column is world
 * geometry, depth-tested like the telegraphs.
 */
final class WaypointRender189 {
    private static final float BEAM_HEIGHT = 96, BEAM_HALF = 0.12f;
    private static final FloatBuffer MODELVIEW = BufferUtils.createFloatBuffer(16), PROJECTION = BufferUtils.createFloatBuffer(16);
    private static final float[] MV = new float[16], PR = new float[16];
    private static final Map<String, ItemStack> STACKS = new HashMap<>();
    private static final List<Marker> MARKERS = new ArrayList<>();

    private static final class Marker {
        final WaypointState.Waypoint w;
        final float x, y;
        final double distance;

        Marker(WaypointState.Waypoint w, float x, float y, double distance) {
            this.w = w;
            this.x = x;
            this.y = y;
            this.distance = distance;
        }
    }

    private WaypointRender189() {}

    private static String glyph(String name) {
        switch (name) {
            case "flag": return "⚑";
            case "bed": return "⌂";
            case "skull": return "☠";
            case "star": return "★";
            case "arrow": return "▼";
            case "shield": return "⛨";
            case "sword": return "⚔";
            case "question": return "?";
            case "exclamation": return "!";
            case "circle": return "●";
            default: return "◆";
        }
    }

    private static ItemStack stack(String id) {
        return STACKS.computeIfAbsent(id, key -> {
            Item item = (Item) Item.itemRegistry.getObject(new ResourceLocation(key));
            return item == null ? null : new ItemStack(item);
        });
    }

    /** Where the marker stands; entity markers are lifted above the head. */
    private static double[] where(WaypointState.Waypoint w) {
        double[] p = WaypointState.position(w, Hooks.entities);
        if (p != null) p[1] += w.entity() != null ? 2.4 : 1.0; // a ground point would fail its own line-of-sight test
        return p;
    }

    static void clear() {
        MARKERS.clear();
    }

    /** RenderWorldLastEvent: the markers' screen positions for this frame, then the beams. */
    static void world(Minecraft mc, long now, double vx, double vy, double vz) {
        MARKERS.clear();
        List<WaypointState.Waypoint> all = Minigame.waypoints.all();
        if (all.isEmpty() || mc.theWorld == null) return;
        Entity view = mc.getRenderViewEntity();
        if (view == null) return;
        MODELVIEW.clear();
        PROJECTION.clear();
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, MODELVIEW);
        GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, PROJECTION);
        MODELVIEW.get(MV);
        PROJECTION.get(PR);
        ScaledResolution sr = new ScaledResolution(mc);
        Vec3 eye = new Vec3(vx, vy + view.getEyeHeight(), vz);
        boolean beams = false;
        for (WaypointState.Waypoint w : all) {
            beams |= w.beam();
            double[] p = where(w);
            if (p == null) continue;
            double rx = p[0] - vx, ry = p[1] - vy, rz = p[2] - vz;
            double ex = MV[0] * rx + MV[4] * ry + MV[8] * rz + MV[12], ey = MV[1] * rx + MV[5] * ry + MV[9] * rz + MV[13],
                ez = MV[2] * rx + MV[6] * ry + MV[10] * rz + MV[14], ew = MV[3] * rx + MV[7] * ry + MV[11] * rz + MV[15];
            double cx = PR[0] * ex + PR[4] * ey + PR[8] * ez + PR[12] * ew, cy = PR[1] * ex + PR[5] * ey + PR[9] * ez + PR[13] * ew,
                cz = PR[2] * ex + PR[6] * ey + PR[10] * ez + PR[14] * ew, cw = PR[3] * ex + PR[7] * ey + PR[11] * ez + PR[15] * ew;
            if (cw <= 0.05 || cz > cw) continue; // behind the view or past the far plane
            float sx = (float) (cx / cw * 0.5 + 0.5) * sr.getScaledWidth(), sy = (float) (0.5 - cy / cw * 0.5) * sr.getScaledHeight();
            if (sx < -40 || sx > sr.getScaledWidth() + 40 || sy < -40 || sy > sr.getScaledHeight() + 40) continue;
            if (!WaypointState.throughWalls(w) && mc.theWorld.rayTraceBlocks(eye, new Vec3(p[0], p[1], p[2]), false, true, false) != null) continue;
            MARKERS.add(new Marker(w, sx, sy, Math.sqrt(rx * rx + ry * ry + rz * rz)));
        }
        if (beams) beams(vx, vy, vz);
    }

    private static void beams(double vx, double vy, double vz) {
        TelegraphRender189.begin3d();
        Tessellator tess = Tessellator.getInstance();
        WorldRenderer wr = tess.getWorldRenderer();
        wr.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        for (WaypointState.Waypoint w : Minigame.waypoints.all()) {
            if (!w.beam()) continue;
            double[] p = WaypointState.position(w, Hooks.entities);
            if (p == null) continue;
            float x = (float) (p[0] - vx), y = (float) (p[1] - vy), z = (float) (p[2] - vz), top = y + BEAM_HEIGHT, s = BEAM_HALF;
            int r = w.color() >> 16 & 255, g = w.color() >> 8 & 255, b = w.color() & 255;
            face(wr, x - s, z - s, x + s, z - s, y, top, r, g, b);
            face(wr, x + s, z - s, x + s, z + s, y, top, r, g, b);
            face(wr, x + s, z + s, x - s, z + s, y, top, r, g, b);
            face(wr, x - s, z + s, x - s, z - s, y, top, r, g, b);
        }
        tess.draw();
        TelegraphRender189.end3d();
    }

    private static void face(WorldRenderer wr, float x0, float z0, float x1, float z1, float y0, float y1, int r, int g, int b) {
        wr.pos(x0, y0, z0).color(r, g, b, 192).endVertex();
        wr.pos(x1, y0, z1).color(r, g, b, 192).endVertex();
        wr.pos(x1, y1, z1).color(r, g, b, 0).endVertex();
        wr.pos(x0, y1, z0).color(r, g, b, 0).endVertex();
    }

    /** With the HUD: icons, labels and distances. */
    static void draw(Minecraft mc) {
        if (MARKERS.isEmpty()) return;
        FontRenderer font = mc.fontRendererObj;
        for (Marker m : MARKERS) {
            WaypointState.Waypoint w = m.w;
            int color = 0xFF000000 | w.color();
            int x = Math.round(m.x), y = Math.round(m.y);
            if (w.glyph()) {
                String s = glyph(w.icon());
                GlStateManager.pushMatrix();
                GlStateManager.translate(m.x, m.y, 0);
                GlStateManager.scale(2f, 2f, 1f);
                font.drawStringWithShadow(s, -font.getStringWidth(s) / 2f, -4, color);
                GlStateManager.popMatrix();
            } else {
                ItemStack item = stack(w.icon());
                if (item != null) {
                    RenderHelper.enableGUIStandardItemLighting();
                    mc.getRenderItem().renderItemIntoGUI(item, x - 8, y - 8);
                    RenderHelper.disableStandardItemLighting();
                    GlStateManager.disableLighting();
                }
            }
            String label = w.label();
            if (label != null && !label.isEmpty()) font.drawStringWithShadow(label, x - font.getStringWidth(label) / 2f, y - 22, color);
            if (w.distance()) {
                String d = Math.round(m.distance) + " m";
                font.drawStringWithShadow(d, x - font.getStringWidth(d) / 2f, y + 12, 0xFFFFFFFF);
            }
        }
        GlStateManager.color(1f, 1f, 1f, 1f);
    }
}
