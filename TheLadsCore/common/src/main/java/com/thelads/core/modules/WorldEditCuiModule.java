package com.thelads.core.modules;

import com.thelads.core.config.BoolOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.SliderOption;

/**
 * WorldEdit CUI: renders client-side 3D selection boxes for WorldEdit selections.
 * GATED: requires plugin channel communication with server.
 */
public class WorldEditCuiModule extends Module {
    public static final String NAME = "WorldEdit CUI";

    public final SliderOption boxColor;
    public final BoolOption showGrid;

    public WorldEditCuiModule() {
        super(NAME, "Client-side 3D visual bounding box rendering for WorldEdit selections.");
        boxColor = addOption(new SliderOption("Box Color (RGB)", 0x4400FF00, 0, 0xFFFFFF, 1));
        showGrid = addOption(new BoolOption("Show selection grid", true));
        setEnabled(false); // GATED: defaults off
    }
}
