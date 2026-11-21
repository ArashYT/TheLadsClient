package com.thelads.core.modules;

import com.thelads.core.client.HypixelSafetyManager;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;

/**
 * Offhand Key (default OFF): Lads remake of OFFHAND KeyBind. The Use key then uses the main hand only and a separate
 * "Use Offhand Item" key uses the off hand only. Sends use packets a vanilla client would not, so it is gated by HypixelSafetyManager.
 */
public class OffhandKeyModule extends Module {
    public static final String NAME = "Offhand Key";
    private static OffhandKeyModule cached;
    private static boolean logged;

    public OffhandKeyModule() {
        super(NAME, "Use key uses the main hand only; a separate key uses the off hand only. Unbound by default.");
        setEnabled(false);
    }

    /** True when split hands apply right now: module on and permitted on this server. */
    public static boolean active() {
        OffhandKeyModule m = cached;
        if (m == null) {
            if (!(ModuleManager.getInstance().getModule(NAME) instanceof OffhandKeyModule)) return false;
            cached = m = (OffhandKeyModule) ModuleManager.getInstance().getModule(NAME);
        }
        return m.isEnabled() && HypixelSafetyManager.isAllowed(NAME);
    }

    /** One QA log line, the first time the off-hand key uses an item. */
    public static void used() {
        if (logged) return;
        logged = true;
        org.slf4j.LoggerFactory.getLogger("TheLadsCore").info("[Lads] Offhand Key: off hand used with its own key, Use key is main hand only");
    }
}
