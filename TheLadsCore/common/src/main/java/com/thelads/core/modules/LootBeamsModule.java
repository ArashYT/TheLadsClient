package com.thelads.core.modules;

import com.thelads.core.client.LootBeams;
import com.thelads.core.config.BoolOption;
import com.thelads.core.config.ColorOption;
import com.thelads.core.config.DropdownOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.SliderOption;

public class LootBeamsModule extends Module {
    public final DropdownOption beamStyle;
    public final SliderOption beamHeight;
    public final BoolOption colorByRarity;
    public final ColorOption customColor;
    public final SliderOption beamOpacity;

    public LootBeamsModule() {
        super(LootBeams.MODULE_NAME, "Renders vertical beacon-style light beams rising from dropped items.");
        beamStyle = addOption(new DropdownOption("Beam Style", 0, "Solid", "Gradient", "Pulsing"));
        beamHeight = addOption(new SliderOption("Beam Height", 2.0, 1.0, 5.0, 0.5));
        colorByRarity = addOption(new BoolOption("Color by Rarity", true));
        customColor = addOption(new ColorOption("Custom Color", false, 0xFFFFAA00));
        beamOpacity = addOption(new SliderOption("Beam Opacity", 75, 10, 100, 5).percent());
        LootBeams.setModule(this);
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        LootBeams.refresh();
    }
}
