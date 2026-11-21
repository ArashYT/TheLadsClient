package com.thelads.core.perf;

import com.thelads.core.config.ModuleManager;
import com.thelads.core.modules.SmoothSingleplayerModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Smooth Singleplayer (Lads remake of Singleplayer Jitter Fix): in singleplayer the integrated server ticks on its own thread at a
 * random phase against the client tick, so entities and the world jitter. While the client hosts an integrated server that is not
 * open to LAN (and not with {@code -Dlads.smoothsp=false} or the real mod installed), the version mixins
 * (a) send entity updates every tick, and (b) nudge the server's next-tick time toward "end of the last client tick + a few ms".
 * Nothing here ever waits: the server thread only moves a timestamp by a few ms per tick, the client thread only stores one.
 */
public final class SmoothSingleplayer {
    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore");
    private static final long PERIOD_NS = 50_000_000L, OFFSET_NS = 3_000_000L, MAX_STEP_NS = 3_000_000L, DEAD_BAND_NS = 1_000_000L;
    /** The client has not ticked for this long (loading, paused window): leave the server alone. */
    private static final long STALE_NS = 250_000_000L;
    /** Healthy server ticks needed after falling behind before the phase lock comes back. */
    private static final int RECOVER_TICKS = 20;

    /** Phase lock log lines written so far (server thread): a PC that keeps falling behind would repeat them every second. */
    private static int logs;

    private static String reason;
    private static boolean decided;
    private static SmoothSingleplayerModule module;
    private static volatile boolean tracking;
    private static volatile long clientTickEndNs;
    private static boolean loggedTracking;
    /** Server thread only. */
    private static int healthy;
    private static boolean locked;

    private SmoothSingleplayer() {}

    /** Why the feature is off for this session (kill switch, original mod), or null. */
    private static synchronized String standDownReason() {
        if (!decided) {
            decided = true;
            reason = BuiltInRender.decide("smoothsp", System.getProperty("lads.smoothsp"), "smooth_animations", "smooth_singleplayer");
            if (reason != null) LOGGER.info("[Lads] Smooth Singleplayer stands down: {}", reason);
        }
        return reason;
    }

    /** True while entity updates should go out every tick; readable from the server thread. */
    public static boolean tracking() { return tracking; }

    /**
     * Client thread, end of every client tick. {@code hosting}: the client runs an integrated server that is not published to LAN.
     */
    public static void clientTickEnd(boolean hosting) {
        long now = System.nanoTime();
        clientTickEndNs = now;
        boolean on = hosting && standDownReason() == null && enabled();
        if (on != tracking) {
            tracking = on;
            if (on && !loggedTracking) {
                loggedTracking = true;
                LOGGER.info("[Lads] Smooth Singleplayer on: entity updates every tick, server tick phase-locked to the client tick");
            } else if (!on) {
                LOGGER.info("[Lads] Smooth Singleplayer off for now (not hosting a private integrated server, or switched off)");
            }
            if (!on) clientTickEndNs = 0L;
        }
    }

    private static boolean enabled() {
        SmoothSingleplayerModule m = module;
        if (m == null) {
            if (!(ModuleManager.getInstance().getModule(SmoothSingleplayerModule.NAME) instanceof SmoothSingleplayerModule)) return false;
            module = m = (SmoothSingleplayerModule) ModuleManager.getInstance().getModule(SmoothSingleplayerModule.NAME);
        }
        return m.isEnabled();
    }

    /**
     * Server thread, end of a server tick: how many nanoseconds to add to the server's next-tick time (0 = leave it).
     * Never positive or negative by more than a few ms; 0 when the client is not ticking, the server is behind 20 TPS
     * (until it has been healthy for a while) or the tick rate is not 20.
     *
     * @param nextTickNs the scheduled start of the next server tick, on the System.nanoTime clock
     */
    public static long phaseAdjust(long nextTickNs, long periodNs) {
        long anchor = clientTickEndNs;
        if (!tracking || anchor == 0L) { healthy = 0; locked = false; return 0L; }
        if (periodNs != PERIOD_NS) return 0L;
        long now = System.nanoTime();
        if (nextTickNs - now < 0L) { // the next tick is already due: server behind 20 TPS, let vanilla catch up
            healthy = 0;
            if (locked) { locked = false; if (logs++ < 3) LOGGER.info("[Lads] Smooth Singleplayer: tick phase lock dropped, server is behind 20 TPS"); }
            return 0L;
        }
        if (healthy < RECOVER_TICKS) { healthy++; return 0L; }
        if (now - anchor > STALE_NS) return 0L;
        // where the next tick should start sits at anchor + OFFSET + k * period; e = that phase minus the scheduled one, in [-25, 25) ms
        long e = Math.floorMod(anchor + OFFSET_NS - nextTickNs + PERIOD_NS / 2, PERIOD_NS) - PERIOD_NS / 2;
        if (!locked) { locked = true; if (logs++ < 3) LOGGER.info("[Lads] Smooth Singleplayer: tick phase lock engaged"); }
        if (Math.abs(e) < DEAD_BAND_NS) return 0L;
        long step = e / 4;
        return Math.max(-MAX_STEP_NS, Math.min(MAX_STEP_NS, step));
    }
}
