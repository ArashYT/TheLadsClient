package com.thelads.core.client.killbanner;

import java.io.IOException;
import java.io.InputStream;

/**
 * The Valorant skin banners, measured from gameplay footage (tools/killbanner/preview_killbanner.py holds the same
 * numbers). Geometry is in cell pixels of the style's frames; colours are HSV on 0-255 scales.
 */
public enum KillBannerStyle {
    // Reaver's flipbook purple, then Valorant's Base, Red, Black and White variants as they appear in game.
    REAVER("reaver", 128.3f, 99.9f, 43.6f, -5f, 26f, 56f, true, 165, 230, new int[] {197, 255, 166},
        new String[] {"Base", "Red", "Black", "White"},
        new int[][] {{195, 255, 152}, {249, 247, 155}, {250, 229, 132}, {104, 161, 201}}),
    // Rogue's export red, then Base, Green, Red (gold in game) and Blue.
    ROGUE("rogue", 157.7f, 106.1f, 48.2f, -11.5f, 30f, 66f, false, 232, 20, new int[] {8, 255, 166},
        new String[] {"Base", "Green", "Red", "Blue"},
        new int[][] {{251, 232, 157}, {76, 224, 195}, {28, 203, 166}, {157, 204, 213}});

    /** Frame (60 fps, from the kill) the kill mark lands and the red strobe starts, in both skins. */
    public static final int MARK_FRAME = 11;
    /** One cell pixel on a 1080p screen, as Valorant draws the banner. */
    public static final float SCREEN_SCALE = 1.15f;

    public final String id;
    /** markY: the kill mark below the ring centre; labelY: the HEADSHOT label's top, under the outer frame. */
    public final float anchorX, anchorY, ring, markY, markSize, labelY;
    public final boolean heart;
    final int bandLow, bandHigh;
    final int[] accent;
    public final String[] variantNames;
    final int[][] variants;
    private final KillBannerStrip[] strips = new KillBannerStrip[5];

    KillBannerStyle(String id, float anchorX, float anchorY, float ring, float markY, float markSize, float labelY, boolean heart,
                    int bandLow, int bandHigh, int[] accent, String[] variantNames, int[][] variants) {
        this.id = id;
        this.anchorX = anchorX;
        this.anchorY = anchorY;
        this.ring = ring;
        this.markY = markY;
        this.markSize = markSize;
        this.labelY = labelY;
        this.heart = heart;
        this.bandLow = bandLow;
        this.bandHigh = bandHigh;
        this.accent = accent;
        this.variantNames = variantNames;
        this.variants = variants;
    }

    public String asset(String name) { return "/assets/theladscore/killbanner/" + id + "/" + name; }

    /** Frames for 1 to 5 kills (more show as 5), read from the jar on first use. */
    public synchronized KillBannerStrip strip(int kills) {
        int k = Math.max(1, Math.min(5, kills));
        if (strips[k - 1] == null) {
            String path = asset("k" + k + ".lkb");
            try (InputStream in = KillBannerStyle.class.getResourceAsStream(path)) {
                if (in == null) throw new IOException(path + " is missing");
                strips[k - 1] = KillBannerStrip.read(in);
            } catch (IOException failure) {
                throw new IllegalStateException("Kill banner frames unavailable: " + path, failure);
            }
        }
        return strips[k - 1];
    }

    /** Recolours the accent (the style's hue band, saturated) in place: hue, and saturation and value in proportion. */
    public void recolor(byte[] rgba, int variant) {
        int[] target = variants[Math.max(0, Math.min(variants.length - 1, variant))];
        float sScale = target[1] / (float) accent[1], vScale = target[2] / (float) accent[2];
        float[] hsv = new float[3];
        int[] rgb = new int[3];
        for (int i = 0; i < rgba.length; i += 4) {
            if (rgba[i + 3] == 0) continue;
            int r = rgba[i] & 255, g = rgba[i + 1] & 255, b = rgba[i + 2] & 255;
            toHsv(r, g, b, hsv);
            boolean inBand = bandLow < bandHigh ? hsv[0] >= bandLow && hsv[0] <= bandHigh : hsv[0] >= bandLow || hsv[0] <= bandHigh;
            float weight = inBand ? Math.max(0, Math.min(1, (hsv[1] - 50) / 60f)) : 0;
            if (weight == 0) continue;
            toRgb(target[0], Math.min(255, hsv[1] * sScale), Math.min(255, hsv[2] * vScale), rgb);
            rgba[i] = (byte) Math.round(r + (rgb[0] - r) * weight);
            rgba[i + 1] = (byte) Math.round(g + (rgb[1] - g) * weight);
            rgba[i + 2] = (byte) Math.round(b + (rgb[2] - b) * weight);
        }
    }

    /** RGB to HSV with hue on 0-255 (OpenCV's HSV_FULL), saturation and value on 0-255. */
    static void toHsv(int r, int g, int b, float[] out) {
        int max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b)), d = max - min;
        float h;
        if (d == 0) h = 0;
        else if (max == r) h = 60f * (g - b) / d;
        else if (max == g) h = 120 + 60f * (b - r) / d;
        else h = 240 + 60f * (r - g) / d;
        if (h < 0) h += 360;
        out[0] = h * 256 / 360;
        out[1] = max == 0 ? 0 : 255f * d / max;
        out[2] = max;
    }

    static void toRgb(float h, float s, float v, int[] out) {
        float hue = (h % 256) * 360 / 256 / 60, sat = s / 255;
        int sector = (int) Math.floor(hue) % 6;
        float f = hue - (float) Math.floor(hue);
        float p = v * (1 - sat), q = v * (1 - sat * f), t = v * (1 - sat * (1 - f));
        float r, g, b;
        switch (sector) {
            case 0 -> { r = v; g = t; b = p; }
            case 1 -> { r = q; g = v; b = p; }
            case 2 -> { r = p; g = v; b = t; }
            case 3 -> { r = p; g = q; b = v; }
            case 4 -> { r = t; g = p; b = v; }
            default -> { r = v; g = p; b = q; }
        }
        out[0] = Math.round(r);
        out[1] = Math.round(g);
        out[2] = Math.round(b);
    }
}
