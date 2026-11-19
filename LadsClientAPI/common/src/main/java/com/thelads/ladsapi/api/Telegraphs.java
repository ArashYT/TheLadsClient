package com.thelads.ladsapi.api;

import com.google.gson.JsonObject;
import com.thelads.ladsapi.session.Features;
import com.thelads.ladsapi.session.Wire;

/**
 * Ground indicators for attacks (PROTOCOL.md section 15). Max 64 per player. {@code ms} is how long it shows (100..30000),
 * {@code fillMs} when the fill is complete (0 = at once). Every call is false without the {@code telegraph} cap.
 */
public final class Telegraphs {
    public static final int MAX = 64;
    private final Features features;

    public Telegraphs(Features features) { this.features = features; }

    public boolean circle(String id, double x, double y, double z, double radius, String color, int ms, int fillMs) {
        JsonObject o = base(id, "circle", x, y, z, color, ms, fillMs);
        if (o == null) return false;
        o.addProperty("radius", Wire.num(radius, 0.5, 64, 4));
        return put(o);
    }

    /** {@code inner}: the hole's radius, 0..radius. */
    public boolean ring(String id, double x, double y, double z, double radius, double inner, String color, int ms, int fillMs) {
        JsonObject o = base(id, "ring", x, y, z, color, ms, fillMs);
        if (o == null) return false;
        double r = Wire.num(radius, 0.5, 64, 4);
        o.addProperty("radius", r);
        o.addProperty("inner", Wire.num(inner, 0, r, 0.75 * r));
        return put(o);
    }

    /** Apex at x,y,z, {@code radius} long, {@code angle} 1..360 degrees wide, pointing along yaw. */
    public boolean cone(String id, double x, double y, double z, double radius, double angle, double yaw, String color, int ms, int fillMs) {
        JsonObject o = base(id, "cone", x, y, z, color, ms, fillMs);
        if (o == null) return false;
        o.addProperty("radius", Wire.num(radius, 0.5, 64, 4));
        o.addProperty("angle", Wire.num(angle, 1, 360, 90));
        o.addProperty("yaw", Wire.round(Wire.num(yaw, -3600, 3600, 0), 2));
        return put(o);
    }

    /** From x,y,z along yaw for {@code length} blocks. */
    public boolean line(String id, double x, double y, double z, double yaw, double length, double width, String color, int ms, int fillMs) {
        return bar("line", id, x, y, z, yaw, length, width, color, ms, fillMs);
    }

    /** Centred on x,y,z. */
    public boolean rect(String id, double x, double y, double z, double yaw, double length, double width, String color, int ms, int fillMs) {
        return bar("rect", id, x, y, z, yaw, length, width, color, ms, fillMs);
    }

    private boolean bar(String shape, String id, double x, double y, double z, double yaw, double length, double width, String color, int ms, int fillMs) {
        JsonObject o = base(id, shape, x, y, z, color, ms, fillMs);
        if (o == null) return false;
        o.addProperty("yaw", Wire.round(Wire.num(yaw, -3600, 3600, 0), 2));
        o.addProperty("length", Wire.num(length, 0.5, 64, 4));
        o.addProperty("width", Wire.num(width, 0.1, 64, 1));
        return put(o);
    }

    private JsonObject base(String id, String shape, double x, double y, double z, String color, int ms, int fillMs) {
        String tid = Wire.id(id, 32);
        if (tid == null || !Wire.finite(x) || !Wire.finite(y) || !Wire.finite(z)) return null;
        JsonObject o = Wire.obj("telegraph", "set");
        o.addProperty("id", tid);
        o.addProperty("shape", shape);
        o.addProperty("x", Wire.round(x, 3));
        o.addProperty("y", Wire.round(y, 3));
        o.addProperty("z", Wire.round(z, 3));
        String c = Wire.color(color, null);
        if (c != null) o.addProperty("color", c);
        int m = Wire.whole(ms, 100, 30000, 1500);
        o.addProperty("ms", m);
        if (fillMs > 0) o.addProperty("fill_ms", Math.min(fillMs, m));
        return o;
    }

    private boolean put(JsonObject o) {
        String key = "tg:" + o.get("id").getAsString();
        if (!features.has(key) && features.count("tg:") >= MAX) return false;
        return features.keep("telegraph", key, o, o.get("ms").getAsLong(), true, Features.countdown("ms", "fill_ms"));
    }

    public boolean remove(String id) {
        String tid = Wire.id(id, 32);
        if (tid == null) return false;
        JsonObject m = Wire.obj("telegraph", "remove");
        m.addProperty("id", tid);
        return features.remove("telegraph", "tg:" + tid, m);
    }

    public boolean clear() {
        return features.removeAll("telegraph", "tg:", Wire.obj("telegraph", "clear"));
    }
}
