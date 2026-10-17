package com.thelads.core.modules;

import com.thelads.core.config.Module;
import com.thelads.core.config.SliderOption;

public class FullbrightModule extends Module {
    /**
     * 1.9.10: one percentage, how far the lightmap is lifted toward full light. Before, any Gamma above 0% already set an 80% floor
     * (5% looked like 100%) and a Brightness Multiplier sat beside it; both are gone, so old saves start at the default.
     */
    private final SliderOption brightness;

    public FullbrightModule() {
        super("Fullbright", "Makes the dark world bright: 0% is Minecraft's brightest setting, 100% lights everything fully.");
        brightness = addOption(new SliderOption("Brightness", 100, 0, 100, 5).percent());
    }

    /** The lightmap gamma while Fullbright is on: Minecraft's own brightest setting; the Brightness slider lifts from there. */
    public double effectiveGamma() {
        return 1;
    }

    /** How far the lightmap is lifted toward full light, 0 to 1: the Brightness percentage, 0 while the module is off. */
    public float lightmapFactor() {
        return isEnabled() ? (float) (brightness.getValue() / 100) : 0;
    }
}
