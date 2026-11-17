package com.thelads.core.modules;

import com.thelads.core.config.Module;

/**
 * Smooth Movement: other players and mobs use the 26.2 entity smoothing again (fixes the 26.3 stutter, MC-311974 / MC-311976).
 * Only the 26.3 client has a mixin for it. LadsClientAPI servers can force it on (The Lads Network: everywhere).
 */
public final class SmoothMovementModule extends Module {
    public static final String NAME = "Smooth Movement";

    public SmoothMovementModule() {
        super(NAME, "Fixes choppy player and mob movement in 26.3 by using the 26.2 smoothing. Some other servers' anticheats may not expect it.");
    }

    /** True when entities should use the 26.2 linear smoothing right now (module on, by the player or forced by the server). */
    public static boolean active() {
        com.thelads.core.config.ModuleManager mm = com.thelads.core.config.ModuleManager.getInstance();
        if (mm == null) return false;
        Module m = mm.getModule(NAME);
        return m != null && m.isEnabled();
    }
}
