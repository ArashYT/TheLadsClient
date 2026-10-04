package com.thelads.core.shared;

import java.lang.management.ManagementFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * QA only, set by LADS_VERIFY_LOG_PROBE=burst|quit|crash: logs the JVM uptime once the game has loaded (startup time), then
 * 100 ticks later a numbered burst of lines, and keeps running, quits normally or crashes. latest.log must then hold every
 * line. Inert without the variable.
 */
public final class LogProbe {
    private static final String MODE = System.getenv("LADS_VERIFY_LOG_PROBE");
    private static final int LINES = 20_000;
    private static int ticks;

    private LogProbe() {}

    /** Every client tick; {@code loaded}: startup finished (title screen reachable). */
    public static void tick(boolean loaded, Runnable quit) {
        if (MODE == null || !loaded || ticks > 100) return;
        Logger log = LoggerFactory.getLogger("LadsLogProbe");
        if (ticks++ == 0) log.info("Lads log probe: game loaded at JVM uptime {} ms", ManagementFactory.getRuntimeMXBean().getUptime());
        if (ticks <= 100) return;
        long start = System.nanoTime();
        for (int i = 1; i <= LINES; i++) log.info("Lads log probe line {}/{}", i, LINES);
        log.info("Lads log probe: {} lines took {} ms on the game thread; mode {}", LINES, (System.nanoTime() - start) / 1_000_000, MODE);
        if (MODE.equals("crash")) throw new IllegalStateException("Lads log probe: deliberate QA crash");
        if (MODE.equals("quit")) quit.run();
    }
}
