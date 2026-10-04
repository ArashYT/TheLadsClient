package com.thelads.core.client;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * EnhancedToolbars' durability line, shared by 26.x NativeDurabilityTooltip and 1.8.9 Durability189. It is always one line:
 * the uses left as a count, a gauge of pips with a percentage, or a condition word. The wear colour uses the item's own
 * durability-bar hue, from red when worn out to green when new.
 */
public final class DurabilityPresentation {
    /** In the order of the "Durability Style" dropdown (Numbers, Bar, Text). */
    public enum Shape { COUNT, GAUGE, WORDS }
    /** In the order of the "Durability Color Style" dropdown (Varying, Base, Gold). */
    public enum Tint { WEAR, PLAIN, GOLD }
    public record Part(String text, int rgb) {}
    public record Settings(Shape shape, Tint tint, boolean label, boolean showMax, boolean colorize, int baseRgb) {}

    /** Condition words from worn out to new: under a quarter left, under half, under three quarters, not full, full. */
    public static final List<String> WEAR_WORDS = List.of("About to break", "Battered", "Worn", "Good", "Like new");
    public static final int PIPS = 20;
    private static final int GOLD = 0xffaa00, SPENT_PIP = 0x555555;
    private DurabilityPresentation() {}

    /** The line as coloured parts, or nothing for an item without durability. */
    public static List<Part> line(int maximum, int damage, Settings settings) {
        if (maximum <= 0) return List.of();
        int left = (int) Math.max(0, Math.min(maximum, (long) maximum - damage));
        int base = settings.baseRgb() & 0xffffff;
        int ink = !settings.colorize() ? base : switch (settings.tint()) {
            case WEAR -> wearColor(left, maximum);
            case PLAIN -> base;
            case GOLD -> GOLD;
        };
        List<Part> parts = new ArrayList<>(4);
        if (settings.label()) parts.add(new Part(settings.shape() == Shape.WORDS ? "Condition: " : "Uses left: ", base));
        switch (settings.shape()) {
            case COUNT -> {
                parts.add(new Part(Integer.toString(left), ink));
                if (settings.showMax()) parts.add(new Part(" / " + maximum, base));
            }
            case GAUGE -> {
                int lit = share(left, maximum, PIPS);
                parts.add(new Part("|".repeat(lit), ink));
                parts.add(new Part("|".repeat(PIPS - lit), settings.colorize() ? SPENT_PIP : base));
                parts.add(new Part(" " + share(left, maximum, 100) + "%", ink));
            }
            case WORDS -> parts.add(new Part(condition(left, maximum), ink));
        }
        return List.copyOf(parts);
    }

    /** The colour scale of the item's durability bar: hue from red (nothing left) to green (new). */
    public static int wearColor(int left, int maximum) {
        float health = maximum <= 0 ? 0 : Math.max(0, Math.min(1, (float) left / maximum));
        return Color.HSBtoRGB(health / 3, 1, 1) & 0xffffff;
    }

    static String condition(int left, int maximum) {
        return WEAR_WORDS.get(left >= maximum ? 4 : (int) (4L * Math.max(0, left) / maximum));
    }

    /** {@code left} of {@code maximum} in {@code units}, rounded down, but at least 1 while anything is left. */
    static int share(int left, int maximum, int units) {
        return left <= 0 ? 0 : (int) Math.max(1, (long) left * units / maximum);
    }

    /** The chat colours 0-f, for text that has only those (1.8.9). */
    private static final int[] CHAT = {0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA,
        0x555555, 0x5555FF, 0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF};
    /** The chat colour code (0-15) closest to an RGB colour. */
    public static int chatColor(int rgb) {
        int best = 0;
        long bestDistance = Long.MAX_VALUE;
        for (int i = 0; i < CHAT.length; i++) {
            long r = (rgb >> 16 & 255) - (CHAT[i] >> 16 & 255), g = (rgb >> 8 & 255) - (CHAT[i] >> 8 & 255), b = (rgb & 255) - (CHAT[i] & 255);
            long distance = r * r + g * g + b * b;
            if (distance < bestDistance) { bestDistance = distance; best = i; }
        }
        return best;
    }

    /**
     * The "Excluded Mods" text, split on commas, semicolons or spaces. An entry "mod" hides every item of that mod and "mod:item"
     * hides one item. Anything that is not a resource id is dropped.
     */
    public static Set<String> exclusions(String text) {
        Set<String> entries = new HashSet<>();
        if (text != null)
            for (String entry : text.toLowerCase(Locale.ROOT).split("[,;\\s]+"))
                if (entry.matches("[a-z0-9_.-]+(:[a-z0-9_./-]+)?")) entries.add(entry);
        return Set.copyOf(entries);
    }

    /** Whether item {@code itemId} ("mod:item"; no "mod:" means minecraft) gets the line under the module's filters. */
    public static boolean shows(String itemId, int maximum, int damage, boolean vanillaOnly, boolean whenFull, Set<String> exclusions) {
        if (maximum <= 0 || damage <= 0 && !whenFull) return false;
        int colon = itemId.indexOf(':');
        String mod = colon < 0 ? "minecraft" : itemId.substring(0, colon);
        return (!vanillaOnly || mod.equals("minecraft")) && !exclusions.contains(mod) && !exclusions.contains(itemId);
    }
}
