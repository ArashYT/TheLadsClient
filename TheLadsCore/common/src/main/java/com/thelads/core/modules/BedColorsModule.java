package com.thelads.core.modules;

import com.thelads.core.config.BoolOption;
import com.thelads.core.config.ColorOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.SliderOption;

public final class BedColorsModule extends Module {
    public static final String NAME = "Bed Colors";

    public final BoolOption teamColors = addOption(new BoolOption("Bed Wars team colors", true));
    public final ColorOption customColor = addOption(new ColorOption("Custom color", false, 0xE74C3C));
    public final BoolOption destructionVisuals = addOption(new BoolOption("Bed destruction burst", true));
    public final SliderOption particleCount = addOption(new SliderOption("Burst particles", 25, 10, 60, 5));

    public BedColorsModule() {
        super(NAME, "Renders colored beds in Minecraft 1.8.9 based on Bed Wars team assignments or dye variants, and emits destruction burst particles.");
    }

    public static BedColorsModule get() {
        Module mod = ModuleManager.getInstance().getModule(NAME);
        return mod instanceof BedColorsModule ? (BedColorsModule) mod : null;
    }

    public static boolean isActive() {
        BedColorsModule mod = get();
        return mod != null && mod.isEnabled();
    }
}
