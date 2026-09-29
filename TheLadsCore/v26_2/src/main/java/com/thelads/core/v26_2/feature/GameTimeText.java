package com.thelads.core.v26_2.feature;

import java.util.Locale;

/** One clock string per displayed minute, rather than a Formatter and boxes each HUD frame. */
public final class GameTimeText {
    private long cachedHours = Long.MIN_VALUE, cachedMinutes;
    private Locale cachedLocale;
    private String text;

    public String get(long clock) {
        long time = (clock + 6000L) % 24000L;
        long hours = time / 1000L;
        long minutes = (time % 1000L) * 60L / 1000L;
        Locale locale = Locale.getDefault(Locale.Category.FORMAT);
        if (hours != cachedHours || minutes != cachedMinutes || locale != cachedLocale) {
            text = String.format(locale, "%02d:%02d", hours, minutes);
            cachedHours = hours;
            cachedMinutes = minutes;
            cachedLocale = locale;
        }
        return text;
    }
}
