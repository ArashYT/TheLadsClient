package com.thelads.ladsapi.api;

import com.google.gson.JsonObject;
import com.thelads.ladsapi.session.Features;
import com.thelads.ladsapi.session.Wire;
import java.util.UUID;

/** World markers for objectives and teammates (PROTOCOL.md section 14). Max 64 per player. Every call is false without the {@code waypoint} cap. */
public final class Waypoints {
    public static final int MAX = 64;
    private final Features features;

    public Waypoints(Features features) { this.features = features; }

    public boolean set(String id, double x, double y, double z, String label, String color) {
        return set(id, x, y, z, label, color, null, true, false, false);
    }

    /** {@code icon}: an item id or a glyph name (diamond, flag, bed, ...), null = diamond. */
    public boolean set(String id, double x, double y, double z, String label, String color, String icon,
                       boolean distance, boolean beam, boolean throughWalls) {
        if (!Wire.finite(x) || !Wire.finite(y) || !Wire.finite(z)) return false;
        JsonObject o = base(id, label, color, icon, distance, beam);
        if (o == null) return false;
        o.addProperty("x", Wire.round(x, 3));
        o.addProperty("y", Wire.round(y, 3));
        o.addProperty("z", Wire.round(z, 3));
        if (throughWalls) o.addProperty("through_walls", true);
        return put(o);
    }

    /** A marker that follows an entity the client has loaded. */
    public boolean setEntity(String id, UUID entity, String label, String color, String icon) {
        if (entity == null) return false;
        JsonObject o = base(id, label, color, icon, true, false);
        if (o == null) return false;
        o.addProperty("entity", entity.toString());
        return put(o);
    }

    private JsonObject base(String id, String label, String color, String icon, boolean distance, boolean beam) {
        String wid = Wire.id(id, 32);
        if (wid == null) return null;
        JsonObject o = Wire.obj("waypoint", "set");
        o.addProperty("id", wid);
        if (label != null) o.addProperty("label", Wire.text(label, 32));
        String c = Wire.color(color, null);
        if (c != null) o.addProperty("color", c);
        if (icon != null && Wire.resource(icon, 64) != null) o.addProperty("icon", icon);
        if (!distance) o.addProperty("distance", false);
        if (beam) o.addProperty("beam", true);
        return o;
    }

    private boolean put(JsonObject o) {
        String key = "wp:" + o.get("id").getAsString();
        if (!features.has(key) && features.count("wp:") >= MAX) return false;
        return features.keep("waypoint", key, o, 0, true, null);
    }

    public boolean remove(String id) {
        String wid = Wire.id(id, 32);
        if (wid == null) return false;
        JsonObject m = Wire.obj("waypoint", "remove");
        m.addProperty("id", wid);
        return features.remove("waypoint", "wp:" + wid, m);
    }

    public boolean clear() {
        return features.removeAll("waypoint", "wp:", Wire.obj("waypoint", "clear"));
    }
}
