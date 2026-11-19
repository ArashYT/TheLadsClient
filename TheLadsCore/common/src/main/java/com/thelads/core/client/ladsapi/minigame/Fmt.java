package com.thelads.core.client.ladsapi.minigame;

import java.util.Locale;

/** Text for the widgets, so every game version shows the same numbers. */
public final class Fmt {
    private Fmt() {}

    /** "1:30" or "1:02:03": whole seconds, rounded down. */
    public static String clock(long ms) {
        long s = Math.max(0, ms) / 1000;
        return s >= 3600 ? String.format(Locale.ROOT, "%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60)
            : String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60);
    }

    /** A run time: "1:00.512" or "0:14.650". */
    public static String runTime(long ms) {
        long t = Math.max(0, ms);
        long s = t / 1000;
        String base = s >= 3600 ? String.format(Locale.ROOT, "%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60)
            : String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60);
        return base + String.format(Locale.ROOT, ".%03d", t % 1000);
    }

    /** A split delta in seconds with a sign, hundredths cut: "-0.42", "+0.18". */
    public static String delta(long ms) {
        long a = Math.abs(ms);
        return String.format(Locale.ROOT, "%s%d.%02d", ms < 0 ? "-" : "+", a / 1000, a % 1000 / 10);
    }
}
