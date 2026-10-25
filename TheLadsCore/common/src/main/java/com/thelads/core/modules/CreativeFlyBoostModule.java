package com.thelads.core.modules;

import com.thelads.core.config.Module;
import com.thelads.core.config.SliderOption;

/**
 * Creative Fly Boost multiplier (gated, default OFF): scales flying speed in creative mode.
 * Gated by HypixelSafetyManager under anti-ban rules (modifies movement speeds seen by servers).
 */
public class CreativeFlyBoostModule extends Module {
    public static final String NAME = "Creative Fly Boost";

    public final SliderOption multiplier;

    public CreativeFlyBoostModule() {
        super(NAME, "Scales flying speed in creative mode, gated by server safety rules.");
        multiplier = addOption(new SliderOption("Multiplier", 2.0, 1.0, 5.0, 0.1));
        setEnabled(false);
    }
}
