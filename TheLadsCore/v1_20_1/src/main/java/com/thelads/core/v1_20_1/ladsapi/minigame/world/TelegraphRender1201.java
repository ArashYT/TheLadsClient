package com.thelads.core.v1_20_1.ladsapi.minigame.world;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.thelads.core.client.ladsapi.minigame.Minigame;
import com.thelads.core.client.ladsapi.minigame.TelegraphState;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Ground decals: flat at y + 0.02, depth-tested, translucent (fill 35 %, outline 80 %), one quad batch and one line batch for all of
 * them. The fill grows outward as Telegraph.fillAt says. Nothing is drawn while the list is empty.
 */
public final class TelegraphRender1201 {
    private static final int SEGMENTS = 48;
    private static final float LIFT = 0.02f;
    private static double camX, camY, camZ;

    private TelegraphRender1201() {}

    static void draw(WorldRenderContext context, long now) {
        List<TelegraphState.Telegraph> list = Minigame.telegraphs.active(now);
        if (list.isEmpty() || context.consumers() == null) return;
        Vec3 cp = context.camera().getPosition();
        camX = cp.x; camY = cp.y; camZ = cp.z;
        PoseStack.Pose pose = context.matrixStack().last();
        fills(pose, context.consumers().getBuffer(RenderType.debugQuads()), list, now);
        outlines(pose, context.consumers().getBuffer(RenderType.lines()), list);
    }

    private static int fillColor(TelegraphState.Telegraph t) {
        return 0x59 << 24 | t.color() & 0xFFFFFF; // 35 %
    }

    private static int lineColor(TelegraphState.Telegraph t) {
        return 0xCC << 24 | t.color() & 0xFFFFFF; // 80 %
    }

    /** Unit direction of the shape's yaw in world x/z: yaw 0 looks toward +Z, 90 toward -X. */
    private static double dirX(double yaw) {
        return -Math.sin(Math.toRadians(yaw));
    }

    private static double dirZ(double yaw) {
        return Math.cos(Math.toRadians(yaw));
    }

    private static void fills(PoseStack.Pose pose, VertexConsumer quads, List<TelegraphState.Telegraph> list, long now) {
        for (TelegraphState.Telegraph t : list) {
            double f = t.fillAt(now);
            if (f <= 0) continue;
            int color = fillColor(t);
            float y = (float) (t.y() - camY) + LIFT;
            float cx = (float) (t.x() - camX), cz = (float) (t.z() - camZ);
            switch (t.shape()) {
                case CIRCLE -> sector(pose, quads, cx, y, cz, 0, t.radius() * f, 0, 360, color);
                case RING -> sector(pose, quads, cx, y, cz, t.inner(), t.inner() + (t.radius() - t.inner()) * f, 0, 360, color);
                case CONE -> sector(pose, quads, cx, y, cz, 0, t.radius() * f, yawAngle(t.yaw()) - t.angle() / 2, yawAngle(t.yaw()) + t.angle() / 2, color);
                case LINE -> rect(pose, quads, cx, y, cz, t, 0, t.length() * f, t.width(), color);
                case RECT -> rect(pose, quads, cx, y, cz, t, -t.length() * f / 2, t.length() * f / 2, t.width() * f, color);
            }
        }
    }

    /** Angle in degrees on the x/z plane (0 = +x, counter-clockwise toward +z) of a shape yaw. */
    private static double yawAngle(double yaw) {
        return Math.toDegrees(Math.atan2(dirZ(yaw), dirX(yaw)));
    }

    /** Annular sector from radius r0 to r1 between two plane angles (degrees). */
    private static void sector(PoseStack.Pose pose, VertexConsumer q, float cx, float y, float cz, double r0, double r1, double a0, double a1, int color) {
        if (r1 <= r0) return;
        int steps = Math.max(1, (int) Math.ceil(SEGMENTS * Math.abs(a1 - a0) / 360));
        for (int i = 0; i < steps; i++) {
            double s = Math.toRadians(a0 + (a1 - a0) * i / steps), e = Math.toRadians(a0 + (a1 - a0) * (i + 1) / steps);
            double c0 = Math.cos(s), s0 = Math.sin(s), c1 = Math.cos(e), s1 = Math.sin(e);
            quad(pose, q, color,
                cx + (float) (c0 * r0), y, cz + (float) (s0 * r0), cx + (float) (c0 * r1), y, cz + (float) (s0 * r1),
                cx + (float) (c1 * r1), y, cz + (float) (s1 * r1), cx + (float) (c1 * r0), y, cz + (float) (s1 * r0));
        }
    }

    /** Rectangle along the shape's yaw from along0 to along1 and {@code width} across, centred on the axis. */
    private static void rect(PoseStack.Pose pose, VertexConsumer q, float cx, float y, float cz, TelegraphState.Telegraph t, double along0, double along1,
                             double width, int color) {
        float dx = (float) dirX(t.yaw()), dz = (float) dirZ(t.yaw()), px = -dz, pz = dx, h = (float) width / 2;
        float ax = cx + dx * (float) along0, az = cz + dz * (float) along0, bx = cx + dx * (float) along1, bz = cz + dz * (float) along1;
        quad(pose, q, color, ax + px * h, y, az + pz * h, bx + px * h, y, bz + pz * h, bx - px * h, y, bz - pz * h, ax - px * h, y, az - pz * h);
    }

