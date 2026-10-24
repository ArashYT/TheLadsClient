package com.thelads.core.modules;

import com.thelads.core.client.UhcOverlay;
import com.thelads.core.config.BoolOption;
import com.thelads.core.config.ColorOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.SliderOption;

public class UhcOverlayModule extends Module {
    public final SliderOption scaleFactor;
    public final BoolOption highlightGoldenApples;
    public final BoolOption highlightHeads;
    public final BoolOption highlightOres;
    public final ColorOption borderColor;

    public UhcOverlayModule() {
        super(UhcOverlay.MODULE_NAME, "Scales and highlights golden apples, player heads and ores in inventory screens.");
        scaleFactor = addOption(new SliderOption("Scale Factor", 1.3, 1.1, 2.0, 0.1));
        highlightGoldenApples = addOption(new BoolOption("Highlight Golden Apples", true));
        highlightHeads = addOption(new BoolOption("Highlight Player Heads", true));
        highlightOres = addOption(new BoolOption("Highlight Ores", true));
        borderColor = addOption(new ColorOption("Border Color", false, 0xFFFFD700));
        UhcOverlay.setModule(this);
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        UhcOverlay.refresh();
    }
}
