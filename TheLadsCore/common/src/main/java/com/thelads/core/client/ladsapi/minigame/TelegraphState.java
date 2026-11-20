package com.thelads.core.client.ladsapi.minigame;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Telegraphs (PROTOCOL.md section 15): flat shapes on the ground, depth-tested, that vanish after their ms. Only the geometry
 * the server sent; no entity information. Game thread only.
 */
public final class TelegraphState {
    public static final int MAX = 64;
    public static final double OUTLINE_ALPHA = 0.8, FILL_ALPHA = 0.35, Y_OFFSET = 0.02;

    public enum Shape { CIRCLE, RING, CONE, LINE, RECT }

    /** Sizes not used by the shape keep their defaults. {@code angle} is the full opening of a cone in degrees. */
    public record Telegraph(String id, Shape shape, double x, double y, double z, double yaw, double radius, double inner,
                            double angle, double length, double width, int color, long start, int ms, int fillMs) {
        /** 0-1: how far the fill has grown from the centre/start; complete (1) at fill_ms, at once when fill_ms is 0. */
        public double fillAt(long now) {
            if (fillMs <= 0) return 1;
            return Math.max(0, Math.min(1, (now - start) / (double) fillMs));
        }

        public boolean expired(long now) {
            return now - start >= ms;
        }
    }

    private final Map<String, Telegraph> map = new LinkedHashMap<>();

    public void clear() {
        map.clear();
    }

    /** What to draw at {@code now}; expired ones are dropped. */
    public List<Telegraph> active(long now) {
        if (map.isEmpty()) return List.of();
        prune(now);
        return new ArrayList<>(map.values());
    }

    private void prune(long now) {
        map.values().removeIf(t -> t.expired(now));
    }

    public void apply(JsonObject json, long now) {
        String op = Wire.str(json, "op");
        if (op == null) op = "set";
        switch (op) {
            case "clear" -> map.clear();
            case "remove" -> {
                String id = Wire.id(json, "id", 32);
                if (id != null) map.remove(id);
            }
            case "set" -> set(json, now);
            default -> { }
        }
    }

    private void set(JsonObject json, long now) {
        String id = Wire.id(json, "id", 32);
        String sh = Wire.str(json, "shape");
        Double ms = Wire.num(json, "ms");
        Double x = Wire.num(json, "x"), y = Wire.num(json, "y"), z = Wire.num(json, "z");
        if (id == null || sh == null || ms == null || x == null || y == null || z == null) return;
        Shape shape = switch (sh) {
            case "circle" -> Shape.CIRCLE;
            case "ring" -> Shape.RING;
            case "cone" -> Shape.CONE;
            case "line" -> Shape.LINE;
            case "rect" -> Shape.RECT;
            default -> null;
        };
        if (shape == null) return;
        prune(now);
        if (!map.containsKey(id) && map.size() >= MAX) return;
        int dur = (int) Math.round(Math.max(100, Math.min(30000, ms)));
        double radius = Wire.num(json, "radius", 0.5, 64, 4);
        double inner = Wire.num(json, "inner", 0, radius, 0.75 * radius);
        double angle = Wire.num(json, "angle", 1, 360, 90);
        double length = Wire.num(json, "length", 0.5, 64, 4);
        double width = Wire.num(json, "width", 0.1, 64, 1);
        double yaw = Wire.num(json, "yaw", -3600, 3600, 0);
        int fill = Wire.whole(json, "fill_ms", 0, dur, 0);
        map.put(id, new Telegraph(id, shape, x, y, z, yaw, radius, inner, angle, length, width, Wire.color(json, "color", 0xFF0000),
            now, dur, fill));
    }
}
