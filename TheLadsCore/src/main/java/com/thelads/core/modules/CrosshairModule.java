package com.thelads.core.modules;

import com.thelads.core.config.ColorOption;
import com.thelads.core.config.DoubleOption;
import com.thelads.core.config.Module;

public class CrosshairModule extends Module {
    public final ColorOption color = new ColorOption("Color", false, 0xFFFFFFFF);
    public final DoubleOption scale = new DoubleOption("Scale", 1.0, 0.1, 5.0);
    public final DoubleOption thickness = new DoubleOption("Thickness", 1.0, 0.1, 10.0);

    public CrosshairModule() {
        super("Crosshair Tweaks", "Customize the appearance of the crosshair.");
        addOption(color);
        addOption(scale);
        addOption(thickness);
    }
}
