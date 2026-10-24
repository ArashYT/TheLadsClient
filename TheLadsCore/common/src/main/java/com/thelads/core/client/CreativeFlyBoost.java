package com.thelads.core.client;

import com.thelads.core.config.ModuleManager;
import com.thelads.core.modules.CreativeFlyBoostModule;

/**
 * Creative fly boost multiplier calculation (parity-1023).
 * Scales abilities.flying speed, strictly gated by HypixelSafetyManager.
 */
public final class CreativeFlyBoost {
    private static CreativeFlyBoostModule cachedModule;

    private CreativeFlyBoost() {}

    public static CreativeFlyBoostModule module() {
        CreativeFlyBoostModule m = cachedModule;
        if (m == null) {
            if (ModuleManager.getInstance().getModule(CreativeFlyBoostModule.NAME) instanceof CreativeFlyBoostModule found) {
                cachedModule = m = found;
            }
        }
        return m;
    }

    public static boolean isAllowed() {
        CreativeFlyBoostModule m = module();
        return m != null && m.isEnabled() && HypixelSafetyManager.isAllowed(CreativeFlyBoostModule.NAME);
    }

    public static float getMultiplier() {
        if (!isAllowed()) return 1.0F;
        CreativeFlyBoostModule m = module();
        return m != null ? (float) m.multiplier.getValue() : 1.0F;
    }
}
