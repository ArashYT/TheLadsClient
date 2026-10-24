package com.thelads.core.modules;

import com.thelads.core.config.Module;

/**
 * Clear Glass (Badlion): glass and the 16 stained glass blocks (and their panes, which use the same textures) lose the streaks
 * inside their frame; plain glass shows only its frame, stained glass an even tint. It edits the textures as they load
 * (client.ClearGlass), so turning it on or off reloads resources once. Works with resource packs. Visual only.
 */
public final class ClearGlassModule extends Module {
    public static final String NAME = "ClearGlass";

    public ClearGlassModule() {
        super(NAME, "Removes the streaks inside glass and stained glass, keeping the frame. Reloads textures when toggled.");
    }
}
