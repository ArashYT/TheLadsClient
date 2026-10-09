package com.thelads.core.v1_20_1.embedded.playtime;

import com.thelads.core.v1_20_1.embedded.playtime.client.handler.EventHandlerCommon;
import com.thelads.core.v1_20_1.embedded.playtime.util.IWithPlayTime;
import com.thelads.core.v1_20_1.perf.PerfProbe1201;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.storage.LevelSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * World Play Time Reborn for 1.20.1, embedded in Lads Core: play time (and world size) in the world list, play time in the server
 * list. Called once at client init, only while the original mod is not installed ({@code EmbeddedMods1201}); the rendering and the
 * data read live in the mixins of {@code theladscore.v1_20_1.embedded.playtime.mixins.json}.
 */
public final class PlayTimeInit1201 {
    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore-PlayTime");

    private PlayTimeInit1201() {}

    public static void init() {
        WorldPlayTimeReborn.init();
        ClientTickEvents.END_CLIENT_TICK.register(mc -> EventHandlerCommon.onClientTick());
        ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> EventHandlerCommon.onLeavingGame());
        ClientPlayConnectionEvents.DISCONNECT.register((listener, mc) -> EventHandlerCommon.onLeaveServer());
        if (PerfProbe1201.passive()) ClientTickEvents.END_CLIENT_TICK.register(new Probe()::tick);
    }

    // ---------------------------------------------------------------- QA probe

    /**
     * Once in a world: the mixin on the level summary must be applied, and the real world list loader (the one the select-world
     * screen uses) must return summaries with the size/play time attached by the wrapped read.
     */
    private static final class Probe {
        private int ticks;
        private boolean started;

        void tick(Minecraft client) {
            if (started || client.level == null || ++ticks < 100) return;
            started = true;
            boolean mixin = IWithPlayTime.class.isAssignableFrom(LevelSummary.class);
            try {
                var source = client.getLevelSource();
                source.loadLevelSummaries(source.findLevelCandidates()).whenComplete((list, failure) -> {
                    if (failure != null) {
                        LOGGER.info("PERF-PROBE playtime FAIL loading the world list: {}", failure.toString());
                        return;
                    }
                    int sized = 0, timed = 0;
                    for (LevelSummary summary : list) {
                        if (summary instanceof IWithPlayTime data) {
                            if (data.getWorldSizeBytes() > 0) sized++;
                            if (data.getPlayTimeTicks() > 0) timed++;
                        }
                    }
                    boolean ok = mixin && (list.isEmpty() || sized > 0);
                    LOGGER.info("PERF-PROBE playtime {} summaryMixin={} worlds={} withSize={} withPlayTime={}", ok ? "PASS" : "FAIL", mixin, list.size(), sized, timed);
                });
            } catch (Exception failure) {
                LOGGER.info("PERF-PROBE playtime FAIL {}", failure.toString());
            }
        }
    }
}
