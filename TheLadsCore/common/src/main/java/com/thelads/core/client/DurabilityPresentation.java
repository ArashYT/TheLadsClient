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
