package com.thelads.core.modules;

import com.thelads.core.config.BoolOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.SliderOption;

/** Player LOD: other players far from the camera skip parts nobody can see at that range (see {@link com.thelads.core.perf.PlayerLod}). */
public class PlayerLodModule extends Module {
    public static final String NAME = "Player LOD";
    public final BoolOption outer = addOption(new BoolOption("Skip outer skin layer", true));
    public final SliderOption outerDistance = addOption(new SliderOption("Outer skin distance", 24, 16, 128, 1));
    public final BoolOption trims = addOption(new BoolOption("Skip armor trims", true));
    public final SliderOption trimsDistance = addOption(new SliderOption("Armor trim distance", 32, 16, 128, 1));
    public final BoolOption capes = addOption(new BoolOption("Skip cape and elytra", true));
    public final SliderOption capesDistance = addOption(new SliderOption("Cape and elytra distance", 40, 16, 128, 1));
    public final BoolOption items = addOption(new BoolOption("Skip held and head items", true));
    public final SliderOption itemsDistance = addOption(new SliderOption("Held and head item distance", 48, 16, 128, 1));
    public final BoolOption names = addOption(new BoolOption("Hide name tags", false));
    public final SliderOption namesDistance = addOption(new SliderOption("Name tag distance", 32, 16, 128, 1));

    public PlayerLodModule() {
        super(NAME, "Skips the outer skin layer, armor trims, capes, elytra and held items on players far from you. Nothing within 16 blocks is touched.");
        setEnabled(true);
    }
}
