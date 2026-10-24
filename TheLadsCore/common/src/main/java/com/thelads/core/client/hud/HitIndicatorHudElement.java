package com.thelads.core.client.hud;

import com.thelads.core.client.DamageTilt;
import com.thelads.core.client.bridge.LadsGraphics;

public class HitIndicatorHudElement extends HudElement {
    public HitIndicatorHudElement() {
        this.x = 0;
        this.y = 0;
        this.width = 100;
        this.height = 100;
    }

    /**
     * Converts Minecraft/DamageTilt relative yaw (where 0 deg is from the left, 90 deg is front,
     * 180 deg is right, 270 deg is behind) to a screen clock angle (where 0 deg is 12 o'clock / front,
     * 90 deg is 3 o'clock / right, 180 deg is 6 o'clock / behind, 270 deg is 9 o'clock / left).
     */
    public static double calculateIndicatorAngle(float dirYaw) {
        return dirYaw - 90.0;
    }

    @Override
    public void prepareRender(LadsGraphics g, boolean editor) {
        width = 100;
        height = 100;
    }

    @Override
    public void render(LadsGraphics g) {
        renderArc(g, false);
    }

    @Override
    public void renderEditor(LadsGraphics g) {
        renderArc(g, true);
    }

    private void renderArc(LadsGraphics g, boolean editor) {
        int radius = (int) optSlider("Radius", 40);
        long duration = (long) optSlider("Duration (ms)", 800);
        int arcSize = (int) optSlider("Arc Size", 20);
        int baseColor = resolveColor();

        int cx = x + width / 2;
        int cy = y + height / 2;

        long now = System.currentTimeMillis();
        double angle;
        float fade;

        if (editor) {
            angle = 45.0; // 45 degrees for preview
            fade = 0.9f;
        } else {
            long hurtAt = DamageTilt.CLIENT.lastHurtTime();
            long age = now - hurtAt;
            if (age < 0 || age > duration) return;
            fade = 1.0f - ((float) age / duration);
            float dirYaw = DamageTilt.CLIENT.yaw(now);
            if (Float.isNaN(dirYaw)) {
                dirYaw = DamageTilt.CLIENT.lastYaw();
            }
            if (Float.isNaN(dirYaw)) {
                dirYaw = 0.0f;
            }
            angle = calculateIndicatorAngle(dirYaw);
        }

        int alpha = (int) (255 * Math.max(0.0f, Math.min(1.0f, fade)));
        if (alpha <= 5) return;

        int color = (alpha << 24) | (baseColor & 0x00FFFFFF);

        // Arc Size: the arc spans twice that many degrees and is a fifth of it thick.
        drawArc(g, cx, cy, radius, Math.max(2, arcSize / 5), Math.toRadians(angle), Math.toRadians(arcSize), color);
    }

    /**
     * A ring segment around (cx, cy): {@code thickness} deep outside {@code radius}, centred on the clock angle {@code angle} (radians, 0
     * straight up, clockwise) and {@code halfSpan} (under 90 degrees) either side. Each pixel row inside it is one fill per run, so the
     * arc is solid and no pixel blends twice; only the box around the segment is scanned.
     */
    static void drawArc(LadsGraphics g, int cx, int cy, int radius, int thickness, double angle, double halfSpan, int color) {
        double dirX = Math.sin(angle), dirY = -Math.cos(angle), cosHalf = Math.cos(halfSpan);
        int outer = radius + thickness;
        double inner2 = (double) radius * radius, outer2 = (double) outer * outer, cos2 = cosHalf * cosHalf;
        double mid = (radius * cosHalf + outer) / 2, half = Math.hypot(outer * Math.sin(halfSpan), (outer - radius * cosHalf) / 2) + 1;
        int minX = (int) Math.floor(dirX * mid - half), minY = (int) Math.floor(dirY * mid - half), maxX = minX + (int) Math.ceil(2 * half) + 1;
        int maxY = minY + (int) Math.ceil(2 * half) + 1;
        for (int y = minY; y < maxY; y++) {
            int run = Integer.MIN_VALUE;
            for (int x = minX; x <= maxX; x++) {
                double px = x + 0.5, py = y + 0.5, d2 = px * px + py * py, dot = px * dirX + py * dirY;
                boolean inside = x < maxX && d2 >= inner2 && d2 < outer2 && dot > 0 && dot * dot >= d2 * cos2;
                if (inside && run == Integer.MIN_VALUE) run = x;
                else if (!inside && run != Integer.MIN_VALUE) {
                    g.fill(cx + run, cy + y, cx + x, cy + y + 1, color);
                    run = Integer.MIN_VALUE;
                }
            }
        }
    }
}
