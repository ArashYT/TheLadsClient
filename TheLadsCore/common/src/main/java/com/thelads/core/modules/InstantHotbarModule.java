package com.thelads.core.modules;

import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;

/**
 * Instant Hotbar (default ON): Lads remake of Kenny's Hotbar Optimizer. A number key changes the selected slot when it is pressed,
 * in press order, instead of on the next client tick. Kill switch: -Dlads.instanthotbar=false.
 * No HypixelSafetyManager gate: only the client-side selection moves earlier, the carried-item packet is still the vanilla per-tick one.
 */
public class InstantHotbarModule extends Module {
    public static final String NAME = "Instant Hotbar";
    private static final boolean KILL_SWITCH = !"false".equalsIgnoreCase(System.getProperty("lads.instanthotbar"));
    private static InstantHotbarModule cached;
    private static boolean logged;

    public InstantHotbarModule() {
        super(NAME, "Number keys switch the hotbar slot the moment they are pressed, in press order, not on the next tick.");
        setEnabled(true);
    }

    /** True when hotbar keys should be applied at press time. */
    public static boolean active() {
        if (!KILL_SWITCH) return false;
        InstantHotbarModule m = cached;
        if (m == null) {
            if (!(ModuleManager.getInstance().getModule(NAME) instanceof InstantHotbarModule)) return false;
            cached = m = (InstantHotbarModule) ModuleManager.getInstance().getModule(NAME);
        }
        return m.isEnabled();
    }

    /** One QA log line, the first time a slot is applied at press time. */
    public static void applied(int slot) {
        if (logged) return;
        logged = true;
        org.slf4j.LoggerFactory.getLogger("TheLadsCore").info("[Lads] Instant Hotbar: slot {} applied at key press", slot + 1);
    }
}
