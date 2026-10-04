package com.thelads.core.client.killbanner;

/**
 * What a skin banner draws at a moment, from Valorant footage: the skin's frames, a dark backdrop, the kill mark
 * (a lattice that lands large and shrinks onto the icon), the icon's red strobe, the emblem's red cavity (Reaver) and
 * the HEADSHOT label. Pure timing; the adapters draw it.
 */
public final class KillBannerPlayer {
    /** The icon's red strobe per frame from MARK_FRAME: four pulses 6 frames (100 ms) apart, measured in both skins. */
    static final float[] STROBE = {.85f, .27f, .01f, .06f, .38f, 1f, .79f, .25f, .01f, .06f, .40f, 1f, .79f, .27f, .02f,
        .05f, .39f, .97f, .66f, .22f};
    /** Reaver's frames stop settled; its way out is drawn: the icon goes (14 frames), the frame stays, then fades. */
    static final int ICON_OUT = 14, FRAME_HOLD = 38, DRAWN_EXIT = 54;
    public static final int MARK_RED = 0xFFC41626, STROBE_RED = 0xFFE2122C;

    /** iconY: cell pixels the strip's icon sits below its settled place; everything drawn over the banner follows it. */
    public record Frame(int stripFrame, float iconAlpha, float iconScale, float restAlpha, float shadowAlpha,
                        float strobe, float markSize, float markThinAlpha, float markAlpha, int markColor,
                        float heartAlpha, float labelAlpha, float iconY) {
        /** True when the frame comes from the drawn way out (the strip's exit layers), not the strip. */
        public boolean drawnExit() { return stripFrame < 0; }
    }

    private KillBannerPlayer() {}

    /** Seconds the banner takes with no hold: its frames up to the settled one, and the way out. */
    public static double minimumSeconds(KillBannerStrip strip) {
        return (strip.introEnd + 1 + exitLength(strip)) / 60.0;
    }

    static int exitLength(KillBannerStrip strip) { return strip.exitFrames > 0 ? strip.exitFrames : DRAWN_EXIT; }

    /**
     * The banner {@code age} seconds after its kill, shown for {@code seconds} in total (the settled frame holds for
     * what its frames leave). Null once it is gone.
     */
    public static Frame at(KillBannerStyle style, KillBannerStrip strip, double age, double seconds, boolean headshot) {
        if (age < 0 || !Double.isFinite(age)) return null;
        int f = (int) Math.floor(age * 60);
        int intro = strip.introEnd + 1, exit = exitLength(strip);
        int hold = Math.max(0, (int) Math.round(seconds * 60) - intro - exit);
        int e = f - intro - hold;
        if (e >= exit) return null;

        int stripFrame = Math.min(f, strip.introEnd);
        float iconAlpha = 1, iconScale = 1, restAlpha = 1, leaving = 1;
        if (e >= 0) {
            leaving = 1 - smooth(0, exit, e);
            if (strip.exitFrames > 0) {
                stripFrame = strip.introEnd + 1 + e;
                iconAlpha = 1 - smooth(0, 6, e);
                iconScale = 1 - .3f * smooth(0, 6, e);
            } else {
                stripFrame = -1;
                iconAlpha = 1 - Math.min(1, e / (float) ICON_OUT);
                iconScale = 1 - .35f * smooth(0, ICON_OUT, e);
                restAlpha = e < FRAME_HOLD ? 1 : 1 - (e - FRAME_HOLD) / (float) (DRAWN_EXIT - FRAME_HOLD);
            }
        }
        int m = KillBannerStyle.MARK_FRAME, t = f - m;
        float shadow = .5f * smooth(m - 8, m, f) * leaving;
        float strobe = t >= 0 && t < STROBE.length ? STROBE[t] : 0;
        float size = 0, thin = 0, solid = 0, heart = 0;
        int color = MARK_RED;
        if (t >= 0) {
            size = style.markSize * (1 + 1.4f * (float) Math.exp(-t / 1.5)) * iconScale;
            float arriving = 1 - smooth(0, 3, t);
            thin = .75f * arriving * iconAlpha;
            solid = (1 - arriving) * iconAlpha;
            // The mark flips to white against the red flashes, and is red on the white icon.
            float white = t > 1 ? smooth(.05f, .45f, strobe) : 1;
            color = mix(MARK_RED, 0xFFFFFFFF, white);
            heart = style.heart ? iconAlpha : 0;
        }
        float label = headshot ? smooth(m - 4, m + 4, f) * (e >= 0 ? 1 - smooth(0, 8, e) : 1) : 0;
        return new Frame(stripFrame, iconAlpha, iconScale, restAlpha, shadow, strobe * iconAlpha, size, thin, solid,
            color, heart, label, strip.iconY(stripFrame));
    }

    static float smooth(float from, float to, float x) {
        float t = Math.max(0, Math.min(1, (x - from) / (to - from)));
        return t * t * (3 - 2 * t);
    }

    static int mix(int a, int b, float t) {
        int out = 0;
        for (int shift = 0; shift <= 24; shift += 8)
            out |= Math.round(((a >>> shift) & 255) * (1 - t) + ((b >>> shift) & 255) * t) << shift;
        return out;
    }
}
