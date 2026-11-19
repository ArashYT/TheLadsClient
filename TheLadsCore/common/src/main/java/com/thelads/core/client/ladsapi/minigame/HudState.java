package com.thelads.core.client.ladsapi.minigame;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * HUD widgets (PROTOCOL.md section 16). Each group is one element of the Lads HUD editor ({@link #groups}, {@link #widgets}).
 * Cooldowns and timers are counted by the client from receipt: {@link #cooldownRemaining}, {@link #timerMs}. Game thread only.
 */
public final class HudState {
    public static final int MAX_WIDGETS = 16, MAX_GROUPS = 4;

    public enum Kind { COOLDOWN, PIPS, METER, TEXT, TIMER }

    public record Pip(int color, boolean filled) {}

    /** Fields that do not belong to the widget's kind keep their defaults (null / 0 / empty). {@code charges} is -1 when absent. */
    public record Widget(String id, Kind kind, String group, int slot, String title, int color, String icon, long remainingMs,
                         long totalMs, int charges, List<Pip> pips, double value, String left, String right, List<String> lines,
                         long ms, boolean up, long receivedAt) {}

    private final Map<String, Widget> map = new LinkedHashMap<>();

    public void clear() {
        map.clear();
    }

    /** Group names in the order they first appeared. */
    public List<String> groups() {
        Set<String> g = new LinkedHashSet<>();
        for (Widget w : map.values()) g.add(w.group());
        return new ArrayList<>(g);
    }

    /** One group's widgets by slot (arrival order within a slot). */
    public List<Widget> widgets(String group) {
        List<Widget> out = new ArrayList<>();
        for (Widget w : map.values()) if (w.group().equals(group)) out.add(w);
        out.sort(Comparator.comparingInt(Widget::slot));
        return out;
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
        String k = Wire.str(json, "kind");
        if (id == null || k == null) return;
        Kind kind = switch (k) {
            case "cooldown" -> Kind.COOLDOWN;
            case "pips" -> Kind.PIPS;
            case "meter" -> Kind.METER;
            case "text" -> Kind.TEXT;
            case "timer" -> Kind.TIMER;
            default -> null;
        };
        if (kind == null) return;
        String g = Wire.id(json, "group", 16);
        String group = g == null ? "main" : g;
        Widget old = map.get(id);
        if (old == null && map.size() >= MAX_WIDGETS) return;
        Set<String> others = new LinkedHashSet<>();
        for (Widget w : map.values()) if (!w.id().equals(id)) others.add(w.group());
        if (!others.contains(group) && others.size() >= MAX_GROUPS) return;

        String title = Wire.text(json, "title", 24);
        int color = Wire.color(json, "color", 0xFFFFFF);
        int slot = Wire.whole(json, "slot", 0, 15, 0);
        String icon = Wire.resource(Wire.str(json, "icon"), 64);
        long remaining = 0, total = 1, ms = 0;
        int charges = -1;
        List<Pip> pips = List.of();
        double value = 0;
        String left = "", right = "";
        List<String> lines = List.of();
        boolean up = false;
        switch (kind) {
            case COOLDOWN -> {
                total = Math.round(Wire.num(json, "total_ms", 1, 3_600_000, 1));
                remaining = Math.round(Wire.num(json, "remaining_ms", 0, 3_600_000, 0));
                if (json.has("charges")) charges = Wire.whole(json, "charges", 0, 99, 0);
            }
            case PIPS -> {
                List<Pip> list = new ArrayList<>();
                JsonArray arr = Wire.arr(json, "states");
                if (arr != null) {
                    for (JsonElement e : arr) {
                        if (list.size() >= 12) break;
                        if (!e.isJsonObject()) continue;
                        JsonObject p = e.getAsJsonObject();
                        list.add(new Pip(Wire.color(p, "color", 0xFFFFFF), Wire.flag(p, "filled", false)));
                    }
                }
                pips = list;
            }
            case METER -> {
                value = Wire.num(json, "value", 0, 1, 0);
                String l = Wire.text(json, "left", 16), r = Wire.text(json, "right", 16);
                left = l == null ? "" : l;
                right = r == null ? "" : r;
            }
            case TEXT -> {
                List<String> list = new ArrayList<>();
                JsonArray arr = Wire.arr(json, "lines");
                if (arr != null) {
                    for (JsonElement e : arr) {
                        if (list.size() >= 6) break;
                        if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isString()) list.add(Wire.clean(e.getAsString(), 48));
                    }
                }
                lines = list;
            }
            case TIMER -> {
                ms = Math.round(Wire.num(json, "ms", 0, 86_400_000, 0));
                up = "up".equals(Wire.str(json, "direction"));
            }
        }
        map.put(id, new Widget(id, kind, group, slot, title == null ? "" : title, color, icon, remaining, total, charges, pips,
            value, left, right, lines, ms, up, now));
    }

    /** Milliseconds left on a cooldown widget, counted from receipt. */
    public static long cooldownRemaining(Widget w, long now) {
        return Math.max(0, w.remainingMs() - Math.max(0, now - w.receivedAt()));
    }

    /** 0-1 of a cooldown that is still to wait (1 = just started). */
    public static double cooldownFraction(Widget w, long now) {
        return Math.min(1, cooldownRemaining(w, now) / (double) w.totalMs());
    }

    /** The shown time of a timer widget: down stops at 0, up stops at 24 h. */
    public static long timerMs(Widget w, long now) {
        long el = Math.max(0, now - w.receivedAt());
        return w.up() ? Math.min(86_400_000L, w.ms() + el) : Math.max(0, w.ms() - el);
    }
}
