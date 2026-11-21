package com.thelads.core.perf;

/**
 * Haste V and up: vanilla's arm swing lasts {@code 6 - (1 + amplifier)} ticks, so at 1 tick or less the swing is reset in the tick it
 * starts (swingTime/duration is 0 on every frame) and no swing is drawn. The client shows at least {@link #MIN_TICKS}: with 2, swingTime
 * is 1 for one tick (progress 0.5), the smallest duration that still draws a swing. Visual only: the mixins call it for client worlds
 * only, so the integrated server and every packet keep vanilla's value. Kill switch {@code -Dlads.hasteAnimFix=false}.
 */
public final class HasteAnimFix {
    public static final int MIN_TICKS = 2;
    private static final boolean ENABLED = !"false".equalsIgnoreCase(System.getProperty("lads.hasteAnimFix", "true").trim()) && !"false".equalsIgnoreCase(System.getProperty("lads.hasteanimfix", "true").trim());

    private HasteAnimFix() {}

    public static boolean enabled() { return ENABLED; }

    private static volatile boolean logged;

    public static int clamp(int ticks) {
        if (!ENABLED || ticks >= MIN_TICKS) return ticks;
        if (!logged) { logged = true; org.slf4j.LoggerFactory.getLogger("TheLadsCore").info("[Lads] Haste swing fix: swing duration {} ticks shown as {}", ticks, MIN_TICKS); }
        return MIN_TICKS;
    }
}
