package com.thelads.core.modules;

import com.thelads.core.client.RenderTweaks;
import com.thelads.core.config.BoolOption;
import com.thelads.core.config.Module;

public class RenderTweaksModule extends Module {
    public final BoolOption entityShadows;
    public final BoolOption hideLightning;
    public final BoolOption hideFishingLine;
    public final BoolOption removeElderGuardianCurse;
    public final BoolOption bobbingDisconnect;

    public RenderTweaksModule() {
        super(RenderTweaks.MODULE_NAME, "Entity shadows, lightning hiding, fishing line hiding, bobbing disconnect and curse removal.");
        entityShadows = addOption(new BoolOption("Entity Shadows", true));
        hideLightning = addOption(new BoolOption("Hide Lightning", false));
        hideFishingLine = addOption(new BoolOption("Hide Fishing Line", false));
        removeElderGuardianCurse = addOption(new BoolOption("Remove Elder Guardian Curse", true));
        bobbingDisconnect = addOption(new BoolOption("Bobbing Disconnect", false));
        RenderTweaks.setModule(this);
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        RenderTweaks.refresh();
    }
}
