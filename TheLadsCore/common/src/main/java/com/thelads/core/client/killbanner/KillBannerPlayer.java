package com.thelads.core.client.killbanner;

import java.util.Random;

/**
 * What a skin banner draws at a moment, from Valorant footage: the skin's frames, a dark backdrop, the kill mark
 * (a lattice that lands large and shrinks onto the icon), the icon's red strobe, the emblem's red cavity (Reaver) and
 * the HEADSHOT label. Reaver and Rogue play their measured frames ({@link #at}); the Kingdom Archives skins, which are
 * still layers, move those layers the way the two skins' frames move ({@link #layers}). Pure timing; the adapters draw it.
 */
public final class KillBannerPlayer {
    /** The icon's red strobe per frame from MARK_FRAME: four pulses 6 frames (100 ms) apart, measured in both skins. */
    static final float[] STROBE = {.85f, .27f, .01f, .06f, .38f, 1f, .79f, .25f, .01f, .06f, .40f, 1f, .79f, .27f, .02f,
        .05f, .39f, .97f, .66f, .22f};
    /** Reaver's frames stop settled; its way out is drawn: the icon goes (14 frames), the frame stays, then fades. */
    static final int ICON_OUT = 14, FRAME_HOLD = 38, DRAWN_EXIT = 54;
    public static final int MARK_RED = 0xFFC41626, STROBE_RED = 0xFFE2122C;
    /** Frames a still skin's banner moves before it settles, by kill count (1 to 5): as long as Reaver's and Rogue's. */
    static final int[] STILL_INTRO = {0, 52, 88, 80, 80, 205};
    /** A still skin's way out, as Rogue's: the frame goes at once, the emblem shrinks and darkens, then the ring and pips. */
    static final int STILL_EXIT = 16;
    /** From two kills both skins throw a spray to the sides after the mark (frame 26), then a glint runs round the ring. */
    static final int[] SPRAY_COUNT = {0, 0, 16, 22, 28, 36};
    static final int SPRAY_FRAME = 26, GLINT_FRAME = 58, GLINT_LOOP = 26;
    /** The spray slows (per second) and falls (ring radii per second squared). */
    static final float DRAG = 2.5f, FALL = 1.6f;
    /** Per spray particle: start x, y and velocity x, y (ring radii, per second), width, length (radii), delay, life (s). */
    private static final float[] SPRAY = new float[36 * 8];
    /** The picker preview holds each banner a second, then leaves a short gap before the next kill count. */
    static final double PREVIEW_HOLD = 1, PREVIEW_GAP = .4;

    static {
        Random random = new Random(1729); // the same spray every time
        for (int i = 0, o = 0; i < 36; i++, o += 8) {
            float side = i % 2 == 0 ? 1 : -1, width = .06f + .07f * random.nextFloat();
            SPRAY[o] = side * .5f * random.nextFloat();
            SPRAY[o + 1] = .1f + .5f * random.nextFloat();
            SPRAY[o + 2] = side * (2f + 4.5f * random.nextFloat());
            SPRAY[o + 3] = -2.2f + 2.6f * random.nextFloat();
            SPRAY[o + 4] = width;
            SPRAY[o + 5] = width * (2.2f + 1.2f * random.nextFloat());
            SPRAY[o + 6] = .15f * random.nextFloat();
            SPRAY[o + 7] = .35f + .4f * random.nextFloat();
        }
    }

    /** iconY: cell pixels the strip's icon sits below its settled place; everything drawn over the banner follows it. */
    public record Frame(int stripFrame, float iconAlpha, float iconScale, float restAlpha, float shadowAlpha,
                        float strobe, float markSize, float markThinAlpha, float markAlpha, int markColor,
                        float heartAlpha, float labelAlpha, float iconY) {
        /** True when the frame comes from the drawn way out (the strip's exit layers), not the strip. */
        public boolean drawnExit() { return stripFrame < 0; }
    }

    /**
     * A still skin (KillBannerStyle.Type.COMPOSITE, PHASEGUARD or BANNER_SWAP) at a moment. Scales are about the ring centre,
     * emblemY is in art pixels (up is negative), pipRadius is in ring radii, angles are degrees clockwise from the top.
     * emblemShade darkens the emblem on the way out; tier is how far a Banner Swap skin has turned from the previous kill's
     * art into this kill's; spray is seconds into the spray ({@link #particle}), negative before it.
     */
    public record Layers(float emblemAlpha, float emblemScale, float emblemY, float emblemShade,
                         float frameAlpha, float frameScale, float ringAlpha, float ringScale,
                         float pipAlpha, float pipScale, float pipRadius, float pipSpin, float pipFlare,
                         float burstAlpha, float burstScale, float glintAngle, float glintAlpha, float tier, float spray,
                         float shadowAlpha, float strobe, float markSize, float markThinAlpha, float markAlpha, int markColor,
                         float labelAlpha) {}

