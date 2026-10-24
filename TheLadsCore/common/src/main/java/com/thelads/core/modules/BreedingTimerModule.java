package com.thelads.core.modules;

import com.thelads.core.config.BoolOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.SliderOption;

/**
 * Breeding Timer: displays countdown cooldown timers above animals indicating time until ready to breed or grow (parity with Badlion).
 * GATED: conveys hidden animal metadata.
 */
public class BreedingTimerModule extends Module {
    public static final String NAME = "Breeding Timer";

    public final BoolOption showSeconds;
    public final SliderOption range;
    public final SliderOption timerColor;

    public BreedingTimerModule() {
        super(NAME, "Displays countdown timers above animals indicating remaining breeding cooldown or baby growth time.");
        showSeconds = addOption(new BoolOption("Show seconds", true));
        range = addOption(new SliderOption("Range", 16, 4, 32, 2));
        timerColor = addOption(new SliderOption("Timer Color (RGB)", 0xFFFF55, 0, 0xFFFFFF, 1));
        setEnabled(false); // GATED: defaults off
    }
}
