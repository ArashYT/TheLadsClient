package com.thelads.core.client.killbanner;

import java.util.Random;

/**
 * What a skin banner draws at a moment, from Valorant footage: the skin's frames, a dark backdrop, the kill mark
 * (a lattice that lands large and shrinks onto the icon), the icon's red strobe, the emblem's red cavity (Reaver) and
 * the HEADSHOT label. Reaver and Rogue play their measured frames ({@link #at}); the Kingdom Archives skins, which are
 * still layers, move those layers as their own Valorant preview shows ({@link #layers}, {@link KillBannerTemplate}).
 * Pure timing; the adapters draw it.
 */
public final class KillBannerPlayer {
    /** The icon's red strobe per frame from MARK_FRAME: four pulses 6 frames (100 ms) apart, measured in both skins. */
    static final float[] STROBE = {.85f, .27f, .01f, .06f, .38f, 1f, .79f, .25f, .01f, .06f, .40f, 1f, .79f, .27f, .02f,
        .05f, .39f, .97f, .66f, .22f};
    /** Reaver's frames stop settled; its way out is drawn: the icon goes (14 frames), the frame stays, then fades. */
    static final int ICON_OUT = 14, FRAME_HOLD = 38, DRAWN_EXIT = 54;
    public static final int MARK_RED = 0xFFC41626, STROBE_RED = 0xFFE2122C;
    /** The pips' arrival glow at its peak: Rogue's pips give about a sixth more light then, as a soft halo. */
    static final float FLARE = .6f;
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
     * A still skin (KillBannerStyle.Type.COMPOSITE, PHASEGUARD or BANNER_SWAP) at a moment: its measured motion on its layers.
     * Scales are about the ring centre, emblemY is in art pixels (up is negative). The pips sit at pipOrbit times the skin's
     * pip radius, times pipRadius as they slide in, each at pipDegrees[i] in the adapters' angle (0 is the top, running
     * anticlockwise on screen), their turn included; pipSpin is that turn alone. emblemShade darkens the emblem on the way out;
     * pipFlare is the glow behind each pip as it arrives; tier is how far a Banner Swap skin has turned from the previous kill's
     * art into this kill's; spray is seconds into the spray ({@link #particle}), negative before it.
     */
    public record Layers(float emblemAlpha, float emblemScale, float emblemY, float emblemShade,
                         float frameAlpha, float frameScale, float ringAlpha, float ringScale,
                         float pipAlpha, float pipRadius, float pipOrbit, float[] pipDegrees, float pipSpin, float pipFlare, float tier, float spray,
                         float shadowAlpha, float strobe, float markSize, float markThinAlpha, float markAlpha, int markColor,
                         float labelAlpha) {}

    private KillBannerPlayer() {}

    /** Seconds a still skin's banner for this many kills takes with no hold. */
    public static double stillSeconds(KillBannerStyle style, int kills) {
        return KillBannerTemplate.of(style, kills).seconds();
    }

    /**
     * A still skin's banner {@code age} seconds after its kill, shown for {@code seconds} in total (it holds settled for what
     * its motion leaves). Null once it is gone. Motion runs on fractional frames, so it is smooth above 60 fps.
     */
    public static Layers layers(KillBannerStyle style, int kills, double age, double seconds, boolean headshot) {
        return layers(style, kills, age, seconds, headshot, -1);
    }

    /** As above; {@code cut} seconds after its kill the next kill cut the banner short, so it leaves from then on (negative: it was not). */
    public static Layers layers(KillBannerStyle style, int kills, double age, double seconds, boolean headshot, double cut) {
        if (age < 0 || !Double.isFinite(age)) return null;
        int k = Math.max(1, Math.min(5, kills));
        KillBannerTemplate t = KillBannerTemplate.of(style, k);
        float f = (float) (age * 60);
        int intro = t.introEnd + 1, exit = t.exit;
        int hold = Math.max(0, (int) Math.round(seconds * 60) - intro - exit);
        float e = f - intro - hold;
        if (cut >= 0 && cut * 60 < intro + hold) e = f - (float) (cut * 60); // cut short by the next kill: it leaves from the cut on
        if (e >= exit) return null;
        // Which measured frame this moment plays: the intro up to the settled one, which holds, then the way out.
        float tf = e < 0 ? Math.min(f, t.introEnd) : t.introEnd + 1 + e;

        float emblemAlpha = KillBannerTemplate.at(t.iconAlpha, tf), emblemScale = KillBannerTemplate.at(t.iconScale, tf);
        float emblemY = KillBannerTemplate.at(t.iconY, tf) / KillBannerStyle.ART_SCALE, shade = KillBannerTemplate.at(t.iconShade, tf);
        // A Banner Swap skin has one picture a kill count and no pips: it shows the previous kill's and turns into this
        // kill's as the mark lands.
        float tier = style.type == KillBannerStyle.Type.BANNER_SWAP && k > 1 ? smooth(9, 13, f) : 1;
        float frameAlpha = KillBannerTemplate.at(t.frameAlpha, tf), frameScale = KillBannerTemplate.at(t.frameScale, tf);
        float ringAlpha = KillBannerTemplate.at(t.ringAlpha, tf), ringScale = KillBannerTemplate.at(t.ringScale, tf);
        float pipAlpha = KillBannerTemplate.at(t.pipAlpha, tf), pipRadius = KillBannerTemplate.at(t.pipRadius, tf);
        float pipFlare = FLARE * KillBannerTemplate.at(t.pipFlare, tf), pipSpin = -KillBannerTemplate.at(t.pipSpin, tf);
        int count = Math.max(1, Math.min(6, kills));
        float[] pipDegrees = new float[count];
        for (int i = 0; i < count; i++) {
            float settled = t.pipAngles != null && i < t.pipAngles.length ? -t.pipAngles[i] : 360f / count * (i + 1) + (count == 2 ? 90 : 0);
            pipDegrees[i] = settled + pipSpin;
        }
        float spray = t.sprayCount > 0 && f >= t.sprayStart ? (f - t.sprayStart) / 60 : -1;

        float leaving = e >= 0 ? 1 - smooth(0, exit, e) : 1;
        int m = t.mark, mt = (int) Math.floor(f) - m;
        float shadow = .5f * smooth(m - 8, m, f) * leaving;
        float strobe = mt >= 0 && mt < STROBE.length ? STROBE[mt] : 0;
        float size = 0, thin = 0, solid = 0;
        int color = MARK_RED;
        if (mt >= 0) {
            size = style.markSize * (1 + 1.4f * (float) Math.exp(-mt / 1.5)) * emblemScale;
            float arriving = 1 - smooth(0, 3, mt);
            thin = .75f * arriving * emblemAlpha;
            solid = (1 - arriving) * emblemAlpha;
            color = mix(MARK_RED, 0xFFFFFFFF, mt > 1 ? smooth(.05f, .45f, strobe) : 1);
        }
        float label = headshot ? smooth(m - 4, m + 4, f) * (e >= 0 ? 1 - smooth(0, 8, e) : 1) : 0;
        return new Layers(emblemAlpha, emblemScale, emblemY, shade, frameAlpha, frameScale, ringAlpha, ringScale,
            pipAlpha, pipRadius, t.orbit, pipDegrees, pipSpin, pipFlare, tier, spray,
            shadow, strobe * emblemAlpha, size, thin, solid, color, label);
    }

    /** Spray particles a banner for this many kills throws (droplets: none for one kill). */
    public static int sprayCount(KillBannerStyle style, int kills) {
        return Math.min(36, KillBannerTemplate.of(style, kills).sprayCount);
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
    public static int previewKills(KillBannerStyle style, double clock) {
        return (int) preview(style, clock, true);
    }

    /** The picker preview at {@code clock} seconds: how far into its banner it is (past its duration: the gap, nothing shown). */
    public static double previewAge(KillBannerStyle style, double clock) {
        return preview(style, clock, false);
    }

    /** How long the picker preview shows a kill count's banner: its motion and a second's hold. */
    public static double previewSeconds(KillBannerStyle style, int kills) {
        return stillSeconds(style, kills) + PREVIEW_HOLD;
    }

    private static double preview(KillBannerStyle style, double clock, boolean kills) {
        double cycle = 0;
        for (int k = 1; k <= 5; k++) cycle += previewSeconds(style, k) + PREVIEW_GAP;
        double t = ((clock % cycle) + cycle) % cycle;
        for (int k = 1; k < 5; k++) {
            double length = previewSeconds(style, k) + PREVIEW_GAP;
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
        return at(style, strip, age, seconds, headshot, -1);
    }

    /** As above; {@code cut} seconds after its kill the next kill cut the banner short, so it leaves from then on (negative: it was not). */
    public static Frame at(KillBannerStyle style, KillBannerStrip strip, double age, double seconds, boolean headshot, double cut) {
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
        if (cut >= 0 && cut * 60 < intro + hold) e = f - (int) Math.round(cut * 60); // cut short by the next kill: it leaves from the cut on
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