    private KillBannerPlayer() {}

    /** Seconds a still skin's banner for this many kills takes with no hold. */
    public static double stillSeconds(int kills) {
        return (STILL_INTRO[Math.max(1, Math.min(5, kills))] + STILL_EXIT) / 60.0;
    }

    /**
     * A still skin's banner {@code age} seconds after its kill, shown for {@code seconds} in total (it holds settled for what
     * its motion leaves). Null once it is gone. Motion runs on fractional frames, so it is smooth above 60 fps.
     */
    public static Layers layers(KillBannerStyle style, int kills, double age, double seconds, boolean headshot) {
        if (age < 0 || !Double.isFinite(age)) return null;
        int k = Math.max(1, Math.min(5, kills)), intro = STILL_INTRO[k];
        float f = (float) (age * 60);
        int hold = Math.max(0, (int) Math.round(seconds * 60) - intro - STILL_EXIT);
        float e = f - intro - hold;
        if (e >= STILL_EXIT) return null;
        boolean swap = style.type == KillBannerStyle.Type.BANNER_SWAP;

        // The emblem drops in from above, overshooting its size, and sits by frame 9 (both skins' icons), with a glow behind.
        float emblemAlpha = ramp(0, 3, f);
        float emblemScale = f < 3 ? .6f + .48f * out(f / 3) : 1.08f - .08f * smooth(3, 9, f);
        float emblemY = -28 * (1 - out(ramp(1, 9, f)));
        // A Banner Swap skin shows the previous kill's art first and turns into this kill's with a punch as the mark lands.
        float tier = swap && k > 1 ? smooth(9, 13, f) : 1;
        if (swap && k > 1) emblemScale += .1f * (smooth(9, 11, f) - smooth(11, 18, f));
        float burst = .85f * (f < 2 ? ramp(0, 2, f) : 1 - smooth(2, 16, f)), burstScale = .55f + .75f * out(ramp(0, 14, f));
        // The frame zooms in onto it, the ring opens out of it, and the pips fly in flaring.
        float frameAlpha = smooth(8, 13, f), frameScale = 1.18f - .18f * out(ramp(8, 17, f));
        float ringAlpha = smooth(9, 20, f), ringScale = .8f + .2f * out(ramp(9, 19, f));
        float pipIn = out(ramp(9, 16, f));
        float pipAlpha = smooth(9, 12, f), pipScale = 1.7f - .7f * pipIn, pipRadius = 1.25f - .25f * pipIn;
        float pipFlare = .95f * (f < 12 ? ramp(9, 12, f) : 1 - smooth(12, 24, f));
        // From two kills: the spray, then a glint round the ring; an ace's glint runs three times while its pips turn once.
        float spray = SPRAY_COUNT[k] > 0 && f >= SPRAY_FRAME ? (f - SPRAY_FRAME) / 60 : -1;
        float glintAngle = 0, glintAlpha = 0, loop = (f - GLINT_FRAME) / GLINT_LOOP;
        if (loop >= 0 && loop < (k == 5 ? 3 : k > 1 ? 1 : 0)) {
            float p = loop - (int) loop;
            glintAngle = 360 * smooth(0, 1, p);
            glintAlpha = (float) Math.sin(Math.PI * p);
        }
        float pipSpin = k == 5 ? 360 * smooth(120, 190, f) : 0;

        float shade = 1, leaving = 1;
        if (e >= 0) {
            leaving = 1 - smooth(0, STILL_EXIT, e);
            frameAlpha *= 1 - smooth(0, 3, e);
            emblemScale *= 1 - .45f * smooth(0, 11, e);
            emblemAlpha *= 1 - smooth(5, 13, e);
            shade = 1 - .7f * smooth(0, 11, e);
            ringAlpha *= 1 - smooth(9, 13, e);
            pipAlpha *= 1 - smooth(10, 16, e);
        }
        int m = KillBannerStyle.MARK_FRAME, t = (int) Math.floor(f) - m;
        float shadow = .5f * smooth(m - 8, m, f) * leaving;
        float strobe = t >= 0 && t < STROBE.length ? STROBE[t] : 0;
        float size = 0, thin = 0, solid = 0;
        int color = MARK_RED;
        if (t >= 0) {
            size = style.markSize * (1 + 1.4f * (float) Math.exp(-t / 1.5)) * emblemScale;
            float arriving = 1 - smooth(0, 3, t);
            thin = .75f * arriving * emblemAlpha;
            solid = (1 - arriving) * emblemAlpha;
            color = mix(MARK_RED, 0xFFFFFFFF, t > 1 ? smooth(.05f, .45f, strobe) : 1);
        }
        float label = headshot ? smooth(m - 4, m + 4, f) * (e >= 0 ? 1 - smooth(0, 8, e) : 1) : 0;
        return new Layers(emblemAlpha, emblemScale, emblemY, shade, frameAlpha, frameScale, ringAlpha, ringScale,
            pipAlpha, pipScale, pipRadius, pipSpin, pipFlare, burst, burstScale, glintAngle, glintAlpha, tier, spray,
            shadow, strobe * emblemAlpha, size, thin, solid, color, label);
    }

