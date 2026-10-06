package com.thelads.core.client.killbanner;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * The kill banner's motion, frame by frame: one value a frame for each layer channel, frames 0..introEnd, then the way out.
 * It is Valorant's own animation (KillBanner_Base, KillBanner_Wheel and KillBanner_PieSlice, with their Blueprint timers)
 * evaluated at 60 fps by tools/killbanner/game_template.py into killbanner/template.properties; every still skin plays
 * it, since in the game they all share it and differ only in art, colour and pip radius.
 */
public final class KillBannerTemplate {
    private static final String DIR = "/assets/theladscore/killbanner/";
    private static final KillBannerTemplate[] SHARED = load(DIR + "template.properties");

    /** Frames 0..introEnd play and introEnd holds; the next {@code exit} frames play the way out. */
    public final int introEnd, exit;
    /** The frame the kill mark lands and the headshot flicker starts (the game's slices-and-FX event). */
    public final int mark;
    /** The frame droplets first fly and how many; count 0: none for this kill count. */
    public final int sprayStart, sprayCount;
    /** Where the pips settle: a multiple of the skin's pip radius (its Kingdom Archives layout, the game's slice radius). */
    public final float orbit;
    /** The pips' settled angles, degrees clockwise from the top on screen; null: the usual spacing. */
    public final float[] pipAngles;
    /** The emblem: opacity, size about the ring centre, Rogue cell pixels below its settled place, brightness. */
    public final float[] iconAlpha, iconScale, iconY, iconShade;
    public final float[] ringAlpha, ringScale, frameAlpha, frameScale;
    /**
     * The kill pips: the wheel's opacity, the Up texture's opacity, the pip's size, art pixels outward from its place,
     * the hover texture's opacity (its arrival glow), degrees turned clockwise.
     */
    public final float[] pipAlpha, pipUp, pipScale, pipRadius, pipFlare, pipSpin;

    private KillBannerTemplate(Properties data, String prefix, float orbit) {
        introEnd = Integer.parseInt(data.getProperty(prefix + "introEnd").trim());
        exit = Integer.parseInt(data.getProperty(prefix + "exit").trim());
        mark = Integer.parseInt(data.getProperty(prefix + "mark", String.valueOf(KillBannerStyle.MARK_FRAME)).trim());
        String[] spray = data.getProperty(prefix + "spray", "0,0").split(",");
        sprayStart = Integer.parseInt(spray[0].trim());
        sprayCount = Integer.parseInt(spray[1].trim());
        this.orbit = orbit;
        String angles = data.getProperty(prefix + "pip.angles");
        pipAngles = angles == null || angles.isBlank() ? null : floats(angles);
        iconAlpha = channel(data, prefix + "icon.alpha");
        iconScale = channel(data, prefix + "icon.scale");
        iconY = channel(data, prefix + "icon.y");
        iconShade = channel(data, prefix + "icon.shade");
        ringAlpha = channel(data, prefix + "ring.alpha");
        ringScale = channel(data, prefix + "ring.scale");
        frameAlpha = channel(data, prefix + "frame.alpha");
        frameScale = channel(data, prefix + "frame.scale");
        pipAlpha = channel(data, prefix + "pip.alpha");
        pipUp = channel(data, prefix + "pip.up");
        pipScale = channel(data, prefix + "pip.scale");
        pipRadius = channel(data, prefix + "pip.radius");
        pipFlare = channel(data, prefix + "pip.flare");
        pipSpin = channel(data, prefix + "pip.spin");
    }

    private static float[] floats(String values) {
        String[] parts = values.split(",");
        float[] out = new float[parts.length];
        for (int i = 0; i < parts.length; i++) out[i] = Float.parseFloat(parts[i].trim());
        return out;
    }

    private float[] channel(Properties data, String key) {
        float[] out = floats(data.getProperty(key, ""));
        if (out.length != introEnd + 1 + exit) throw new IllegalStateException(key + ": " + out.length + " values for " + (introEnd + 1 + exit) + " frames");
        return out;
    }

    /** The five kill counts' templates of the motion file. */
    private static KillBannerTemplate[] load(String path) {
        Properties data = new Properties();
        try (InputStream in = KillBannerTemplate.class.getResourceAsStream(path)) {
            if (in == null) throw new IOException(path + " is missing");
            data.load(in);
        } catch (IOException failure) {
            throw new IllegalStateException("Kill banner motion template unavailable: " + path, failure);
        }
        float orbit = Float.parseFloat(data.getProperty("orbit", "1").trim());
        KillBannerTemplate[] out = new KillBannerTemplate[5];
        for (int kills = 1; kills <= 5; kills++) {
            if (data.getProperty("k" + kills + ".introEnd") == null) throw new IllegalStateException(path + " has no " + kills + "-kill banner");
            out[kills - 1] = new KillBannerTemplate(data, "k" + kills + ".", orbit);
        }
        return out;
    }

    /** The game's motion for this many kills (1 to 5; more is the ace). */
    public static KillBannerTemplate of(int kills) {
        return SHARED[Math.max(1, Math.min(5, kills)) - 1];
    }

    /** The same for every skin: the game's motion for this many kills. */
    public static KillBannerTemplate of(KillBannerStyle style, int kills) {
        return of(kills);
    }

    /** Seconds the banner takes with no hold: its frames up to the settled one, and the way out. */
    public double seconds() {
        return (introEnd + 1 + exit) / 60.0;
    }

    /** A channel's value at a fractional frame (between frames it moves linearly, so it is smooth above 60 fps). */
    public static float at(float[] channel, float frame) {
        if (frame <= 0) return channel[0];
        int i = (int) frame;
        if (i >= channel.length - 1) return channel[channel.length - 1];
        float t = frame - i;
        return channel[i] + (channel[i + 1] - channel[i]) * t;
    }
}
