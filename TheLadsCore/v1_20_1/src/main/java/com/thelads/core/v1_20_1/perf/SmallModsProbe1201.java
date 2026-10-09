package com.thelads.core.v1_20_1.perf;

import java.util.Locale;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Shared plumbing for the QA probes of the small embedded mods (serverpingerfixer, fastipping, fixbookgui, ksyxis, quickpack,
 * hoveringhotbar, tooltips). A probe runs only when {@code -Dthelads.perfProbe} is {@code true}, {@code all} or a comma list that
 * names the mod, and logs {@code PERF-PROBE <name> PASS|FAIL <details>} once.
 */
public final class SmallModsProbe1201 {
    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore-PerfProbe");

    private SmallModsProbe1201() {}

    public static boolean enabled(String name) {
        String value = System.getProperty("thelads.perfProbe");
        if (value == null || value.isBlank()) return false;
        for (String part : value.toLowerCase(Locale.ROOT).split(",")) {
            String token = part.trim();
            if (token.equals("true") || token.equals("all") || token.equals(name)) return true;
        }
        return false;
    }

    /**
     * Runs the task once on the client thread: after the loading overlay is gone, or (inWorld) once the player has been in a
     * world for about three seconds. A throwing task is logged as a FAIL.
     */
    public static void runOnce(String name, boolean inWorld, Consumer<Minecraft> task) {
        if (!enabled(name)) return;
        LOGGER.info("PERF-PROBE {} armed ({})", name, inWorld ? "waits for a world" : "runs after the game has loaded");
        boolean[] done = {false};
        int[] worldTicks = {0};
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (done[0] || client.getOverlay() != null || client.getWindow() == null) return;
            if (inWorld) {
                if (client.level == null || client.player == null) {
                    worldTicks[0] = 0;
                    return;
                }
                if (++worldTicks[0] < 60) return;
            }
            done[0] = true;
            try {
                task.accept(client);
            } catch (Throwable failure) {
                LOGGER.error("PERF-PROBE {} FAIL probe threw", name, failure);
            }
        });
    }

    public static void result(String name, boolean ok, String details) {
        LOGGER.info("PERF-PROBE {} {} {}", name, ok ? "PASS" : "FAIL", details);
    }

    /** The probe cannot run in this session (for example it needs a single-player world and the player is on a server). */
    public static void skip(String name, String why) {
        LOGGER.info("PERF-PROBE {} SKIP {}", name, why);
    }
}