    /** Spray particles a banner for this many kills throws. */
    public static int sprayCount(int kills) {
        return SPRAY_COUNT[Math.max(1, Math.min(5, kills))];
    }

    /**
     * Spray particle {@code i} {@code spray} seconds into the spray, in ring radii from the emblem's centre: {@code out} gets
     * x, y, length, width, alpha and its angle of flight (radians). False while it is not showing.
     */
    public static boolean particle(int i, float spray, float[] out) {
        int o = i * 8;
        float t = spray - SPRAY[o + 6], life = SPRAY[o + 7];
        if (t <= 0 || t >= life) return false;
        float drag = (float) Math.exp(-DRAG * t), travel = (1 - drag) / DRAG, vx = SPRAY[o + 2], vy = SPRAY[o + 3];
        out[0] = SPRAY[o] + vx * travel;
        out[1] = SPRAY[o + 1] + vy * travel + .5f * FALL * t * t;
        out[2] = SPRAY[o + 5];
        out[3] = SPRAY[o + 4];
        out[4] = Math.min(1, t / .04f) * (1 - smooth(.6f * life, life, t));
        out[5] = (float) Math.atan2(vy * drag + FALL * t, vx * drag);
        return true;
    }

    /** The picker preview at {@code clock} seconds: which kill count it shows (1 to 5 in turn, then again). */
    public static int previewKills(double clock) {
        return (int) preview(clock, true);
    }

    /** The picker preview at {@code clock} seconds: how far into its banner it is (past its duration: the gap, nothing shown). */
    public static double previewAge(double clock) {
        return preview(clock, false);
    }

    /** How long the picker preview shows a kill count's banner: its motion and a second's hold. */
    public static double previewSeconds(int kills) {
        return stillSeconds(kills) + PREVIEW_HOLD;
    }

    private static double preview(double clock, boolean kills) {
        double cycle = 0;
        for (int k = 1; k <= 5; k++) cycle += previewSeconds(k) + PREVIEW_GAP;
        double t = ((clock % cycle) + cycle) % cycle;
        for (int k = 1; k < 5; k++) {
            double length = previewSeconds(k) + PREVIEW_GAP;
            if (t < length) return kills ? k : t;
            t -= length;
        }
        return kills ? 5 : t;
    }

    /** Seconds the banner takes with no hold: its frames up to the settled one, and the way out. */
    public static double minimumSeconds(KillBannerStrip strip) {
        if (strip == null) return 25.0 / 60.0;
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
        if (strip == null) {
            int intro = 12, exit = 12;
            int hold = Math.max(0, (int) Math.round(seconds * 60) - intro - exit);
            int e = f - intro - hold;
            if (e >= exit) return null;
            float iconAlpha = 1, iconScale = 1, restAlpha = 1, leaving = 1;
            if (e >= 0) {
                leaving = 1 - smooth(0, exit, e);
                iconAlpha = leaving;
                restAlpha = leaving;
            } else if (f < 6) {
                iconScale = 0.88f + 0.12f * smooth(0, 6, f);
            }
            int m = KillBannerStyle.MARK_FRAME, t = f - m;
            float shadow = .5f * smooth(m - 8, m, f) * leaving;
            float strobe = t >= 0 && t < STROBE.length ? STROBE[t] : 0;
            float size = 0, thin = 0, solid = 0;
            int color = MARK_RED;
            if (t >= 0) {
                size = style.markSize * (1 + 1.4f * (float) Math.exp(-t / 1.5)) * iconScale;
                float arriving = 1 - smooth(0, 3, t);
                thin = .75f * arriving * iconAlpha;
                solid = (1 - arriving) * iconAlpha;
                float white = t > 1 ? smooth(.05f, .45f, strobe) : 1;
                color = mix(MARK_RED, 0xFFFFFFFF, white);
            }
            float label = headshot ? smooth(m - 4, m + 4, f) * (e >= 0 ? 1 - smooth(0, 8, e) : 1) : 0;
            return new Frame(-1, iconAlpha, iconScale, restAlpha, shadow, strobe * iconAlpha, size, thin, solid,
                color, 0, label, 0);
        }
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

    /** Linear from 0 at {@code from} to 1 at {@code to}. */
    static float ramp(float from, float to, float x) {
        return Math.max(0, Math.min(1, (x - from) / (to - from)));
    }

    /** Ease-out cubic of 0 to 1: fast, then settling. */
    static float out(float x) {
        float r = 1 - Math.max(0, Math.min(1, x));
        return 1 - r * r * r;
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
