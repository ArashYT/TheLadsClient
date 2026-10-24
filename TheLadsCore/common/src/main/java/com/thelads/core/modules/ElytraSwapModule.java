package com.thelads.core.modules;

import com.thelads.core.config.Module;

/**
 * Elytra Swap (gated, default OFF): swaps chestplate and elytra in armor slot.
 * Gated by HypixelSafetyManager under anti-ban rules (must never click with no screen open on untrusted servers).
 */
public class ElytraSwapModule extends Module {
    public static final String NAME = "Elytra Swap";

    public ElytraSwapModule() {
        super(NAME, "Hotkey to swap chestplate and elytra in armor slot.");
        setEnabled(false);
    }
}
