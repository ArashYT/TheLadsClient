package com.thelads.core.client.ladsapi.minigame;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Tolerant reading of the minigame messages (PROTOCOL.md section 8.1): a wrong type, NaN or a missing optional field gives the
 * field's default, a number out of range is clamped, text loses its formatting codes and is cut. Nothing here throws.
 */
final class Wire {
    private static final Pattern ID = Pattern.compile("[a-z0-9_-]+");
    private static final Pattern COLOR = Pattern.compile("#[0-9a-fA-F]{6}");
    private static final Pattern RESOURCE = Pattern.compile("([a-z0-9_.-]+:)?[a-z0-9_./-]+");

    private Wire() {}

    static String str(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isString() ? e.getAsString() : null;
    }

    /** A finite JSON number (not a string that looks like one), or null. */
    static Double num(JsonObject o, String key) {
        JsonElement e = o.get(key);
        if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber()) return null;
        double d = e.getAsDouble();
        return Double.isNaN(d) || Double.isInfinite(d) ? null : d;
    }

    /** The number clamped into min..max, or {@code def} when it is missing or not a finite number. */
    static double num(JsonObject o, String key, double min, double max, double def) {
        Double d = num(o, key);
        return d == null ? def : Math.max(min, Math.min(max, d));
    }

    static int whole(JsonObject o, String key, int min, int max, int def) {
        return (int) Math.round(num(o, key, min, max, def));
    }

    /** Only a JSON {@code true} is true. */
    static boolean flag(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isBoolean() && e.getAsBoolean();
    }

    /** {@code true}/{@code false} when it is a JSON boolean, else {@code def}. */
    static boolean flag(JsonObject o, String key, boolean def) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isBoolean() ? e.getAsBoolean() : def;
    }

    static JsonObject obj(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonObject() ? e.getAsJsonObject() : null;
    }

    static JsonArray arr(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonArray() ? e.getAsJsonArray() : null;
    }

    /** An id of 1..max characters of [a-z0-9_-], or null. */
    static String id(JsonObject o, String key, int max) {
        String s = str(o, key);
        return s != null && s.length() >= 1 && s.length() <= max && ID.matcher(s).matches() ? s : null;
    }

    /** Text without formatting codes and control characters, cut at {@code max}; null when it is missing. */
    static String text(JsonObject o, String key, int max) {
        String s = str(o, key);
        return s == null ? null : clean(s, max);
    }

    static String clean(String s, int max) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < s.length() && out.length() < max; i++) {
            char c = s.charAt(i);
            if (c == '\u00a7') i++; // the code character goes too
            else if (c >= ' ' && c != 0x7f) out.append(c);
        }
        return out.toString();
    }

    /** "#RRGGBB" as 0xRRGGBB, or {@code def}. */
    static int color(JsonObject o, String key, int def) {
        String s = str(o, key);
        return s != null && COLOR.matcher(s).matches() ? Integer.parseInt(s.substring(1), 16) : def;
    }

    /** An item or sound id as "namespace:path" (no namespace = minecraft), or null. Never a URL or a path that climbs. */
    static String resource(String s, int max) {
        if (s == null || s.length() > max || !RESOURCE.matcher(s).matches() || s.contains("//") || s.contains("..")) return null;
        String low = s.toLowerCase(Locale.ROOT);
        if (low.startsWith("http:") || low.startsWith("https:")) return null;
        return s.indexOf(':') < 0 ? "minecraft:" + s : s;
    }

    static UUID uuid(JsonObject o, String key) {
        String s = str(o, key);
        return s == null ? null : com.thelads.core.client.ladsapi.LadsApiClient.parseUuid(s);
    }
}
