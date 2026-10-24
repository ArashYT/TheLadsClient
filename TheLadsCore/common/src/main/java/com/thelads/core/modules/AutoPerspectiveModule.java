package com.thelads.core.modules;

import com.thelads.core.config.BoolOption;
import com.thelads.core.config.Module;

public final class AutoPerspectiveModule extends Module {
    public static final String NAME = "AutoPerspective";

    public final BoolOption autoThirdPerson;
    public final BoolOption smoothCamera;

    public AutoPerspectiveModule() {
        super(NAME, "Automatically switches to third-person perspective when riding boats, horses, or gliding.");
        autoThirdPerson = addOption(new BoolOption("Auto Third Person", true));
        smoothCamera = addOption(new BoolOption("Cinematic Camera When Riding", false));
        setEnabled(false);
    }

    public boolean isAutoThirdPerson() {
        return isEnabled() && autoThirdPerson.get();
    }

    public boolean isSmoothCamera() {
        return isEnabled() && smoothCamera.get();
    }
}
