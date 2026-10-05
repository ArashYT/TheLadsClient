package com.thelads.core.client.killbanner;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Rogue's banner motion, measured frame by frame from its strips (tools/killbanner/gen_template.py, killbanner/template.properties),
 * which every still skin plays with its own art: one value a frame for each layer channel, frames 0..introEnd, then the way out.
 */
public final class KillBannerTemplate {
    private static final KillBannerTemplate[] BY_KILLS = new KillBannerTemplate[5];

    static {
        Properties data = new Properties();
        try (InputStream in = KillBannerTemplate.class.getResourceAsStream("/assets/theladscore/killbanner/template.properties")) {
            if (in == null) throw new IOException("killbanner/template.properties is missing");
            data.load(in);
        } catch (IOException failure) {
            throw new IllegalStateException("Kill banner motion template unavailable", failure);
        }
        for (int kills = 1; kills <= 5; kills++) BY_KILLS[kills - 1] = new KillBannerTemplate(data, "k" + kills + ".");
    }

    /** Frames 0..introEnd play and introEnd holds; the next {@code exit} frames play the way out. */
    public final int introEnd, exit;
    /** The frame droplets first fly and how many; count 0: none for this kill count. */
    public final int sprayStart, sprayCount;
    /** The emblem: opacity, size about the ring centre, Rogue cell pixels below its settled place, brightness. */
    public final float[] iconAlpha, iconScale, iconY, iconShade;
    public final float[] ringAlpha, ringScale, frameAlpha, frameScale;
    /** The kill pips: opacity, orbit radius (1 = settled), arrival glow (0..1), degrees turned clockwise. */
    public final float[] pipAlpha, pipRadius, pipFlare, pipSpin;

    private KillBannerTemplate(Properties data, String prefix) {
        introEnd = Integer.parseInt(data.getProperty(prefix + "introEnd").trim());
        exit = Integer.parseInt(data.getProperty(prefix + "exit").trim());
        String[] spray = data.getProperty(prefix + "spray", "0,0").split(",");
        sprayStart = Integer.parseInt(spray[0].trim());
        sprayCount = Integer.parseInt(spray[1].trim());
        iconAlpha = channel(data, prefix + "icon.alpha");
        iconScale = channel(data, prefix + "icon.scale");
        iconY = channel(data, prefix + "icon.y");
        iconShade = channel(data, prefix + "icon.shade");
        ringAlpha = channel(data, prefix + "ring.alpha");
        ringScale = channel(data, prefix + "ring.scale");
        frameAlpha = channel(data, prefix + "frame.alpha");
        frameScale = channel(data, prefix + "frame.scale");
        pipAlpha = channel(data, prefix + "pip.alpha");
        pipRadius = channel(data, prefix + "pip.radius");
        pipFlare = channel(data, prefix + "pip.flare");
        pipSpin = channel(data, prefix + "pip.spin");
    }

    private float[] channel(Properties data, String key) {
        String[] values = data.getProperty(key, "").split(",");
        float[] out = new float[values.length];
        for (int i = 0; i < values.length; i++) out[i] = Float.parseFloat(values[i].trim());
        if (out.length != introEnd + 1 + exit) throw new IllegalStateException(key + ": " + out.length + " values for " + (introEnd + 1 + exit) + " frames");
        return out;
    }

    /** The motion for this many kills (1 to 5). */
    public static KillBannerTemplate of(int kills) {
        return BY_KILLS[Math.max(1, Math.min(5, kills)) - 1];
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
