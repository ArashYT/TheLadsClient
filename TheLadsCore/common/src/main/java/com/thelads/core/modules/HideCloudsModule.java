package com.thelads.core.modules;

import com.thelads.core.config.Module;

/**
 * Hide Clouds: while on, the game renders as if Clouds were OFF. The player's own Clouds video setting is never changed or saved.
 * LadsClientAPI servers can force it on (The Lads Network: PVP-Duels).
 */
public final class HideCloudsModule extends Module {
    public static final String NAME = "Hide Clouds";

    public HideCloudsModule() {
        super(NAME, "Stops drawing clouds without changing your Clouds video setting.");
    }

    /** True when clouds should not be drawn right now (module on, by the player or forced by the server). */
    public static boolean active() {
        com.thelads.core.config.ModuleManager mm = com.thelads.core.config.ModuleManager.getInstance();
        if (mm == null) return false;
        Module m = mm.getModule(NAME);
        return m != null && m.isEnabled();
    }
}
