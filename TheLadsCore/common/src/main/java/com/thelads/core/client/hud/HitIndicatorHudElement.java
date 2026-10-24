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
        int size = (int) optSlider("Size", 20);
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

        // Angle in radians (0 is straight up / 12 o'clock, rotating clockwise)
        double rad = Math.toRadians(angle);
        double cos = Math.cos(rad);
        double sin = Math.sin(rad);

        // Tip of the indicator arc/chevron
        int tipX = (int) Math.round(cx + sin * radius);
        int tipY = (int) Math.round(cy - cos * radius);

        // Wings of chevron
        double spreadRad = Math.toRadians(25.0);
        double wingDist = radius + (size * 0.5);
        int wing1X = (int) Math.round(cx + Math.sin(rad - spreadRad) * wingDist);
        int wing1Y = (int) Math.round(cy - Math.cos(rad - spreadRad) * wingDist);
        int wing2X = (int) Math.round(cx + Math.sin(rad + spreadRad) * wingDist);
        int wing2Y = (int) Math.round(cy - Math.cos(rad + spreadRad) * wingDist);

        // Draw chevron
        g.fill(tipX - 2, tipY - 2, tipX + 2, tipY + 2, color);
        g.fill(wing1X - 1, wing1Y - 1, wing1X + 1, wing1Y + 1, color);
        g.fill(wing2X - 1, wing2Y - 1, wing2X + 1, wing2Y + 1, color);

        // Fill connecting dots
        int mid1X = (tipX + wing1X) / 2;
        int mid1Y = (tipY + wing1Y) / 2;
        int mid2X = (tipX + wing2X) / 2;
        int mid2Y = (tipY + wing2Y) / 2;
        g.fill(mid1X - 1, mid1Y - 1, mid1X + 1, mid1Y + 1, color);
        g.fill(mid2X - 1, mid2Y - 1, mid2X + 1, mid2Y + 1, color);
    }
}
