package com.thelads.core.client;

import com.thelads.core.modules.ColorSaturationModule;

/**
 * Color Saturation (ColorSaturationModule) as the numbers each version's screen effect applies to the world: one 3x4 color matrix
 * (hue turn, then saturation, contrast around mid gray and brightness as an offset) and a vibrance amount the shader applies per pixel
 * after it, as vibrance depends on how colorful the pixel already is. Lunar's scale: every slider 0-10, 5 changes nothing, Hue 0 (or
 * 10, a full turn) turns no hue. Versions call {@link #update} once a frame; it recomputes only after a slider moved.
 */
public final class ColorGrade {
    /** The gray every row keeps (W3C feColorMatrix weights, Rec. 709 luma): gray stays gray through hue and saturation. */
    static final float LR = 0.213F, LG = 0.715F, LB = 0.072F;

    /** Row-major 3x4 (r' = m[0]r + m[1]g + m[2]b + m[3], then g', then b'), and the shader's vibrance (-1..1). */
    public final float[] matrix = new float[12];
    public float vibrance;
    /** False while the sliders change nothing (the version skips the pass). */
    public boolean active;
    private double hue = Double.NaN, saturation, vibranceSlider, brightness, contrast;
    private boolean grayscale;

    /** True when the matrix or vibrance changed since the last call (the version uploads them). */
    public boolean update(ColorSaturationModule m) {
        double h = m.hue.getValue(), s = m.saturation.getValue(), v = m.vibrance.getValue(), b = m.brightness.getValue(), c = m.contrast.getValue();
        boolean g = m.grayscale.get();
        if (h == hue && s == saturation && v == vibranceSlider && b == brightness && c == contrast && g == grayscale) return false;
        hue = h; saturation = s; vibranceSlider = v; brightness = b; contrast = c; grayscale = g;
        vibrance = g ? 0 : (float) ((clamp(v) - 5) / 5);
        active = matrix(h, s, b, c, g, matrix) || vibrance != 0;
        return true;
    }

    /** Fills {@code out} (12 floats); returns false when it is the identity. */
    static boolean matrix(double hue, double saturation, double brightness, double contrast, boolean grayscale, float[] out) {
        double angle = clamp(hue) / 10 * 2 * Math.PI, cos = Math.cos(angle), sin = Math.sin(angle);
        double[] h = {
            LR + cos * (1 - LR) - sin * LR, LG - cos * LG - sin * LG, LB - cos * LB + sin * (1 - LB),
            LR - cos * LR + sin * 0.143, LG + cos * (1 - LG) + sin * 0.140, LB - cos * LB - sin * 0.283,
            LR - cos * LR - sin * (1 - LR), LG - cos * LG + sin * LG, LB + cos * (1 - LB) + sin * LB};
        double s = grayscale ? 0 : clamp(saturation) / 5;
        double[] sat = {
            LR + (1 - LR) * s, LG - LG * s, LB - LB * s,
            LR - LR * s, LG + (1 - LG) * s, LB - LB * s,
            LR - LR * s, LG - LG * s, LB + (1 - LB) * s};
        double k = clamp(contrast) / 5, offset = 0.5 * (1 - k) + (clamp(brightness) - 5) / 10;
        boolean identity = Math.abs(offset) < 1e-6;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                double v = 0;
                for (int i = 0; i < 3; i++) v += sat[row * 3 + i] * h[i * 3 + col];
                v *= k;
                out[row * 4 + col] = (float) v;
                identity &= Math.abs(v - (row == col ? 1 : 0)) < 1e-6;
            }
            out[row * 4 + 3] = (float) offset;
        }
        return !identity;
    }

    private static double clamp(double slider) {
        return Math.max(0, Math.min(10, slider));
    }
}
