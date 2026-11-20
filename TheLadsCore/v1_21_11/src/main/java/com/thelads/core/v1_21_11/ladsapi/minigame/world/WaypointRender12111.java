package com.thelads.core.v1_21_11.ladsapi.minigame.world;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.thelads.core.client.ladsapi.minigame.Hooks;
import com.thelads.core.client.ladsapi.minigame.Minigame;
import com.thelads.core.client.ladsapi.minigame.WaypointState;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Waypoints: icon, label and distance are drawn as screen markers projected from the world position (a marker behind the view is
 * skipped); a marker without through_walls also needs a clear line of sight. The optional light column is world geometry.
 */
public final class WaypointRender12111 {
    private static final Vector3f VEC = new Vector3f();
    private static final Map<String, ItemStack> STACKS = new HashMap<>();
    private static final float BEAM_HEIGHT = 96, BEAM_HALF = 0.12f;
    private static final int FULL_BRIGHT = 0xF000F0;
    private static double camX, camY, camZ;

    private WaypointRender12111() {}

    private static String glyph(String name) {
        return switch (name) {
            case "flag" -> "⚑";
            case "bed" -> "⌂";
            case "skull" -> "☠";
            case "star" -> "★";
            case "arrow" -> "▼";
            case "shield" -> "⛨";
            case "sword" -> "⚔";
            case "question" -> "?";
            case "exclamation" -> "!";
            case "circle" -> "●";
            default -> "◆";
        };
    }

    private static ItemStack stack(String id) {
        return STACKS.computeIfAbsent(id, key -> {
            Identifier parsed = Identifier.tryParse(key.contains(":") ? key : "minecraft:" + key);
            if (parsed == null) return ItemStack.EMPTY;
            Item item = BuiltInRegistries.ITEM.getOptional(parsed).orElse(null);
            return item == null ? ItemStack.EMPTY : new ItemStack(item);
        });
    }

    /** Where the marker stands; entity markers are lifted above the head. */
    private static double[] where(WaypointState.Waypoint w) {
        double[] p = WaypointState.position(w, Hooks.entities);
        if (p != null) p[1] += w.entity() != null ? 2.4 : 1.0; // a ground point would fail its own line-of-sight test
        return p;
    }

    static void drawMarkers(GuiGraphics g, long now) {
        List<WaypointState.Waypoint> all = Minigame.waypoints.all();
        if (all.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        Camera cam = mc.gameRenderer.getMainCamera();
        Vec3 cp = cam.position();
        int gw = g.guiWidth(), gh = g.guiHeight();
        for (WaypointState.Waypoint w : all) {
            double[] p = where(w);
            if (p == null) continue;
            double dx = p[0] - cp.x, dy = p[1] - cp.y, dz = p[2] - cp.z;
            Vec3 ndc = mc.gameRenderer.projectPointToScreen(new Vec3(p[0], p[1], p[2])); // behind the view or past the far plane: depth outside [-1, 1]
            if (ndc.z > 1 || ndc.z < -1) continue;
            float sx = (float) (ndc.x * 0.5 + 0.5) * gw, sy = (float) (0.5 - ndc.y * 0.5) * gh;
            if (sx < -40 || sx > gw + 40 || sy < -40 || sy > gh + 40) continue;
            if (!WaypointState.throughWalls(w) && blocked(mc, cp, new Vec3(p[0], p[1], p[2]))) continue;
            draw(g, mc, w, sx, sy, Math.sqrt(dx * dx + dy * dy + dz * dz));
        }
    }

    private static boolean blocked(Minecraft mc, Vec3 from, Vec3 to) {
        return mc.level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player)).getType() != HitResult.Type.MISS;
    }

    private static void draw(GuiGraphics g, Minecraft mc, WaypointState.Waypoint w, float sx, float sy, double distance) {
        int color = 0xFF000000 | w.color();
        int x = Math.round(sx), y = Math.round(sy);
        if (w.glyph()) {
            String s = glyph(w.icon());
            g.pose().pushMatrix();
            g.pose().translate(sx, sy);
            g.pose().scale(2f, 2f);
            g.drawString(mc.font, s, -mc.font.width(s) / 2, -4, color, true);
            g.pose().popMatrix();
        } else {
            ItemStack item = stack(w.icon());
            if (!item.isEmpty()) g.renderItem(item, x - 8, y - 8);
        }
        String label = w.label();
        if (label != null && !label.isEmpty()) g.drawString(mc.font, label, x - mc.font.width(label) / 2, y - 22, color, true);
        if (w.distance()) {
            String d = Math.round(distance) + " m";
            g.drawString(mc.font, d, x - mc.font.width(d) / 2, y + 12, 0xFFFFFFFF, true);
        }
    }

    static void submitBeams(WorldRenderContext context, long now) {
        List<WaypointState.Waypoint> all = Minigame.waypoints.all();
        if (all.isEmpty()) return;
        boolean any = false;
        for (WaypointState.Waypoint w : all) any |= w.beam();
        if (!any) return;
        Vec3 cp = context.worldState().cameraRenderState.pos;
        camX = cp.x; camY = cp.y; camZ = cp.z;
        context.commandQueue().submitCustomGeometry(context.matrices(), RenderTypes.beaconBeam(BeaconRenderer.BEAM_LOCATION, true), WaypointRender12111::drawBeams);
    }

    private static void drawBeams(PoseStack.Pose pose, VertexConsumer buffer) {
        pose.transformNormal(0, 1, 0, VEC);
        float nx = VEC.x, ny = VEC.y, nz = VEC.z;
        for (WaypointState.Waypoint w : Minigame.waypoints.all()) {
            if (!w.beam()) continue;
            double[] p = WaypointState.position(w, Hooks.entities);
            if (p == null) continue;
            float x = (float) (p[0] - camX), y = (float) (p[1] - camY), z = (float) (p[2] - camZ), top = y + BEAM_HEIGHT, s = BEAM_HALF;
            int rgb = w.color();
            for (int side = 0; side < 2; side++) {
                face(buffer, pose, x - s, z - s, x + s, z - s, y, top, rgb, side, nx, ny, nz);
                face(buffer, pose, x + s, z - s, x + s, z + s, y, top, rgb, side, nx, ny, nz);
                face(buffer, pose, x + s, z + s, x - s, z + s, y, top, rgb, side, nx, ny, nz);
                face(buffer, pose, x - s, z + s, x - s, z - s, y, top, rgb, side, nx, ny, nz);
            }
        }
    }

    private static void face(VertexConsumer buffer, PoseStack.Pose pose, float x0, float z0, float x1, float z1, float y0, float y1, int rgb, int side,
                             float nx, float ny, float nz) {
        if (side == 1) { float t = x0; x0 = x1; x1 = t; t = z0; z0 = z1; z1 = t; }
        int bottom = 0xC0 << 24 | rgb, top = rgb;
        vertex(buffer, pose, x0, y0, z0, bottom, 0, 0, nx, ny, nz);
        vertex(buffer, pose, x1, y0, z1, bottom, 1, 0, nx, ny, nz);
        vertex(buffer, pose, x1, y1, z1, top, 1, 1, nx, ny, nz);
        vertex(buffer, pose, x0, y1, z0, top, 0, 1, nx, ny, nz);
    }

    private static void vertex(VertexConsumer buffer, PoseStack.Pose pose, float x, float y, float z, int color, float u, float v, float nx, float ny, float nz) {
        pose.pose().transformPosition(x, y, z, VEC);
        buffer.addVertex(VEC.x, VEC.y, VEC.z).setColor(color).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULL_BRIGHT).setNormal(nx, ny, nz);
    }
}
