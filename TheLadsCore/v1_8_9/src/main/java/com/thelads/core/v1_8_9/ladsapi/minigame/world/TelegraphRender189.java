package com.thelads.core.v1_8_9.ladsapi.minigame.world;

import com.thelads.core.client.ladsapi.minigame.Minigame;
import com.thelads.core.client.ladsapi.minigame.TelegraphState;
import java.util.List;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.lwjgl.opengl.GL11;

/**
 * Ground decals: flat at y + 0.02, depth-tested, translucent (fill 35 %, outline 80 %), drawn in Forge's RenderWorldLastEvent as
 * one quad batch and one line batch. The fill grows outward as Telegraph.fillAt says. Nothing happens while the list is empty.
 */
final class TelegraphRender189 {
    private static final int SEGMENTS = 48;
    private static final float LIFT = 0.02f;
    private static double vx, vy, vz;

    private TelegraphRender189() {}

    /** GL state for translucent untextured world geometry; {@link #end3d} puts back what vanilla has at this point. */
    static void begin3d() {
        GlStateManager.disableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.disableCull();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.depthMask(false);
    }

    static void end3d() {
        GlStateManager.depthMask(true);
        GlStateManager.disableBlend();
        GlStateManager.enableCull();
        GlStateManager.enableTexture2D();
        GlStateManager.color(1f, 1f, 1f, 1f);
    }

