package com.thelads.core.modules;

import com.thelads.core.client.PvpVisuals;
import com.thelads.core.client.bridge.LadsGameBridge;
import com.thelads.core.client.bridge.LadsGraphics;
import com.thelads.core.client.sound.CustomSounds;
import com.thelads.core.config.BoolOption;
import com.thelads.core.config.ColorOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.SliderOption;
import javax.sound.sampled.AudioFormat;

/**
 * Damage Tint (Lunar, Badlion): below a health threshold the screen edges glow in the tint colour, stronger the less health is
 * left, and a heartbeat plays, faster as health drops. Drawn by HudManager with the Lads HUD on every version (F1 hides it, as it
 * hides Minecraft's own vignette); the heartbeat is made here and played through CustomSounds, so 1.8.9 has it too.
 */
public final class DamageTintModule extends Module {
    public static final String NAME = "Damage Tint";
    /** Bands of the edge glow, each a little fainter than the one outside it. */
    static final int BANDS = 12;
    /** Just under the threshold the glow already shows this share of the colour's alpha, so the warning is visible at once. */
    static final float START = 0.3F;
    static final int RATE = 22050;
    private static final CustomSounds.Pcm HEARTBEAT = new CustomSounds.Pcm(new AudioFormat(RATE, 16, 1, true, false), heartbeat(RATE));

    public final SliderOption hearts = addOption(new SliderOption("Below Hearts", 4, 1, 10, 0.5));
    public final ColorOption color = addOption(new ColorOption("Color", false, 0x99FF0000));
    public final BoolOption heartbeat = addOption(new BoolOption("Heartbeat", true));
    public final SliderOption volume = addOption(new SliderOption("Volume", 50, 0, 100, 5).percent());

    /** Cached each tick. */
    public boolean on, beat;
    public float threshold = 8;
    public int argb = 0x99FF0000;
    public float gain = .5F;
    private long nextBeat;

    public DamageTintModule() {
        super(NAME, "Red screen edges and a heartbeat while your health is low.");
    }

    public void refresh() {
        on = isEnabled();
        threshold = (float) hearts.getValue() * 2;
        argb = PvpVisuals.argb(color);
        beat = heartbeat.get();
        gain = (float) volume.getValue() / 100;
    }

    /** Once a frame, from HudManager.render: the edge glow and the heartbeat. */
    public void render(LadsGraphics g) {
        LadsGameBridge game = g.getGame();
        float strength = on && game != null && game.isIngame() && game.hasPlayer() ? intensity(game.getHealth(), threshold) : 0;
        if (strength <= 0) {
            nextBeat = 0;
            return;
        }
        int w = g.getScaledWidth(), h = g.getScaledHeight(), band = Math.max(1, Math.min(w, h) / 4 / BANDS);
        for (int i = 0; i < BANDS; i++) {
            int color = bandColor(argb, strength, i), in = i * band, out = in + band;
            g.fill(0, in, w, out, color);
            g.fill(0, h - out, w, h - in, color);
            g.fill(in, out, out, h - out, color);
            g.fill(w - out, out, w - in, h - out, color);
        }
        long now = System.currentTimeMillis();
        if (beat && now >= nextBeat) {
            CustomSounds.play(HEARTBEAT, gain, "Damage Tint heartbeat");
            nextBeat = now + beatMillis(strength);
        }
    }

    /** 0 at the threshold and above (and when dead), else from {@link #START} just under it up to 1 at no health. */
    static float intensity(float health, float threshold) {
        if (health <= 0 || health >= threshold) return 0;
        return START + (1 - START) * (1 - health / threshold);
    }

    /** A band's colour: the tint's alpha times the strength, fading to nothing towards the middle of the screen. */
    static int bandColor(int argb, float strength, int band) {
        int alpha = Math.round((argb >>> 24) * strength * (BANDS - band) / BANDS); // the corners, where two bands overlap, are darker
        return alpha << 24 | argb & 0xFFFFFF;
    }

    /** Time to the next heartbeat: 1.1 s just under the threshold, 0.5 s near death. */
    static long beatMillis(float strength) {
        return 1100 - Math.round(600 * (strength - START) / (1 - START));
    }

    /**
     * A heartbeat: two low thumps ("lub-dub", the second softer), signed 16-bit little-endian mono. Each thump is 60 Hz with
     * two overtones (so small speakers play it) under a quick attack and an exponential decay.
     */
    static byte[] heartbeat(int rate) {
        int frames = rate * 450 / 1000;
        byte[] pcm = new byte[frames * 2];
        for (int i = 0; i < frames; i++) {
            double t = (double) i / rate, s = thump(t, 1) + thump(t - 0.2, 0.7);
            int v = (int) Math.round(Math.max(-1, Math.min(1, s)) * 0.8 * Short.MAX_VALUE);
            pcm[2 * i] = (byte) v;
            pcm[2 * i + 1] = (byte) (v >> 8);
        }
        return pcm;
    }

    private static double thump(double t, double amplitude) {
        if (t < 0 || t > 0.18) return 0;
        double envelope = Math.min(1, t / 0.005) * Math.exp(-t / 0.035);
        double wave = Math.sin(2 * Math.PI * 60 * t) + 0.5 * Math.sin(2 * Math.PI * 120 * t) + 0.25 * Math.sin(2 * Math.PI * 180 * t);
        return amplitude * envelope * wave / 1.75;
    }
}
