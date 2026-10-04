package com.thelads.core.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.HashSet;

/** Original Lads presentation of public durability values; independent of any upstream implementation. */
public final class DurabilityPresentation {
    public enum Format { NUMBERS, BAR, TEXT }
    public enum Coloring { VARYING, BASE, GOLD }
    public record Span(String text, int rgb) {}
    public record Line(List<Span> spans) { public Line { spans = List.copyOf(spans); } }
    public record Style(Format format, Coloring coloring, boolean hint, boolean maximum, boolean colorize, int baseRgb) {}
    private DurabilityPresentation() {}

    public static List<Line> lines(int maximum, int damage, Style style) {
        if (maximum <= 0) return List.of();
        int remaining = maximum - Math.max(0, Math.min(damage, maximum));
        int base = style.baseRgb() & 0xffffff;
        int reactive = !style.colorize() || style.coloring() == Coloring.BASE ? base
            : style.coloring() == Coloring.GOLD ? 0xffaa00 : color(remaining, maximum);
        List<Line> output = new ArrayList<>(2);
        List<Span> spans = new ArrayList<>(5);
        if (style.format() == Format.BAR) {
            if (style.hint()) output.add(new Line(List.of(new Span("Durability:", base))));
            int filled = (int) Math.round(10.0 * remaining / maximum);
            spans.add(new Span("[", base));
            spans.add(new Span("█".repeat(filled) + "▒".repeat(10 - filled), reactive));
            spans.add(new Span("]", base));
        } else {
            if (style.hint()) spans.add(new Span("Durability: ", base));
            if (style.format() == Format.TEXT) {
                spans.add(new Span(condition(remaining, maximum), reactive));
            } else {
                int maxColor = style.coloring() == Coloring.VARYING || !style.colorize() ? base : reactive;
                spans.add(new Span(Integer.toString(remaining), remaining == maximum && style.maximum() ? maxColor : reactive));
                if (style.maximum() && remaining != maximum) {
                    spans.add(new Span(" / ", base));
                    spans.add(new Span(Integer.toString(maximum), maxColor));
                }
            }
        }
        output.add(new Line(spans));
        return List.copyOf(output);
    }
    public static int color(int remaining, int maximum) {
        return (long) remaining * 10 >= (long) maximum * 4 ? 0x55ff55
            : (long) remaining * 10 >= maximum ? 0xffaa00 : 0xff5555;
    }
    public static String condition(int remaining, int maximum) {
        if (remaining >= maximum) return "Pristine";
        if ((long) remaining * 10 >= (long) maximum * 4) return "Slightly damaged";
        if ((long) remaining * 10 >= maximum) return "Severely damaged";
        return "Nearly broken";
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
    public static Set<String> excludedNamespaces(String text) {
        if (text == null || text.isBlank()) return Set.of();
        Set<String> result = new HashSet<>();
        for (String token : text.substring(0, Math.min(text.length(), 4096)).split(",")) {
            String namespace = token.strip().toLowerCase(Locale.ROOT);
            if (namespace.matches("[a-z0-9_.-]+")) result.add(namespace);
        }
        return Set.copyOf(result);
    }
    public static boolean visible(String namespace, int maximum, int damage, boolean vanillaOnly, boolean showFull, Set<String> excluded) {
        return maximum > 0 && (!vanillaOnly || "minecraft".equals(namespace)) && !excluded.contains(namespace)
            && (showFull || damage > 0);
    }
}
