package com.thelads.ladsapi.session;

import com.google.gson.JsonObject;

/** Validation and clamping for the minigame messages (PROTOCOL.md section 8.1). A bad value never throws. */
public final class Wire {
    private Wire() {}

    /** {@code v} clamped to min..max; NaN and infinity give {@code def}. */
    public static double num(double v, double min, double max, double def) {
        if (Double.isNaN(v) || Double.isInfinite(v)) return def;
        return Math.max(min, Math.min(max, v));
    }

    public static int whole(double v, int min, int max, int def) {
        return (int) Math.round(num(v, min, max, def));
    }

    public static boolean finite(double v) { return !Double.isNaN(v) && !Double.isInfinite(v); }

    public static double round(double v, int decimals) {
        double f = Math.pow(10, decimals);
        return Math.round(v * f) / f;
    }

    /** "#RRGGBB" (upper-cased), or {@code def} for anything else. */
    public static String color(String c, String def) {
        return c != null && c.matches("#[0-9a-fA-F]{6}") ? c.toUpperCase(java.util.Locale.ROOT) : def;
    }

    /** An id of 1..max chars of [a-z0-9_-], or null. */
    public static String id(String s, int max) {
        return s != null && s.length() >= 1 && s.length() <= max && s.matches("[a-z0-9_-]+") ? s : null;
    }

    /** Text without formatting codes and control characters, cut at {@code max}; null stays null. */
    public static String text(String s, int max) {
        if (s == null) return null;
        String t = s.replaceAll("§.?", "").replaceAll("\\p{Cntrl}", "");
        return t.length() > max ? t.substring(0, max) : t;
    }

    /** "namespace:path" or "path" for an item or sound id, or null. */
    public static String resource(String s, int max) {
        return s != null && s.length() <= max && s.matches("([a-z0-9_.-]+:)?[a-z0-9_./-]+") ? s : null;
    }

    public static JsonObject obj(String type) {
        JsonObject o = new JsonObject();
        o.addProperty("t", type);
        return o;
    }

    public static JsonObject obj(String type, String op) {
        JsonObject o = obj(type);
        o.addProperty("op", op);
        return o;
    }
}
