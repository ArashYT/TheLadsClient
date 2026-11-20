package com.thelads.core.v1_20_1.ladsapi.minigame.world;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.thelads.core.client.ladsapi.minigame.Hooks;
import com.thelads.core.client.ladsapi.minigame.Minigame;
import com.thelads.core.client.ladsapi.minigame.WaypointState;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Waypoints: icon, label and distance are drawn as screen markers projected from the world position (a marker behind the view is
 * skipped); a marker without through_walls also needs a clear line of sight. The optional light column is world geometry.
 */
public final class WaypointRender1201 {
    private static final Map<String, ItemStack> STACKS = new HashMap<>();
    private static final float BEAM_HEIGHT = 96, BEAM_HALF = 0.12f;
    private static final int FULL_BRIGHT = 0xF000F0;

    private WaypointRender1201() {}

    private static String glyph(String name) {
        return switch (name) {
            case "flag" -> "\u2691";
            case "bed" -> "\u2302";
            case "skull" -> "\u2620";
            case "star" -> "\u2605";
            case "arrow" -> "\u25BC";
            case "shield" -> "\u26E8";
            case "sword" -> "\u2694";
            case "question" -> "?";
            case "exclamation" -> "!";
            case "circle" -> "\u25CF";
            default -> "\u25C6";
        };
    }

    private static ItemStack stack(String id) {
        return STACKS.computeIfAbsent(id, key -> {
            ResourceLocation parsed = ResourceLocation.tryParse(key.contains(":") ? key : "minecraft:" + key);
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
        Vec3 cp = cam.getPosition();
        Vector3f look = cam.getLookVector(), up = cam.getUpVector(), left = cam.getLeftVector();
        double tanHalf = Math.tan(Math.toRadians(CameraRender1201.fov) / 2), aspect = (double) mc.getWindow().getWidth() / mc.getWindow().getHeight();
        int gw = g.guiWidth(), gh = g.guiHeight();
        for (WaypointState.Waypoint w : all) {
            double[] p = where(w);
            if (p == null) continue;
            double dx = p[0] - cp.x, dy = p[1] - cp.y, dz = p[2] - cp.z;
            double depth = dx * look.x + dy * look.y + dz * look.z;
            if (depth < 0.05) continue; // behind the view
            double right = -(dx * left.x + dy * left.y + dz * left.z), above = dx * up.x + dy * up.y + dz * up.z;
            float sx = (float) (right / (depth * tanHalf * aspect) * 0.5 + 0.5) * gw, sy = (float) (0.5 - above / (depth * tanHalf) * 0.5) * gh;
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
            g.pose().pushPose();
            g.pose().translate(sx, sy, 0);
            g.pose().scale(2f, 2f, 1f);
            g.drawString(mc.font, s, -mc.font.width(s) / 2, -4, color, true);
            g.pose().popPose();
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

    static void drawBeams(WorldRenderContext context, long now) {
        List<WaypointState.Waypoint> all = Minigame.waypoints.all();
        if (all.isEmpty() || context.consumers() == null) return;
        boolean any = false;
        for (WaypointState.Waypoint w : all) any |= w.beam();
        if (!any) return;
        Vec3 cp = context.camera().getPosition();
        PoseStack.Pose pose = context.matrixStack().last();
        VertexConsumer buffer = context.consumers().getBuffer(RenderType.beaconBeam(BeaconRenderer.BEAM_LOCATION, true));
        for (WaypointState.Waypoint w : all) {
            if (!w.beam()) continue;
            double[] p = WaypointState.position(w, Hooks.entities);
            if (p == null) continue;
            float x = (float) (p[0] - cp.x), y = (float) (p[1] - cp.y), z = (float) (p[2] - cp.z), top = y + BEAM_HEIGHT, s = BEAM_HALF;
            int rgb = w.color();
            for (int side = 0; side < 2; side++) {
                face(buffer, pose, x - s, z - s, x + s, z - s, y, top, rgb, side);
                face(buffer, pose, x + s, z - s, x + s, z + s, y, top, rgb, side);
                face(buffer, pose, x + s, z + s, x - s, z + s, y, top, rgb, side);
                face(buffer, pose, x - s, z + s, x - s, z - s, y, top, rgb, side);
            }
        }
    }

    private static void face(VertexConsumer buffer, PoseStack.Pose pose, float x0, float z0, float x1, float z1, float y0, float y1, int rgb, int side) {
        if (side == 1) { float t = x0; x0 = x1; x1 = t; t = z0; z0 = z1; z1 = t; }
        vertex(buffer, pose, x0, y0, z0, 0xC0, rgb, 0, 0);
        vertex(buffer, pose, x1, y0, z1, 0xC0, rgb, 1, 0);
        vertex(buffer, pose, x1, y1, z1, 0xFF, rgb, 1, 1);
        vertex(buffer, pose, x0, y1, z0, 0xFF, rgb, 0, 1);
    }

    private static void vertex(VertexConsumer buffer, PoseStack.Pose pose, float x, float y, float z, int alpha, int rgb, float u, float v) {
        Matrix4f m = pose.pose();
        buffer.vertex(m, x, y, z).color(rgb >> 16 & 255, rgb >> 8 & 255, rgb & 255, alpha).uv(u, v).uv2(FULL_BRIGHT).normal(pose.normal(), 0, 1, 0).endVertex();
    }
}