    /** One quad (the debug-quads type does not cull: it shows from above whichever way it is wound). */
    private static void quad(PoseStack.Pose pose, VertexConsumer q, int color, float ax, float ay, float az, float bx, float by, float bz, float cx,
                             float cy, float cz, float dx, float dy, float dz) {
        Matrix4f m = pose.pose();
        int r = color >> 16 & 255, g = color >> 8 & 255, b = color & 255, a = color >>> 24;
        q.vertex(m, ax, ay, az).color(r, g, b, a).endVertex();
        q.vertex(m, bx, by, bz).color(r, g, b, a).endVertex();
        q.vertex(m, cx, cy, cz).color(r, g, b, a).endVertex();
        q.vertex(m, dx, dy, dz).color(r, g, b, a).endVertex();
    }

    private static void outlines(PoseStack.Pose pose, VertexConsumer lines, List<TelegraphState.Telegraph> list) {
        for (TelegraphState.Telegraph t : list) {
            int color = lineColor(t);
            float y = (float) (t.y() - camY) + LIFT;
            float cx = (float) (t.x() - camX), cz = (float) (t.z() - camZ);
            switch (t.shape()) {
                case CIRCLE -> arc(pose, lines, cx, y, cz, t.radius(), 0, 360, color);
                case RING -> {
                    arc(pose, lines, cx, y, cz, t.radius(), 0, 360, color);
                    arc(pose, lines, cx, y, cz, t.inner(), 0, 360, color);
                }
                case CONE -> {
                    double a0 = yawAngle(t.yaw()) - t.angle() / 2, a1 = yawAngle(t.yaw()) + t.angle() / 2;
                    arc(pose, lines, cx, y, cz, t.radius(), a0, a1, color);
                    if (t.angle() < 360) {
                        line(pose, lines, cx, y, cz, cx + (float) (Math.cos(Math.toRadians(a0)) * t.radius()), y, cz + (float) (Math.sin(Math.toRadians(a0)) * t.radius()), color);
                        line(pose, lines, cx, y, cz, cx + (float) (Math.cos(Math.toRadians(a1)) * t.radius()), y, cz + (float) (Math.sin(Math.toRadians(a1)) * t.radius()), color);
                    }
                }
                case LINE -> box(pose, lines, cx, y, cz, t, 0, t.length(), t.width(), color);
                case RECT -> box(pose, lines, cx, y, cz, t, -t.length() / 2, t.length() / 2, t.width(), color);
            }
        }
    }

    private static void arc(PoseStack.Pose pose, VertexConsumer lines, float cx, float y, float cz, double r, double a0, double a1, int color) {
        int steps = Math.max(1, (int) Math.ceil(SEGMENTS * Math.abs(a1 - a0) / 360));
        for (int i = 0; i < steps; i++) {
            double s = Math.toRadians(a0 + (a1 - a0) * i / steps), e = Math.toRadians(a0 + (a1 - a0) * (i + 1) / steps);
            line(pose, lines, cx + (float) (Math.cos(s) * r), y, cz + (float) (Math.sin(s) * r), cx + (float) (Math.cos(e) * r), y, cz + (float) (Math.sin(e) * r), color);
        }
    }

    private static void box(PoseStack.Pose pose, VertexConsumer lines, float cx, float y, float cz, TelegraphState.Telegraph t, double along0, double along1,
                            double width, int color) {
        float dx = (float) dirX(t.yaw()), dz = (float) dirZ(t.yaw()), px = -dz, pz = dx, h = (float) width / 2;
        float ax = cx + dx * (float) along0, az = cz + dz * (float) along0, bx = cx + dx * (float) along1, bz = cz + dz * (float) along1;
        line(pose, lines, ax + px * h, y, az + pz * h, bx + px * h, y, bz + pz * h, color);
        line(pose, lines, bx + px * h, y, bz + pz * h, bx - px * h, y, bz - pz * h, color);
        line(pose, lines, bx - px * h, y, bz - pz * h, ax - px * h, y, az - pz * h, color);
        line(pose, lines, ax - px * h, y, az - pz * h, ax + px * h, y, az + pz * h, color);
    }

    private static void line(PoseStack.Pose pose, VertexConsumer lines, float x0, float y0, float z0, float x1, float y1, float z1, int color) {
        float dx = x1 - x0, dy = y1 - y0, dz = z1 - z0, len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1e-6f) return;
        dx /= len; dy /= len; dz /= len;
        Matrix4f m = pose.pose();
        int r = color >> 16 & 255, g = color >> 8 & 255, b = color & 255, a = color >>> 24;
        lines.vertex(m, x0, y0, z0).color(r, g, b, a).normal(pose.normal(), dx, dy, dz).endVertex();
        lines.vertex(m, x1, y1, z1).color(r, g, b, a).normal(pose.normal(), dx, dy, dz).endVertex();
    }
}