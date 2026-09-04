package com.thelads.core.modules;

import com.thelads.core.config.DoubleOption;
import com.thelads.core.config.Module;

public class FullbrightModule extends Module {
    private final DoubleOption gamma;

    public FullbrightModule() {
        super("Fullbright", "Makes the dark world completely bright.");
        gamma = new DoubleOption("Gamma", 10.0, 1.0, 15.0);
        addOption(gamma);
    }

    public double getGamma() {
        return gamma.get();
    }
}
