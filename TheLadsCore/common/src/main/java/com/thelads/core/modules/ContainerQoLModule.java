package com.thelads.core.modules;

import com.thelads.core.config.BoolOption;
import com.thelads.core.config.Module;

/**
 * 1.8.9 Quality-of-Life patches: click outside container to close chest/inventory, camera parallax fix (parity with PolyPatcher).
 */
public class ContainerQoLModule extends Module {
    public static final String NAME = "Container QoL";

    public final BoolOption clickOutsideToClose;
    public final BoolOption cameraParallaxFix;

    public ContainerQoLModule() {
        super(NAME, "Container and camera quality of life tweaks (click outside container to close, parallax fixes).");
        clickOutsideToClose = addOption(new BoolOption("Click outside to close", true));
        cameraParallaxFix = addOption(new BoolOption("Camera parallax fix", true));
        setEnabled(false);
    }
}