    static void render(long now, double viewX, double viewY, double viewZ) {
        List<TelegraphState.Telegraph> list = Minigame.telegraphs.active(now);
        if (list.isEmpty()) return;
        vx = viewX;
        vy = viewY;
        vz = viewZ;
        begin3d();
        Tessellator tess = Tessellator.getInstance();
        WorldRenderer wr = tess.getWorldRenderer();
        GlStateManager.enablePolygonOffset();
        GlStateManager.doPolygonOffset(-2f, -2f);
        wr.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        for (TelegraphState.Telegraph t : list) fill(wr, t, now);
        tess.draw();
        GlStateManager.disablePolygonOffset();
        GL11.glLineWidth(3f);
        wr.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);
        for (TelegraphState.Telegraph t : list) outline(wr, t);
        tess.draw();
        GL11.glLineWidth(1f);
        end3d();
    }

    private static double dirX(double yaw) {
        return -Math.sin(Math.toRadians(yaw));
    }

    private static double dirZ(double yaw) {
        return Math.cos(Math.toRadians(yaw));
    }

    /** Angle in degrees on the x/z plane (0 = +x, toward +z) of a shape yaw. */
    private static double yawAngle(double yaw) {
        return Math.toDegrees(Math.atan2(dirZ(yaw), dirX(yaw)));
    }

    private static void fill(WorldRenderer wr, TelegraphState.Telegraph t, long now) {
        double f = t.fillAt(now);
        if (f <= 0) return;
        int c = t.color();
        float y = (float) (t.y() - vy) + LIFT, cx = (float) (t.x() - vx), cz = (float) (t.z() - vz);
        switch (t.shape()) {
            case CIRCLE: sector(wr, c, cx, y, cz, 0, t.radius() * f, 0, 360); break;
            case RING: sector(wr, c, cx, y, cz, t.inner(), t.inner() + (t.radius() - t.inner()) * f, 0, 360); break;
            case CONE: sector(wr, c, cx, y, cz, 0, t.radius() * f, yawAngle(t.yaw()) - t.angle() / 2, yawAngle(t.yaw()) + t.angle() / 2); break;
            case LINE: rect(wr, c, cx, y, cz, t, 0, t.length() * f, t.width()); break;
            default: rect(wr, c, cx, y, cz, t, -t.length() * f / 2, t.length() * f / 2, t.width() * f); break;
        }
    }

    private static void v(WorldRenderer wr, int rgb, double alpha, double x, double y, double z) {
        wr.pos(x, y, z).color(rgb >> 16 & 255, rgb >> 8 & 255, rgb & 255, (int) Math.round(alpha * 255)).endVertex();
    }

    private static void sector(WorldRenderer wr, int rgb, float cx, float y, float cz, double r0, double r1, double a0, double a1) {
        if (r1 <= r0) return;
        int steps = Math.max(1, (int) Math.ceil(SEGMENTS * Math.abs(a1 - a0) / 360));
        for (int i = 0; i < steps; i++) {
            double s = Math.toRadians(a0 + (a1 - a0) * i / steps), e = Math.toRadians(a0 + (a1 - a0) * (i + 1) / steps);
            double c0 = Math.cos(s), s0 = Math.sin(s), c1 = Math.cos(e), s1 = Math.sin(e);
            v(wr, rgb, TelegraphState.FILL_ALPHA, cx + c0 * r0, y, cz + s0 * r0);
            v(wr, rgb, TelegraphState.FILL_ALPHA, cx + c0 * r1, y, cz + s0 * r1);
            v(wr, rgb, TelegraphState.FILL_ALPHA, cx + c1 * r1, y, cz + s1 * r1);
            v(wr, rgb, TelegraphState.FILL_ALPHA, cx + c1 * r0, y, cz + s1 * r0);
        }
    }

    private static void rect(WorldRenderer wr, int rgb, float cx, float y, float cz, TelegraphState.Telegraph t, double along0, double along1, double width) {
        float dx = (float) dirX(t.yaw()), dz = (float) dirZ(t.yaw()), px = -dz, pz = dx, h = (float) width / 2;
        float ax = cx + dx * (float) along0, az = cz + dz * (float) along0, bx = cx + dx * (float) along1, bz = cz + dz * (float) along1;
        v(wr, rgb, TelegraphState.FILL_ALPHA, ax + px * h, y, az + pz * h);
        v(wr, rgb, TelegraphState.FILL_ALPHA, bx + px * h, y, bz + pz * h);
        v(wr, rgb, TelegraphState.FILL_ALPHA, bx - px * h, y, bz - pz * h);
        v(wr, rgb, TelegraphState.FILL_ALPHA, ax - px * h, y, az - pz * h);
    }

    private static void outline(WorldRenderer wr, TelegraphState.Telegraph t) {
        int c = t.color();
        float y = (float) (t.y() - vy) + LIFT, cx = (float) (t.x() - vx), cz = (float) (t.z() - vz);
        switch (t.shape()) {
            case CIRCLE: arc(wr, c, cx, y, cz, t.radius(), 0, 360); break;
            case RING:
                arc(wr, c, cx, y, cz, t.radius(), 0, 360);
                arc(wr, c, cx, y, cz, t.inner(), 0, 360);
                break;
            case CONE: {
                double a0 = yawAngle(t.yaw()) - t.angle() / 2, a1 = yawAngle(t.yaw()) + t.angle() / 2;
                arc(wr, c, cx, y, cz, t.radius(), a0, a1);
                if (t.angle() < 360) {
                    line(wr, c, cx, y, cz, cx + Math.cos(Math.toRadians(a0)) * t.radius(), cz + Math.sin(Math.toRadians(a0)) * t.radius());
                    line(wr, c, cx, y, cz, cx + Math.cos(Math.toRadians(a1)) * t.radius(), cz + Math.sin(Math.toRadians(a1)) * t.radius());
                }
                break;
            }
            case LINE: box(wr, c, cx, y, cz, t, 0, t.length()); break;
            default: box(wr, c, cx, y, cz, t, -t.length() / 2, t.length() / 2); break;
        }
    }

    private static void line(WorldRenderer wr, int rgb, float x0, float y, float z0, double x1, double z1) {
        v(wr, rgb, TelegraphState.OUTLINE_ALPHA, x0, y, z0);
        v(wr, rgb, TelegraphState.OUTLINE_ALPHA, x1, y, z1);
    }

    private static void arc(WorldRenderer wr, int rgb, float cx, float y, float cz, double r, double a0, double a1) {
        int steps = Math.max(1, (int) Math.ceil(SEGMENTS * Math.abs(a1 - a0) / 360));
        for (int i = 0; i < steps; i++) {
            double s = Math.toRadians(a0 + (a1 - a0) * i / steps), e = Math.toRadians(a0 + (a1 - a0) * (i + 1) / steps);
            v(wr, rgb, TelegraphState.OUTLINE_ALPHA, cx + Math.cos(s) * r, y, cz + Math.sin(s) * r);
            v(wr, rgb, TelegraphState.OUTLINE_ALPHA, cx + Math.cos(e) * r, y, cz + Math.sin(e) * r);
        }
    }

    private static void box(WorldRenderer wr, int rgb, float cx, float y, float cz, TelegraphState.Telegraph t, double along0, double along1) {
        float dx = (float) dirX(t.yaw()), dz = (float) dirZ(t.yaw()), px = -dz, pz = dx, h = (float) t.width() / 2;
        double ax = cx + dx * along0, az = cz + dz * along0, bx = cx + dx * along1, bz = cz + dz * along1;
        double[][] c = {{ax + px * h, az + pz * h}, {bx + px * h, bz + pz * h}, {bx - px * h, bz - pz * h}, {ax - px * h, az - pz * h}};
        for (int i = 0; i < 4; i++) {
            double[] a = c[i], b = c[(i + 1) % 4];
            v(wr, rgb, TelegraphState.OUTLINE_ALPHA, a[0], y, a[1]);
            v(wr, rgb, TelegraphState.OUTLINE_ALPHA, b[0], y, b[1]);
        }
    }
}
