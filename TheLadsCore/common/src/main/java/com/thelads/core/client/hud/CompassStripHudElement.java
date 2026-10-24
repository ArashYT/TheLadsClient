package com.thelads.core.client.hud;

import com.thelads.core.client.bridge.LadsGraphics;
import com.thelads.core.config.HudSettings;

public class CompassStripHudElement extends HudElement {
    public CompassStripHudElement() {
        this.x = 20;
        this.y = 20;
        this.width = 140;
        this.height = 18;
    }

    @Override
    public void prepareRender(LadsGraphics g, boolean editor) {
        width = (int) optSlider("Width", 140);
        height = 18;
    }

    @Override
    public void render(LadsGraphics g) {
        renderStrip(g, false);
    }

    @Override
    public void renderEditor(LadsGraphics g) {
        renderStrip(g, true);
    }

    public static float normalizeYaw(float rawYaw) {
        if (!Float.isFinite(rawYaw)) return 180.0f;
        return ((rawYaw % 360.0f) + 360.0f) % 360.0f;
    }

    private void renderStrip(LadsGraphics g, boolean editor) {
        drawBackground(g);
        float rawYaw = g.getGame().getYaw();
        float yaw = editor ? 180.0f : normalizeYaw(rawYaw);

        int textColor = resolveColor();
        boolean shadow = HudSettings.getInstance().isTextShadow();
        boolean showDegrees = optBool("Show degrees", true);
        boolean centerNotch = optBool("Center notch", true);

        int cx = x + width / 2;
        float pixelsPerDegree = (float) width / 100.0f;

        g.enableScissor(x, y, x + width, y + height);

        int minDeg = (int) Math.floor((yaw - 55) / 15.0) * 15;
        int maxDeg = (int) Math.ceil((yaw + 55) / 15.0) * 15;

        for (int deg = minDeg; deg <= maxDeg; deg += 15) {
            int normDeg = ((deg % 360) + 360) % 360;
            int px = Math.round(cx + (deg - yaw) * pixelsPerDegree);

            String label = getDirectionLabel(normDeg, showDegrees);
            if (label != null) {
                int tw = g.textWidth(label);
                g.drawText(label, px - tw / 2, y + 4, textColor, shadow);
            } else {
                g.fill(px, y + 2, px + 1, y + 6, 0x80FFFFFF & textColor);
            }
        }

        g.disableScissor();

        if (centerNotch) {
            // Draw a distinct red or accent center indicator
            g.fill(cx - 1, y, cx + 1, y + 3, 0xFFFF3333);
            g.fill(cx - 1, y + height - 3, cx + 1, y + height, 0xFFFF3333);
        }
    }

    private static String getDirectionLabel(int deg, boolean showDegrees) {
        return switch (deg) {
            case 180 -> "N";
            case 225 -> "NE";
            case 270 -> "E";
            case 315 -> "SE";
            case 0 -> "S";
            case 45 -> "SW";
            case 90 -> "W";
            case 135 -> "NW";
            default -> showDegrees && (deg % 30 == 0) ? String.valueOf(deg) : null;
        };
    }
}
