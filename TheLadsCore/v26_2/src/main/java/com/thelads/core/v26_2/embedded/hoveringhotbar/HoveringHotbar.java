// Adapted from Hovering Hotbar 26.2.1 by Fuzs (MPL-2.0); modified by The Lads: repackaged into Lads Core, no Puzzles Lib.
package com.thelads.core.v26_2.embedded.hoveringhotbar;

import com.thelads.core.v26_2.embedded.hoveringhotbar.config.ClientConfig;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HoveringHotbar {
    public static final String MOD_ID = "hoveringhotbar";
    public static final String MOD_NAME = "Hovering Hotbar";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_NAME);

    public static final ClientConfig CONFIG = new ClientConfig();

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    /** Pixels the hotbar hovers above the screen bottom, for Lads HUD elements attached to it; 0 while the original mod runs. */
    public static int hotbarLift() {
        return com.thelads.core.v26_2.embedded.EmbeddedMods.active(MOD_ID) ? CONFIG.getHotbarOffset() : 0;
    }
}
