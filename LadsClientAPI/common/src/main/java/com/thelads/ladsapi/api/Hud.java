package com.thelads.ladsapi.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.thelads.ladsapi.session.Features;
import com.thelads.ladsapi.session.Wire;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * HUD widgets (PROTOCOL.md section 16): max 16 per player, in at most 4 groups. {@code lp.hud().group("kit").slot(1).cooldown(...)}:
 * group and slot apply to the calls after them on this object. Every call is false without the {@code hud} cap.
 */
public final class Hud {
    public static final int MAX = 16;
    public static final int MAX_GROUPS = 4;

    /** One pip of {@link #pips}: a color and whether it is filled. */
    public record Pip(String color, boolean filled) {}

    private final Features features;
    private int slot;
    private String group = "main";

    public Hud(Features features) { this.features = features; }

    public Hud slot(int slot) { this.slot = slot; return this; }

    /** {@code [a-z0-9_-]{1,16}}; anything else falls back to "main". */
    public Hud group(String group) { String g = Wire.id(group, 16); this.group = g == null ? "main" : g; return this; }

    /** {@code icon} an item id or null, {@code charges} below 0 = no badge. */
    public boolean cooldown(String id, String title, String icon, long remainingMs, long totalMs, int charges) {
        JsonObject o = base(id, "cooldown", title, null);
        if (o == null) return false;
        long total = Math.max(1, Math.min(3_600_000L, totalMs));
        if (icon != null && Wire.resource(icon, 64) != null) o.addProperty("icon", icon);
        o.addProperty("remaining_ms", Math.max(0, Math.min(total, remainingMs)));
        o.addProperty("total_ms", total);
        if (charges >= 0) o.addProperty("charges", Math.min(99, charges));
        return put(o, Features.countdown("remaining_ms"));
    }

    public boolean pips(String id, String title, List<Pip> pips) {
        JsonObject o = base(id, "pips", title, null);
        if (o == null || pips == null) return false;
        JsonArray arr = new JsonArray();
        for (Pip p : pips) {
            if (p == null || arr.size() >= 12) continue;
            JsonObject j = new JsonObject();
            String c = Wire.color(p.color(), null);
            if (c != null) j.addProperty("color", c);
            j.addProperty("filled", p.filled());
            arr.add(j);
        }
        o.add("states", arr);
        return put(o, null);
    }

    /** {@code value} 0..1; {@code left}/{@code right} (<= 16 chars) may be null. */
    public boolean meter(String id, String title, double value, String color, String left, String right) {
        JsonObject o = base(id, "meter", title, color);
        if (o == null) return false;
        o.addProperty("value", Wire.round(Wire.num(value, 0, 1, 0), 4));
        if (left != null) o.addProperty("left", Wire.text(left, 16));
        if (right != null) o.addProperty("right", Wire.text(right, 16));
        return put(o, null);
    }

    /** Up to 6 lines of 48 chars. */
    public boolean text(String id, String title, List<String> lines) {
        JsonObject o = base(id, "text", title, null);
        if (o == null || lines == null) return false;
        JsonArray arr = new JsonArray();
        for (String l : lines) {
            if (l != null && arr.size() < 6) arr.add(Wire.text(l, 48));
        }
        o.add("lines", arr);
        return put(o, null);
    }

    /** Counts down to 0:00 from {@code ms}, or up from it. The client counts; send again only to correct it. */
    public boolean timer(String id, String title, long ms, boolean countUp) {
        JsonObject o = base(id, "timer", title, null);
        if (o == null) return false;
        o.addProperty("ms", Math.max(0, Math.min(86_400_000L, ms)));
        o.addProperty("direction", countUp ? "up" : "down");
        return put(o, countUp ? Features.countup("ms") : Features.countdown("ms"));
    }

    private JsonObject base(String id, String kind, String title, String color) {
        String wid = Wire.id(id, 32);
        if (wid == null) return null;
        JsonObject o = Wire.obj("hud", "set");
        o.addProperty("id", wid);
        o.addProperty("kind", kind);
        if (title != null) o.addProperty("title", Wire.text(title, 24));
        String c = Wire.color(color, null);
        if (c != null) o.addProperty("color", c);
        if (slot != 0) o.addProperty("slot", Math.max(0, Math.min(15, slot)));
        if (!"main".equals(group)) o.addProperty("group", group);
        return o;
    }

    private boolean put(JsonObject o, Features.Adjust adjust) {
        String key = "hud:" + o.get("id").getAsString();
        if (!features.has(key)) {
            if (features.count("hud:") >= MAX) return false;
            Set<String> groups = new HashSet<>();
            for (String k : features.keys("hud:")) {
                JsonObject kept = features.kept(k);
                groups.add(kept != null && kept.has("group") ? kept.get("group").getAsString() : "main");
            }
            String g = o.has("group") ? o.get("group").getAsString() : "main";
            if (!groups.contains(g) && groups.size() >= MAX_GROUPS) return false;
        }
        return features.keep("hud", key, o, 0, true, adjust);
    }

    public boolean remove(String id) {
        String wid = Wire.id(id, 32);
        if (wid == null) return false;
        JsonObject m = Wire.obj("hud", "remove");
        m.addProperty("id", wid);
        return features.remove("hud", "hud:" + wid, m);
    }

    public boolean clear() {
        return features.removeAll("hud", "hud:", Wire.obj("hud", "clear"));
    }
}
