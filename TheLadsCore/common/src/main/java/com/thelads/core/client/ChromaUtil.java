package com.thelads.core.client;

import com.thelads.core.config.HudSettings;
import java.awt.Color;

/**
 * Animated colour helpers for HUD elements (chroma / fade).
 * Implemented in pure Java 21 without version-specific Minecraft dependencies.
 */
public class ChromaUtil {
    private static final int[] FADE_PALETTE = {
        0xFFFF5555, 0xFFFFAA00, 0xFFFFFF55, 0xFF55FF55,
        0xFF55FFFF, 0xFF5555FF, 0xFFAA00FF, 0xFFFF55FF
    };

    public static int chroma(long offsetMs, long periodMs) {
        float hue = ((System.currentTimeMillis() + offsetMs) % periodMs) / (float) periodMs;
        return Color.HSBtoRGB(hue, 0.9f, 1.0f) | 0xFF000000;
    }

    public static int fade(long offsetMs, long perColorMs) {
        int[] pal = HudSettings.getInstance().getFadePalette();
        if (pal == null) {
            pal = FADE_PALETTE;
        }
        long t = System.currentTimeMillis() + offsetMs;
        long total = (long) pal.length * perColorMs;
        long pos = ((t % total) + total) % total;
        int idx = (int) (pos / perColorMs);
        float frac = (pos % perColorMs) / (float) perColorMs;
        return lerp(pal[idx], pal[(idx + 1) % pal.length], frac) | 0xFF000000;
    }

    private static int lerp(int a, int b, float t) {
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        int r = (int) (ar + (br - ar) * t);
        int g = (int) (ag + (bg - ag) * t);
        int bl = (int) (ab + (bb - ab) * t);
        return (r << 16) | (g << 8) | bl;
    }

    public static int forMode(int mode, long offsetMs) {
        switch (mode) {
            case 1: return chroma(offsetMs, 4000L);
            case 2: return chroma(offsetMs, 1500L);
            case 3: return fade(offsetMs, 1500L);
            default: return 0xFFFFFFFF;
        }
    }
}
