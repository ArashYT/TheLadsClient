package com.thelads.core.client.ladsapi.minigame;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Waypoints (PROTOCOL.md section 14). A marker is a position the server just chose to show, or an entity the client already
 * renders. {@link #position} is the only way to get where an entity marker is, and it goes through the client's own entity lookup:
 * an entity that is not loaded gives null (draw nothing, learn nothing). Game thread only.
 */
public final class WaypointState {
    public static final int MAX = 64;
    public static final Set<String> GLYPHS =
        Set.of("diamond", "flag", "bed", "skull", "star", "arrow", "shield", "sword", "question", "exclamation", "circle");

    /**
     * {@code icon} is a glyph name when {@code glyph} is true, else an item id ("minecraft:red_banner"); entity is null for a
     * coordinate marker.
     */
    public record Waypoint(String id, double x, double y, double z, UUID entity, String label, int color, String icon,
                           boolean glyph, boolean distance, boolean beam, boolean throughWalls) {}

    private final Map<String, Waypoint> map = new LinkedHashMap<>();

    public void clear() {
        map.clear();
    }

    public List<Waypoint> all() {
        if (map.isEmpty()) return List.of();
        return new ArrayList<>(map.values());
    }

    public void apply(JsonObject json) {
        String op = Wire.str(json, "op");
        if (op == null) return;
        switch (op) {
            case "clear" -> map.clear();
            case "remove" -> {
                String id = Wire.id(json, "id", 32);
                if (id != null) map.remove(id);
            }
            case "set" -> set(json);
            default -> { }
        }
    }

    private void set(JsonObject json) {
        String id = Wire.id(json, "id", 32);
        if (id == null) return;
        UUID entity = Wire.uuid(json, "entity");
        double x = 0, y = 0, z = 0;
        if (entity == null) {
            Double dx = Wire.num(json, "x"), dy = Wire.num(json, "y"), dz = Wire.num(json, "z");
            if (dx == null || dy == null || dz == null) return;
            x = dx;
            y = dy;
            z = dz;
        }
        if (!map.containsKey(id) && map.size() >= MAX) return;
        String raw = Wire.str(json, "icon");
        String icon = "diamond";
        boolean glyph = true;
        if (raw != null) {
            if (GLYPHS.contains(raw)) icon = raw;
            else {
                String item = Wire.resource(raw, 64);
                if (item != null) {
                    icon = item;
                    glyph = false;
                }
            }
        }
        String label = Wire.text(json, "label", 32);
        map.put(id, new Waypoint(id, x, y, z, entity, label == null ? "" : label, Wire.color(json, "color", 0xFFFFFF), icon, glyph,
            Wire.flag(json, "distance", true), Wire.flag(json, "beam", false), Wire.flag(json, "through_walls", false)));
    }

    /** Where to draw it, or null (an entity that is not loaded, or no lookup): then nothing is drawn. */
    public static double[] position(Waypoint w, Hooks.EntityLookup lookup) {
        if (w.entity() == null) return new double[] {w.x(), w.y(), w.z()};
        return lookup == null ? null : lookup.position(w.entity());
    }

    /** Behind blocks too: coordinate markers only, and only where the client's safety rules allow radar-like information. */
    public static boolean throughWalls(Waypoint w) {
        return w.entity() == null && w.throughWalls() && Hooks.throughWallsAllowed.getAsBoolean();
    }
}
