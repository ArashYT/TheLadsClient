package com.thelads.ladsapi.api;

import com.google.gson.JsonObject;
import com.thelads.ladsapi.session.Wire;
import java.util.List;

/** Screen effects (PROTOCOL.md section 12). {@code ms} is how long the lasting effects stay (1..60000). */
public final class ScreenFx {
    private int ms;
    private int fadeMs;
    private String shader;
    private boolean vig;
    private String vigColor;
    private double vigIntensity;
    private int vigPulse;
    private boolean tint;
    private String tintColor;
    private double tintIntensity;
    private boolean flash;
    private String flashColor;
    private int flashMs;

    private ScreenFx() {}

    public static ScreenFx create() { return new ScreenFx(); }

    public ScreenFx ms(int ms) { this.ms = ms; return this; }
    public ScreenFx fadeMs(int fadeMs) { this.fadeMs = fadeMs; return this; }
    /** none, blur, grayscale, sepia or invert; anything else is left out. */
    public ScreenFx shader(String shader) { this.shader = shader; return this; }
    public ScreenFx vignette(String color, double intensity, int pulseMs) {
        vig = true; vigColor = color; vigIntensity = intensity; vigPulse = pulseMs;
        return this;
    }
    public ScreenFx tint(String color, double intensity) {
        tint = true; tintColor = color; tintIntensity = intensity;
        return this;
    }
    public ScreenFx flash(String color, int ms) {
        flash = true; flashColor = color; flashMs = ms;
        return this;
    }

    /** The fx message with every value clamped; null when it would carry nothing. */
    public JsonObject toJson() {
        JsonObject o = Wire.obj("fx");
        String sh = shader != null && List.of("none", "blur", "grayscale", "sepia", "invert").contains(shader) ? shader : null;
        boolean lasting = ms >= 1 && (vig || tint || (sh != null && !"none".equals(sh)));
        if (lasting) {
            int m = Wire.whole(ms, 1, 60000, 1000);
            o.addProperty("ms", m);
            if (fadeMs > 0) o.addProperty("fade_ms", Math.min(Math.min(fadeMs, 5000), m / 2));
            if (sh != null) o.addProperty("shader", sh);
            if (vig) {
                JsonObject v = new JsonObject();
                v.addProperty("color", Wire.color(vigColor, "#000000"));
                v.addProperty("intensity", Wire.round(Wire.num(vigIntensity, 0, 1, 0.5), 3));
                v.addProperty("pulse_ms", Wire.whole(vigPulse, 0, 10000, 0));
                o.add("vignette", v);
            }
            if (tint) {
                JsonObject t = new JsonObject();
                t.addProperty("color", Wire.color(tintColor, "#000000"));
                t.addProperty("intensity", Wire.round(Wire.num(tintIntensity, 0, 1, 0.3), 3));
                o.add("tint", t);
            }
        }
        if (flash) {
            JsonObject f = new JsonObject();
            f.addProperty("color", Wire.color(flashColor, "#FFFFFF"));
            f.addProperty("ms", Wire.whole(flashMs, 1, 2000, 300));
            o.add("flash", f);
        }
        return o.size() > 1 ? o : null;
    }
}
