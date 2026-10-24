package com.thelads.core.modules;

import com.thelads.core.config.Module;

/**
 * Custom Skies (Lunar, Badlion's pack tweaks): draws the sky boxes resource packs ship in OptiFine's format (client.CustomSky) where
 * nothing else does: 1.8.9 without OptiFine (Ionium), 1.20.1 and 26.x; with OptiFine, OptiFine's own Custom Sky setting draws them.
 * On by default, as OptiFine's: without a sky pack it draws nothing. Visual only.
 */
public final class CustomSkyModule extends Module {
    public static final String NAME = "CustomSky";

    public CustomSkyModule() {
        super(NAME, "Draws the custom skies resource packs include (OptiFine sky format) over the overworld sky.");
        setEnabled(true);
    }
}
