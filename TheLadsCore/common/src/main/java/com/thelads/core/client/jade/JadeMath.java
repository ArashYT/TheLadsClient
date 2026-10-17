package com.thelads.core.client.jade;

/** Pure maths and text of the Lads Jade overlay (no Minecraft types), shared by the version ports. */
public final class JadeMath {
    /** Note block pitch 0 is F sharp; every click goes up a semitone. */
    private static final String[] NOTES = {"F♯", "G", "G♯", "A", "A♯", "B", "C", "C♯", "D", "D♯", "E", "F"};
    /** Gap between the panel and the screen edge it hugs. */
    public static final int EDGE = 4;

    private JadeMath() {}

    public static String noteName(int pitch) {
        return NOTES[Math.floorMod(pitch, NOTES.length)];
    }

    public static int percent(int value, int max) {
        if (max <= 0) return 100;
        return Math.max(0, Math.min(100, Math.round(value * 100f / max)));
    }

    /** 20.0 -> "20", 15.5 -> "15.5", 3.25 -> "3.3". */
    public static String number(double value) {
        double rounded = Math.round(value * 10) / 10.0;
        return rounded == Math.rint(rounded) ? Long.toString(Math.round(rounded)) : Double.toString(rounded);
    }

    /** Peak height in blocks of a fully charged horse jump (the community cubic fit of measured vanilla jumps). */
    public static double jumpHeight(double strength) {
        double x = strength;
        return Math.max(0, -0.1817584952 * x * x * x + 3.689713992 * x * x + 2.128599134 * x - 0.343930367);
    }

    /**
     * Left (or top) edge of a panel on a 3x3 anchor grid: cell 0 hugs the start edge, 1 is centred, 2 hugs the end edge.
     * The offset is applied after, and the result is kept on screen.
     */
    public static int place(int cell, int screen, int size, int offset) {
        int base = switch (cell) {
            case 0 -> EDGE;
            case 2 -> screen - size - EDGE;
            default -> (screen - size) / 2;
        };
        return Math.max(0, Math.min(screen - size, base + offset));
    }

    /** Moves {@code value} toward {@code goal} by at most {@code step}. */
    public static float approach(float value, float goal, float step) {
        return value < goal ? Math.min(goal, value + step) : Math.max(goal, value - step);
    }

    /** "iron_xylophone" -> "Iron Xylophone". */
    public static String title(String id) {
        StringBuilder out = new StringBuilder(id.length());
        boolean upper = true;
        for (char c : id.toCharArray()) {
            if (c == '_' || c == '-') { out.append(' '); upper = true; continue; }
            out.append(upper ? Character.toUpperCase(c) : c);
            upper = false;
        }
        return out.toString();
    }
}
