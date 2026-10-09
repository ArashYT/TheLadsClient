// Modified by The Lads: new entry point for Lads Core (Ksyxis, MIT); see META-INF/lads-sources/ksyxis/NOTICE.txt.
package com.thelads.core.v1_20_1.embedded.ksyxis;

import com.thelads.core.v1_20_1.perf.SmallModsProbe1201;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.server.level.ServerLevel;

/** Entry point called by EmbeddedMods1201 while the original Ksyxis is not installed. */
public final class KsyxisInit1201 {
    private static volatile boolean ran;
    private static volatile boolean skipped;
    private static volatile long nanos;

    private KsyxisInit1201() {}

    public static void init() {
        SmallModsProbe1201.runOnce("ksyxis", true, KsyxisInit1201::probe);
    }

    /** Called from the server mixin when a world's start region has been prepared (or skipped). */
    public static void prepared(boolean startRegionSkipped, long durationNanos) {
        ran = true;
        skipped = startRegionSkipped;
        nanos = durationNanos;
    }

    /** In a single-player world: did prepareLevels leave the start region out, and how long did it take. */
    private static void probe(Minecraft client) {
        IntegratedServer server = client.getSingleplayerServer();
        if (server == null) {
            SmallModsProbe1201.skip("ksyxis", "not in a single-player world (no integrated server to prepare)");
            return;
        }
        ServerLevel overworld = server.overworld();
        SmallModsProbe1201.result("ksyxis", ran && skipped,
            "prepareLevelsRan=" + ran + " startRegionSkipped=" + skipped + " prepareLevelsMs=" + nanos / 1_000_000L
                + " tickingChunksNow=" + overworld.getChunkSource().getTickingGenerated() + " (vanilla waits for 441 before the player joins)");
    }
}
