package com.thelads.core.modules;

import com.google.gson.JsonElement;
import com.thelads.core.config.Module;
import com.thelads.core.config.SliderOption;

public class FullbrightModule extends Module {
    /** Minecraft's own brightest setting is gamma 1; the slider runs from there (0%) to this (100%). */
    public static final double MAX_GAMMA = 15;
    private final SliderOption gamma;
    private final SliderOption multiplier;

    public FullbrightModule() {
        super("Fullbright", "Makes the dark world completely bright.");
        // Saved as a whole percentage in steps of 5. Before 1.7.2 "Gamma" was a plain 1-15 number saved as {"value": n}.
        gamma = addOption(new SliderOption("Gamma", 65, 0, 100, 5) {
            @Override public void load(JsonElement element) {
                if (element != null && element.isJsonObject() && element.getAsJsonObject().has("value")) {
                    try { setValue((element.getAsJsonObject().get("value").getAsDouble() - 1) / (MAX_GAMMA - 1) * 100); } catch (RuntimeException ignored) { }
                } else super.load(element);
            }
        }.percent());
        multiplier = addOption(new SliderOption("Brightness Multiplier", 1.0, 1.0, 10.0, 0.5));
    }

    /** The lightmap gamma the Gamma slider stands for: 0% is Minecraft's brightest setting (1), 100% is {@link #MAX_GAMMA}. */
    public double getGamma() {
        return 1 + (MAX_GAMMA - 1) * gamma.getValue() / 100;
    }

    /** What the lightmap gets, Brightness Multiplier included. */
    public double effectiveGamma() {
        return getGamma() * multiplier.getValue();
    }
}
