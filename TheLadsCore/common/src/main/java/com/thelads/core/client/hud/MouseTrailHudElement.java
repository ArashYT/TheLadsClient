package com.thelads.core.client.hud;

import com.thelads.core.client.bridge.LadsGraphics;

public class MouseTrailHudElement extends HudElement {
    private static final int MAX_TRAIL = 100;
    /** A trail point at most this often, so the 100 hold the longest lifetime (1000 ms) at any frame rate. */
    private static final long SAMPLE_MS = 10;
    private final float[] trailX = new float[MAX_TRAIL];
    private final float[] trailY = new float[MAX_TRAIL];
    private final long[] trailTime = new long[MAX_TRAIL];
    private int trailHead = 0;
    private int trailCount = 0;
    private float lastYaw = -9999, lastPitch = -9999;
    private float mouseDotX = 0, mouseDotY = 0;
    private long lastFrame;

    public MouseTrailHudElement() {
        this.x = 20;
        this.y = 120;
        this.width = 50;
        this.height = 50;
    }

    @Override
    public void prepareRender(LadsGraphics g, boolean editor) {
        int size = (int) optSlider("Box size", 50);
        width = size;
        height = size;
    }

    @Override
    public void render(LadsGraphics g) {
        renderTrail(g, false);
    }

    @Override
    public void renderEditor(LadsGraphics g) {
        renderTrail(g, true);
    }

    private void renderTrail(LadsGraphics g, boolean editor) {
        drawBackground(g);
        int textColor = resolveColor();
        boolean centerDot = optBool("Center dot", true);
        long lifetime = (long) optSlider("Trail lifetime (ms)", 350);
        float sens = (float) optSlider("Sensitivity", 100) / 100.0f;

        int cx = x + width / 2;
        int cy = y + height / 2;
        int radius = Math.min(width, height) / 2 - 3;
        long now = System.nanoTime() / 1_000_000L; // monotonic milliseconds (currentTimeMillis can step 15 ms on Windows)

        if (editor) {
            // Draw sample trail in a smooth curve
            for (int i = 0; i < 15; i++) {
                double angle = (now / 400.0) - (i * 0.15);
                float px = cx + (float) Math.sin(angle) * (radius * 0.7f);
                float py = cy + (float) Math.cos(angle * 2.0) * (radius * 0.5f);
                float alpha = 1.0f - (i / 15.0f);
                int col = ((int) (255 * alpha) << 24) | (textColor & 0x00FFFFFF);
                g.fill(Math.round(px) - 1, Math.round(py) - 1, Math.round(px) + 1, Math.round(py) + 1, col);
            }
        } else {
            float yaw = g.getGame().getYaw();
            float pitch = g.getGame().getPitch();
            if (Float.isFinite(yaw) && Float.isFinite(pitch)) {
                if (lastYaw != -9999 && lastPitch != -9999) {
                    float dyaw = yaw - lastYaw;
                    // Handle 360 wrap
                    if (dyaw > 180) dyaw -= 360;
                    else if (dyaw < -180) dyaw += 360;
                    float dpitch = pitch - lastPitch;

                    mouseDotX = Math.max(-radius, Math.min(radius, mouseDotX - dyaw * sens * 4.0f));
                    mouseDotY = Math.max(-radius, Math.min(radius, mouseDotY + dpitch * sens * 4.0f));

                    if (trailCount == 0 || now - trailTime[(trailHead + MAX_TRAIL - 1) % MAX_TRAIL] >= SAMPLE_MS) {
                        trailX[trailHead] = cx + mouseDotX;
                        trailY[trailHead] = cy + mouseDotY;
                        trailTime[trailHead] = now;
                        trailHead = (trailHead + 1) % MAX_TRAIL;
                        if (trailCount < MAX_TRAIL) trailCount++;
                    }
                }
                lastYaw = yaw;
                lastPitch = pitch;
            }

            // Decay position back to center by time, not per frame (per frame, 500 FPS pulled it back 8 times as fast as 60)
            float decay = decay(lastFrame == 0 ? 0 : now - lastFrame);
            lastFrame = now;
            mouseDotX *= decay;
            mouseDotY *= decay;

            for (int i = 0; i < trailCount; i++) {
                int idx = (trailHead - trailCount + i + MAX_TRAIL) % MAX_TRAIL;
                long age = now - trailTime[idx];
                if (age > lifetime) continue;
                float fade = 1.0f - (float) age / lifetime;
                int col = ((int) (255 * fade) << 24) | (textColor & 0x00FFFFFF);
                int px = Math.round(trailX[idx]);
                int py = Math.round(trailY[idx]);
                g.fill(px - 1, py - 1, px + 1, py + 1, col);
            }
        }

        if (centerDot) {
            g.fill(cx - 1, cy - 1, cx + 1, cy + 1, 0x80FFFFFF);
        }
    }

    /** What stays of the dot's offset after {@code ms}: 0.85 per 60 FPS frame, the old per-frame factor, at any frame rate. */
    public static float decay(double ms) {
        return (float) Math.pow(0.85, Math.min(ms, 1000) * 60 / 1000);
    }
}
