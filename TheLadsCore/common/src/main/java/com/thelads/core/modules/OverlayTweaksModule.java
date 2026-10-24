package com.thelads.core.modules;

import com.thelads.core.client.OverlayTweaks;
import com.thelads.core.config.BoolOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.SliderOption;

public class OverlayTweaksModule extends Module {
    public final SliderOption fireOpacity;
    public final SliderOption fireHeight;
    public final SliderOption pumpkinOpacity;
    public final SliderOption portalOpacity;
    public final SliderOption waterFogOpacity;
    public final BoolOption clearWater;
    public final BoolOption fireResistanceFade;

    public OverlayTweaksModule() {
        super(OverlayTweaks.MODULE_NAME, "Adjust opacity of fire, pumpkin blur, portal overlay, water fog and fire resistance fade.");
        fireOpacity = addOption(new SliderOption("Fire Opacity", 100, 0, 100, 5).percent());
        fireHeight = addOption(new SliderOption("Fire Height", 100, 0, 100, 5).percent());
        pumpkinOpacity = addOption(new SliderOption("Pumpkin Blur Opacity", 100, 0, 100, 5).percent());
        portalOpacity = addOption(new SliderOption("Portal Opacity", 100, 0, 100, 5).percent());
        waterFogOpacity = addOption(new SliderOption("Water Fog Opacity", 100, 0, 100, 5).percent());
        clearWater = addOption(new BoolOption("Clear Water", false));
        fireResistanceFade = addOption(new BoolOption("Fire Resistance Fade", true));
        OverlayTweaks.setModule(this);
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        OverlayTweaks.refresh();
    }
}
