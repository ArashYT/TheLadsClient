/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.util.Mth
 */
package com.thelads.core.client;

import com.thelads.core.config.HudSettings;
import net.minecraft.util.Mth;

public class ChromaUtil {
    private static final int[] FADE_PALETTE = new int[]{-43691, -22016, -171, -11141291, -11141121, -11184641, -5635841, -43521};

    public static int chroma(long offsetMs, long periodMs) {
        float hue = (float)((System.currentTimeMillis() + offsetMs) % periodMs) / (float)periodMs;
        return Mth.hsvToRgb((float)hue, (float)0.9f, (float)1.0f) | 0xFF000000;
    }

    public static int fade(long offsetMs, long perColorMs) {
        int[] pal = HudSettings.getInstance().getFadePalette();
        if (pal == null) {
            pal = FADE_PALETTE;
        }
        long t = System.currentTimeMillis() + offsetMs;
        long total = (long)pal.length * perColorMs;
        long pos = (t % total + total) % total;
        int idx = (int)(pos / perColorMs);
        float frac = (float)(pos % perColorMs) / (float)perColorMs;
        return ChromaUtil.lerp(pal[idx], pal[(idx + 1) % pal.length], frac) | 0xFF000000;
    }

    private static int lerp(int a, int b, float t) {
        int ar = a >> 16 & 0xFF;
        int ag = a >> 8 & 0xFF;
        int ab = a & 0xFF;
        int br = b >> 16 & 0xFF;
        int bg = b >> 8 & 0xFF;
        int bb = b & 0xFF;
        int r = (int)((float)ar + (float)(br - ar) * t);
        int g = (int)((float)ag + (float)(bg - ag) * t);
        int bl = (int)((float)ab + (float)(bb - ab) * t);
        return r << 16 | g << 8 | bl;
    }

    public static int forMode(int mode, long offsetMs) {
        switch (mode) {
            case 1: {
                return ChromaUtil.chroma(offsetMs, 4000L);
            }
            case 2: {
                return ChromaUtil.chroma(offsetMs, 1500L);
            }
            case 3: {
                return ChromaUtil.fade(offsetMs, 1500L);
            }
        }
        return -1;
    }
}

