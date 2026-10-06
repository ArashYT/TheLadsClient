package com.thelads.core.client.killbanner;

import java.io.IOException;
import java.io.InputStream;
import java.util.EnumMap;
import java.util.Map;
import java.util.Properties;

/**
 * A kill banner's motion measured frame by frame: one value a frame for each layer channel, frames 0..introEnd, then the way out.
 * Every still skin has its own, measured from its Valorant preview video (tools/killbanner/measure_video.py,
 * killbanner/motion/&lt;skin&gt;.properties); Rogue's, measured from its strips (tools/killbanner/gen_template.py,
 * killbanner/template.properties), stands in for a kill count or a skin without one.
 */
public final class KillBannerTemplate {
    private static final String DIR = "/assets/theladscore/killbanner/";
    private static final KillBannerTemplate[] SHARED = load(DIR + "template.properties", true);
    /** Each still skin's own templates by kill count (null slots fall back to the shared ones); a skin with none maps to an empty array. */
    private static final Map<KillBannerStyle, KillBannerTemplate[]> OWN = new EnumMap<>(KillBannerStyle.class);
    /** The HEADSHOT label's box colour (RGB) a skin's preview showed ("headshot.box" in its motion file). */
    private static final Map<KillBannerStyle, Integer> BOX = new EnumMap<>(KillBannerStyle.class);
    private static final KillBannerTemplate[] NONE = new KillBannerTemplate[0];

    /** Frames 0..introEnd play and introEnd holds; the next {@code exit} frames play the way out. */
    public final int introEnd, exit;
    /** The frame the kill mark lands and the red strobe starts. */
    public final int mark;
    /** The frame droplets first fly and how many; count 0: none for this kill count. */
    public final int sprayStart, sprayCount;
    /** Where the pips settle: a multiple of the skin's pip radius (its Kingdom Archives layout). */
    public final float orbit;
    /** The pips' settled angles, degrees clockwise from the top on screen; null: the usual spacing. */
    public final float[] pipAngles;
    /** The emblem: opacity, size about the ring centre, Rogue cell pixels below its settled place, brightness. */
    public final float[] iconAlpha, iconScale, iconY, iconShade;
    public final float[] ringAlpha, ringScale, frameAlpha, frameScale;
    /** The kill pips: opacity, orbit radius (1 = settled), arrival glow (0..1), degrees turned clockwise. */
    public final float[] pipAlpha, pipRadius, pipFlare, pipSpin;

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

    /** A motion file's properties; null when there is none (and it is not required). */
    private static Properties read(String path, boolean required) {
        Properties data = new Properties();
        try (InputStream in = KillBannerTemplate.class.getResourceAsStream(path)) {
            if (in == null) {
                if (required) throw new IOException(path + " is missing");
                return null;
            }
            data.load(in);
        } catch (IOException failure) {
            throw new IllegalStateException("Kill banner motion template unavailable: " + path, failure);
        }
        return data;
    }

    /** The five kill counts' templates of a file; a missing kill count is a null slot. The shared file must have all five. */
    private static KillBannerTemplate[] load(String path, boolean required) {
        Properties data = read(path, required);
        if (data == null) return NONE;
        return parse(data, path, required);
    }

    private static KillBannerTemplate[] parse(Properties data, String path, boolean required) {
        float orbit = Float.parseFloat(data.getProperty("orbit", "1").trim());
        KillBannerTemplate[] out = new KillBannerTemplate[5];
        for (int kills = 1; kills <= 5; kills++) {
            if (data.getProperty("k" + kills + ".introEnd") == null) {
                if (required) throw new IllegalStateException(path + " has no " + kills + "-kill banner");
                continue;
            }
            out[kills - 1] = new KillBannerTemplate(data, "k" + kills + ".", orbit);
        }
        return out;
    }

    /** Rogue's motion for this many kills (1 to 5). */
    public static KillBannerTemplate of(int kills) {
        return SHARED[Math.max(1, Math.min(5, kills)) - 1];
    }

    /** The skin's own motion for this many kills, or Rogue's where it has none. */
    public static KillBannerTemplate of(KillBannerStyle style, int kills) {
        int k = Math.max(1, Math.min(5, kills)) - 1;
        if (style == null || style.isAnimated()) return SHARED[k];
        KillBannerTemplate[] own = own(style);
        return own.length > k && own[k] != null ? own[k] : SHARED[k];
    }

    /** The skin's motion file, read once: its templates by kill count (and its HEADSHOT box colour into BOX). */
    private static KillBannerTemplate[] own(KillBannerStyle style) {
        synchronized (OWN) {
            KillBannerTemplate[] own = OWN.get(style);
            if (own == null) {
                String path = DIR + "motion/" + style.id + ".properties";
                Properties data = read(path, false);
                own = data == null ? NONE : parse(data, path, false);
                OWN.put(style, own);
                String box = data == null ? null : data.getProperty("headshot.box");
                if (box != null && !box.isBlank()) {
                    try { BOX.put(style, Integer.parseInt(box.trim(), 16) & 0xFFFFFF); } catch (NumberFormatException ignored) {}
                }
            }
            return own;
        }
    }

    /** The HEADSHOT label's box colour (RGB) the skin's preview showed, or -1 when its preview showed no headshot. */
    public static int headshotBox(KillBannerStyle style) {
        if (style == null || style.isAnimated()) return -1;
        own(style);
        synchronized (OWN) {
            return BOX.getOrDefault(style, -1);
        }
    }

    /** True when the skin has motion of its own (measured from its preview). */
    public static boolean hasOwn(KillBannerStyle style) {
        return style != null && !style.isAnimated() && of(style, 1) != SHARED[0];
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
