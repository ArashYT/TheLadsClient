package com.thelads.core.modules;

import com.thelads.core.config.BoolOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.SliderOption;

/**
 * Custom FOV: how much of each of the game's own field-of-view changes to keep, from 0 (none) to 100% (the game's). Each version
 * hands a change to these helpers where the game makes it (flying's 1.1, the sprint and Speed/Slowness parts of movement speed,
 * the bow's pull, the spyglass and water), so the game's smoothing, its FOV Effects slider (26.x) and Lads Zoom still apply on top.
 */
public class CustomFovModule extends Module {
    public static final String NAME = "Custom FOV";
    public static final String SPRINTING = "Sprinting", EFFECTS = "Speed Effects", FLYING = "Flying", BOW = "Bow Aiming",
        SPYGLASS = "Spyglass", UNDERWATER = "Underwater";

    public CustomFovModule() {
        super(NAME, "Choose how much sprinting, flying, Speed and Slowness, aiming a bow or spyglass and water change your FOV.");
        addOption(new BoolOption("Static FOV", false));
        for (String change : new String[] {SPRINTING, EFFECTS, FLYING, BOW, SPYGLASS, UNDERWATER})
            addOption(new SliderOption(change, 100, 0, 100, 5));
        setEnabled(true);
    }

    /** The share (0 to 1) of an FOV change to keep: the game's whole change while the module is off. */
    public double share(String change) {
        if (!isEnabled()) return 1;
        if (getOption("Static FOV") instanceof BoolOption still && still.get()) return 0;
        return getOption(change) instanceof SliderOption slider ? slider.getValue() / 100 : 1;
    }

    /** A multiplicative FOV change (flying's 1.1 is 10% wider) kept at {@code share}: 0 is no change, 1 the game's own. */
    public static double scaled(double change, double share) {
        return 1 + (change - 1) * share;
    }

    /**
     * Movement speed, as the FOV reads it, with its sprint and Speed/Slowness multipliers (1 when absent) kept at their shares;
     * everything else that changes the speed stays.
     */
    public static double speed(double speed, double sprint, double effects, double sprintShare, double effectShare) {
        if (sprint <= 0 || effects <= 0) return speed;
        return speed / sprint / effects * scaled(sprint, sprintShare) * scaled(effects, effectShare);
    }
}
