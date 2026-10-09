package com.thelads.core.v1_20_1.embedded.entityculling;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

/** Called once at client initialization while the original Entity Culling jar is not installed (see EmbeddedMods1201). */
public final class EntityCullingInit1201 {
    private static int ticks;

    private EntityCullingInit1201() {}

    public static void init() {
        // The hooks and the worker thread need no setup; only the QA probe (-Dthelads.perfProbe=true) registers anything.
        if (!Boolean.getBoolean("thelads.perfProbe")) return;
        EntityCulling.selfTest();
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.level == null) {
                ticks = 0;
                return;
            }
            if (++ticks % 600 == 0) EntityCulling.probeReport();
        });
    }
}
