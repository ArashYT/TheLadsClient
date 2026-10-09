package com.thelads.core.v1_20_1.embedded.immediatelyfast;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Entrypoint for embedded ImmediatelyFast optimizations in Minecraft 1.20.1.
 * Invoked reflectively via EmbeddedMods1201.
 */
public final class ImmediatelyFastInit1201 {
    private static final Logger LOGGER = LoggerFactory.getLogger("ImmediatelyFast");
    private static boolean initialized;

    private ImmediatelyFastInit1201() {}

    public static synchronized void init() {
        if (initialized) return;
        initialized = true;
        LOGGER.info("ImmediatelyFast font glyph cache (TheLadsCore native) initialized");
    }
}
