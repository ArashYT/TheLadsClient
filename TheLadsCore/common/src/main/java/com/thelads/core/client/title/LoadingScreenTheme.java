package com.thelads.core.client.title;

import com.thelads.core.client.bridge.LadsGraphics;
import com.thelads.core.client.gui.LadsPalette;

/**
 * The Lads loading screen, replacing Mojang Studios' on every version: burgundy gradient, the Lads Client wordmark, a
 * Windows-style ring of chasing dots and a thin progress bar. Everything moves with the clock, never with a frame count.
 */
public final class LoadingScreenTheme {
    /** Classpath PNG, read straight from the jar: resource packs are not loaded yet at startup. */
    public static final String LOGO = "/assets/theladscore/textures/gui/loading_logo.png";
    public static final int LOGO_WIDTH = 1024, LOGO_HEIGHT = 163;
    public static final int DOTS = 5;
    private static final double CYCLE = 5.5, DOT_DELAY = .24;
    // Keyframes of one dot (fraction of the cycle, degrees clockwise from 12 o'clock): a fast start from the bottom, a slow
    // pass over the top, a fast lap, a second slow pass, a fast run home, then hidden until the next cycle.
    private static final double[] KEY_TIME = {0, .07, .30, .39, .70, .75};
    private static final double[] KEY_ANGLE = {180, 300, 410, 645, 770, 900};
    private static final int[] KEY_EASE = {1, 0, 2, 0, 1}; // 0 linear, 1 ease-out, 2 ease-in-out
    private static final int DISC_DETAIL = 4; // discs are drawn at 1/4 GUI pixel for smooth sub-pixel motion
    private LoadingScreenTheme() {}

    @FunctionalInterface
    public interface Logo { void draw(int x, int y, int width, int height, float alpha); }

    /** Each dot's angle in degrees clockwise from 12 o'clock, or NaN while that dot is hidden. */
    public static double[] dotAngles(double seconds) {
        double[] angles = new double[DOTS];
        for (int i = 0; i < DOTS; i++) {
            double t = ((seconds - i * DOT_DELAY) / CYCLE) % 1;
            angles[i] = dotAngle(t < 0 ? t + 1 : t);
        }
        return angles;
    }

    static double dotAngle(double t) {
        if (t >= KEY_TIME[KEY_TIME.length - 1]) return Double.NaN;
        int k = 0;
        while (t >= KEY_TIME[k + 1]) k++;
        double p = (t - KEY_TIME[k]) / (KEY_TIME[k + 1] - KEY_TIME[k]);
        p = switch (KEY_EASE[k]) {
            case 1 -> Math.sin(p * Math.PI / 2);
            case 2 -> (1 - Math.cos(p * Math.PI)) / 2;
            default -> p;
        };
        return (KEY_ANGLE[k] + (KEY_ANGLE[k + 1] - KEY_ANGLE[k]) * p) % 360;
    }

    /**
     * One frame. {@code alpha} fades the whole screen, {@code barAlpha} only the progress bar (vanilla hides the bar first);
     * {@code progress} is vanilla's smoothed 0..1 reload progress.
     */
    public static void render(LadsGraphics g, int width, int height, float progress, float alpha, float barAlpha, Logo logo) {
        if (alpha <= 0) return;
        for (int i = 0; i < 32; i++)
            g.fill(0, i * height / 32, width, (i + 1) * height / 32, fade(mix(LadsPalette.BACKGROUND, 0xFF30111C, i / 31f), alpha));
        int logoWidth = Math.max(1, Math.min(width * 46 / 100, height * 2));
        int logoHeight = logoWidth * LOGO_HEIGHT / LOGO_WIDTH;
        int cx = width / 2, logoY = height * 2 / 5 - logoHeight / 2;
        // A soft red band of light behind the wordmark: rows fading out from its centre line.
        int glowCenter = logoY + logoHeight / 2, glowHalf = Math.max(8, logoHeight * 2);
        for (int i = 0; i < 40; i++) {
            double d = (i + .5) / 20 - 1;
            g.fill(0, glowCenter - glowHalf + i * glowHalf / 20, width, glowCenter - glowHalf + (i + 1) * glowHalf / 20,
                fade(0x3A8B0000, alpha * (float)Math.exp(-d * d * 4)));
        }
        logo.draw(cx - logoWidth / 2, logoY, logoWidth, logoHeight, alpha);

        float ring = Math.max(7, Math.min(24, height * .048f)), dot = Math.max(1.2f, ring * .15f);
        float cy = logoY + logoHeight + Math.max(ring * 2.2f, height * .14f);
        int radius = Math.round(dot * DISC_DETAIL);
        for (double angle : dotAngles(System.nanoTime() / 1e9)) {
            if (Double.isNaN(angle)) continue;
            double rad = Math.toRadians(angle);
            g.pushPose();
            g.translate(cx + (float)(Math.sin(rad) * ring), cy - (float)(Math.cos(rad) * ring));
            g.scale(1f / DISC_DETAIL, 1f / DISC_DETAIL);
            // Each row's partly covered end pixels get partial alpha: smooth edges without textures.
            for (int y = -radius; y < radius; y++) {
                double edge = Math.sqrt(Math.max(0, radius * radius - (y + .5) * (y + .5)));
                int half = (int)edge;
                g.fill(-half, y, half, y + 1, fade(LadsPalette.TEXT, alpha));
                int rim = fade(LadsPalette.TEXT, alpha * (float)(edge - half));
                g.fill(-half - 1, y, -half, y + 1, rim);
                g.fill(half, y, half + 1, y + 1, rim);
            }
            g.popPose();
        }

        float shown = alpha * Math.max(0, Math.min(1, barAlpha));
        if (shown <= 0) return;
        int barWidth = Math.max(40, logoWidth * 2 / 5), barY = Math.round(cy + ring + Math.max(10, height * .06f));
        int barX = cx - barWidth / 2, filled = Math.round(barWidth * Math.max(0, Math.min(1, progress)));
        g.fill(barX, barY, barX + barWidth, barY + 2, fade(LadsPalette.BORDER, shown * .8f));
        g.fill(barX, barY, barX + filled, barY + 2, fade(LadsPalette.ACCENT, shown));
    }

    private static int fade(int argb, float alpha) {
        return Math.round((argb >>> 24) * Math.max(0, Math.min(1, alpha))) << 24 | argb & 0xFFFFFF;
    }

    private static int mix(int a, int b, float t) {
        int result = 0;
        for (int shift = 0; shift <= 24; shift += 8)
            result |= ((int)(((a >>> shift) & 255) * (1 - t) + ((b >>> shift) & 255) * t)) << shift;
        return result;
    }
}
