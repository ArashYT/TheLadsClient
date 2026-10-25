package com.thelads.core.modules;

import com.thelads.core.config.BoolOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.SliderOption;

public final class MlgCobwebModule extends Module {
    public static final String NAME = "MLG Cobweb";

    public final BoolOption waterBucket = addOption(new BoolOption("Water bucket", true));
    public final BoolOption cobweb = addOption(new BoolOption("Cobweb", true));
    public final BoolOption slimeBlock = addOption(new BoolOption("Slime block", true));
    public final BoolOption hayBale = addOption(new BoolOption("Hay bale", true));
    public final SliderOption minFallDistance = addOption(new SliderOption("Min fall distance", 4, 2, 10, 1));
    public final BoolOption timingReticle = addOption(new BoolOption("Timing reticle", true));
    public final BoolOption ghostBox = addOption(new BoolOption("Ghost placement box", true));
    public final com.thelads.core.config.ColorOption boxColor = addOption(new com.thelads.core.config.ColorOption("Box color", false, 0x4D00FFCC));

    public MlgCobwebModule() {
        super(NAME, "Provides a helper indicator and placement box for cobwebs and water to break fatal falls.");
    }

    public static MlgCobwebModule get() {
        Module mod = ModuleManager.getInstance().getModule(NAME);
        return mod instanceof MlgCobwebModule ? (MlgCobwebModule) mod : null;
    }

    public static boolean isActive() {
        MlgCobwebModule mod = get();
        return mod != null && mod.isEnabled() && !com.thelads.core.client.HypixelSafetyManager.isDisallowed(NAME);
    }
}
