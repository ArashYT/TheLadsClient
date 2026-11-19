package com.thelads.core.client.ladsapi.minigame;

import com.google.gson.JsonObject;

/**
 * Screen effects (PROTOCOL.md section 12). Visual only. The version hook draws {@link #shader}, {@link #vignette}, {@link #tint}
 * and {@link #flash} each frame as a full-screen overlay (rgb + alpha 0-1) and skips a shader it cannot do. Game thread only.
 */
public final class FxState {
    public enum Shader { NONE, BLUR, GRAYSCALE, SEPIA, INVERT }

    /** A colour at an opacity 0-1. */
    public record Overlay(int rgb, double alpha) {}

    /** A lasting tint never covers the screen fully. */
    public static final double TINT_MAX_ALPHA = 0.8;

    private Shader shader = Shader.NONE;
    private boolean hasVignette, hasTint, lasting;
    private int vignetteRgb, tintRgb;
    private double vignetteIntensity, tintIntensity;
    private int pulseMs;
    private long start;
    private int ms, fadeMs;
    private boolean flashOn;
    private int flashRgb, flashMs;
    private long flashStart;

    public void clear() {
        lasting = false;
        flashOn = false;
        hasVignette = hasTint = false;
        shader = Shader.NONE;
    }

    public void apply(JsonObject json, long now) {
        Double msv = Wire.num(json, "ms");
        boolean hasFlash = Wire.obj(json, "flash") != null;
        if (msv == null && !hasFlash && json.size() <= 1) { // {"t":"fx"}: clear at once
            clear();
            return;
        }
        boolean reduce = Hooks.reduceFlashes.getAsBoolean();
        lasting = false;
        hasVignette = hasTint = false;
        shader = Shader.NONE;
        if (msv != null) {
            ms = (int) Math.round(Math.max(1, Math.min(60000, msv)));
            fadeMs = (int) Math.min(Wire.num(json, "fade_ms", 0, 5000, 0), ms / 2);
            start = now;
            lasting = true;
            String sh = Wire.str(json, "shader");
            if (sh != null) {
                switch (sh) {
                    case "blur" -> shader = Shader.BLUR;
                    case "grayscale" -> shader = Shader.GRAYSCALE;
                    case "sepia" -> shader = Shader.SEPIA;
                    case "invert" -> shader = Shader.INVERT;
                    default -> shader = Shader.NONE;
                }
            }
            JsonObject v = Wire.obj(json, "vignette");
            if (v != null) {
                hasVignette = true;
                vignetteRgb = Wire.color(v, "color", 0x000000);
                vignetteIntensity = Wire.num(v, "intensity", 0, 1, 0.5);
                pulseMs = reduce ? 0 : Wire.whole(v, "pulse_ms", 0, 10000, 0);
            }
            JsonObject t = Wire.obj(json, "tint");
            if (t != null) {
                hasTint = true;
                tintRgb = Wire.color(t, "color", 0x000000);
                tintIntensity = Wire.num(t, "intensity", 0, 1, 0.3);
            }
        }
        JsonObject f = Wire.obj(json, "flash");
        if (f != null && !reduce) {
            flashOn = true;
            flashRgb = Wire.color(f, "color", 0xFFFFFF);
            flashMs = Wire.whole(f, "ms", 1, 2000, 300);
            flashStart = now;
        }
    }

    private boolean live(long now) {
        return lasting && now - start >= 0 && now - start < ms;
    }

    /** Fade factor 0-1 of the lasting effects: in over fade_ms, out over the last fade_ms. */
    public double fadeAt(long now) {
        if (!live(now)) return 0;
        if (fadeMs <= 0) return 1;
        long t = now - start;
        return Math.max(0, Math.min(1, Math.min(t / (double) fadeMs, (ms - t) / (double) fadeMs)));
    }

    public Shader shader(long now) {
        return live(now) ? shader : Shader.NONE;
    }

    /** Steady: intensity. Pulsing: intensity swings between 50 % and 100 % of itself. Times the fade. */
    public Overlay vignette(long now) {
        if (!live(now) || !hasVignette) return null;
        double a = vignetteIntensity;
        if (pulseMs > 0) a *= 0.75 + 0.25 * Math.cos(2 * Math.PI * ((now - start) % pulseMs) / pulseMs);
        return new Overlay(vignetteRgb, a * fadeAt(now));
    }

    public Overlay tint(long now) {
        if (!live(now) || !hasTint) return null;
        return new Overlay(tintRgb, Math.min(tintIntensity, TINT_MAX_ALPHA) * fadeAt(now));
    }

    /** Full at receipt, linear to nothing after its ms. */
    public Overlay flash(long now) {
        if (!flashOn) return null;
        long t = now - flashStart;
        if (t < 0 || t >= flashMs) return null;
        return new Overlay(flashRgb, 1.0 - t / (double) flashMs);
    }
}
