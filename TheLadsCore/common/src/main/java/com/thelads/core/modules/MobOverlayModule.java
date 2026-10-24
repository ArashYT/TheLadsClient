package com.thelads.core.modules;

import com.thelads.core.client.MobOverlay;
import com.thelads.core.config.BoolOption;
import com.thelads.core.config.ColorOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.SliderOption;

public class MobOverlayModule extends Module {
    public final ColorOption hostileColor;
    public final ColorOption passiveColor;
    public final ColorOption playerColor;
    public final SliderOption outlineWidth;
    public final BoolOption throughWalls;

    public MobOverlayModule() {
        super(MobOverlay.MODULE_NAME, "Renders colored outlines and bounding box highlights on entities.");
        hostileColor = addOption(new ColorOption("Hostile Color", false, 0xFFFF4444));
        passiveColor = addOption(new ColorOption("Passive Color", false, 0xFF44FF44));
        playerColor = addOption(new ColorOption("Player Color", false, 0xFF4444FF));
        outlineWidth = addOption(new SliderOption("Outline Width", 2.0, 1.0, 5.0, 0.5));
        throughWalls = addOption(new BoolOption("Through Walls", false));
        MobOverlay.setModule(this);
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        MobOverlay.refresh();
    }
}
