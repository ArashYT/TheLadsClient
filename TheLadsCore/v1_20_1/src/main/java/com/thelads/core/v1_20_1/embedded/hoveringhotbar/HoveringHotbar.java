// Adapted from Hovering Hotbar 26.3.0 by Fuzs (MPL-2.0); modified by The Lads: repackaged into Lads Core, no Puzzles Lib,
// ported to Minecraft 1.20.1 from the Lads 26.3 embedded copy.
package com.thelads.core.v1_20_1.embedded.hoveringhotbar;

import com.thelads.core.v1_20_1.embedded.EmbeddedMods1201;
import com.thelads.core.v1_20_1.embedded.hoveringhotbar.config.ClientConfig;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HoveringHotbar {
    public static final String MOD_ID = "hoveringhotbar";
    public static final String MOD_NAME = "Hovering Hotbar";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_NAME);

    public static final ClientConfig CONFIG = new ClientConfig();

    /** How often each hook ran; the QA probe reads them to tell which parts of the HUD the loader actually draws through the hooked methods. */
    public static final AtomicInteger HOTBAR_DRAWS = new AtomicInteger();
    public static final AtomicInteger EXPERIENCE_DRAWS = new AtomicInteger();
    public static final AtomicInteger STATUS_DRAWS = new AtomicInteger();
    public static final AtomicInteger ITEM_NAME_LINES = new AtomicInteger();
    public static final AtomicInteger CHAT_LINES = new AtomicInteger();

    private static volatile float lastHotbarTranslateY;

    /** Vertical translation of the matrix the hotbar was last drawn with (-offset while the hook is in effect). */
    public static float lastHotbarTranslateY() {
        return lastHotbarTranslateY;
    }

    public static void hotbarDrawn(float translateY) {
        HOTBAR_DRAWS.incrementAndGet();
        lastHotbarTranslateY = translateY;
    }

    /** Pixels the hotbar hovers above the screen bottom, for Lads HUD elements attached to it; 0 while the original mod runs. */
    public static int hotbarLift() {
        return EmbeddedMods1201.active(MOD_ID) ? CONFIG.getHotbarOffset() : 0;
    }
}
