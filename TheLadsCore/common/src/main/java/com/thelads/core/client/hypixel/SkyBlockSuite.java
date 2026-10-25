package com.thelads.core.client.hypixel;

import com.thelads.core.client.HypixelSafetyManager;
import com.thelads.core.config.BoolOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.Option;

/**
 * SkyBlock Suite controller: encapsulates settings and anti-ban safety gating for client-side
 * SkyBlock utilities (Dungeon Map, Terminal Solvers, Garden Timers).
 * All features requiring an official Hypixel API key are omitted.
 */
public final class SkyBlockSuite {
    public static final String MODULE_NAME = "SkyBlock Suite";
    public static final String OPTION_SOLVERS = "Terminal Solvers";

    private SkyBlockSuite() {}

    public static boolean isEnabled() {
        Module m = ModuleManager.getInstance().getModule(MODULE_NAME);
        if (m == null || !m.isEnabled()) return false;
        return !HypixelSafetyManager.isDisallowed(MODULE_NAME);
    }

    public static boolean isTerminalSolversEnabled() {
        if (!isEnabled()) return false;
        Module m = ModuleManager.getInstance().getModule(MODULE_NAME);
        Option opt = m != null ? m.getOption(OPTION_SOLVERS) : null;
        return opt instanceof BoolOption b && b.get();
    }
}
