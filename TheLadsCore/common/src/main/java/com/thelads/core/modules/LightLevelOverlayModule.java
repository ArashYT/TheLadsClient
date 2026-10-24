package com.thelads.core.modules;

import com.thelads.core.client.LightLevelOverlay;
import com.thelads.core.config.BoolOption;
import com.thelads.core.config.ColorOption;
import com.thelads.core.config.DropdownOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.SliderOption;

public class LightLevelOverlayModule extends Module {
    public final DropdownOption mode;
    public final SliderOption renderDistance;
    public final BoolOption showSafeBlocks;
    public final ColorOption nightSpawnColor;
    public final ColorOption alwaysSpawnColor;

    public LightLevelOverlayModule() {
        super(LightLevelOverlay.MODULE_NAME, "Visualize block light levels and mob spawn markers (yellow=night spawn, red=always spawn).");
        mode = addOption(new DropdownOption("Display Mode", 0, "Crosses", "Numbers", "Both"));
        renderDistance = addOption(new SliderOption("Render Distance", 16, 4, 32, 2));
        showSafeBlocks = addOption(new BoolOption("Show Safe Blocks", false));
        nightSpawnColor = addOption(new ColorOption("Night Spawn Color", false, 0xFFFFFF00));
        alwaysSpawnColor = addOption(new ColorOption("Always Spawn Color", false, 0xFFFF0000));
        LightLevelOverlay.setModule(this);
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        LightLevelOverlay.refresh();
    }
}
