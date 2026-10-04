package com.thelads.core.modules;

import com.thelads.core.config.Module;
import com.thelads.core.config.Option;
import com.thelads.core.config.SliderOption;
import com.thelads.core.config.DropdownOption;
import com.thelads.core.config.BoolOption;

/**
 * Dynamic Lights. On 26.x Lads lights the world itself (client DynamicLights); on 1.8.9 the module switches OptiFine's own
 * Dynamic Lights, which has only Off, Fast and Fancy. On by default with Fancy, entities on and torches going out underwater,
 * as LambDynamicLights (which it replaces) shipped.
 */
public class DynamicLightsModule extends Module {
    public static final String NAME = "DynamicLights";
    /** OptiFine's values for its Dynamic Lights video setting (GameSettings.ofDynamicLights). */
    public static final int OPTIFINE_FAST = 1, OPTIFINE_FANCY = 2, OPTIFINE_OFF = 3;
    private static DynamicLightsModule INSTANCE;
    private final SliderOption radius;
    private final DropdownOption quality;
    private final BoolOption entities, droppedItems, underwater;
    private boolean optiFine;

    public DynamicLightsModule() {
        super(NAME, "Held torches and lanterns, burning mobs and dropped glowing items light up the world around them as they move.");
        radius = addOption(new SliderOption("Light Radius", 15.0, 5.0, 30.0, 1.0));
        quality = addOption(new DropdownOption("Quality", 1, "Fast", "Fancy"));
        entities = addOption(new BoolOption("Entities", true));
        droppedItems = addOption(new BoolOption("Dropped Items", true));
        underwater = addOption(new BoolOption("Underwater", false));
        setEnabled(true);
        INSTANCE = this;
    }

    public static DynamicLightsModule getInstance() {
        return INSTANCE;
    }

    /** How far a light reaches, in blocks. */
    public float radius() { return (float) radius.getValue(); }
    /** Fancy follows a moving light every tick; Fast every few ticks, once it moved half a block. */
    public boolean fancy() { return quality.getIndex() == 1; }
    /** Other players and mobs: what they hold, burning, and the mobs that glow (blazes, magma cubes, glow squids...). */
    public boolean entities() { return entities.get(); }
    public boolean droppedItems() { return droppedItems.get(); }
    /** Torches and campfires keep their light underwater. */
    public boolean underwater() { return underwater.get(); }

    /** 1.8.9: the module switches OptiFine's Dynamic Lights instead, which has no radius, entity, item or water settings. */
    public void drivesOptiFine() { optiFine = true; }

    /** The Lads menu leaves out what this game version has no setting for. Saved values stay for the other versions. */
    public boolean hidden(Option option) { return optiFine && option != quality; }

    /** OptiFine's setting for this module's state. */
    public int optiFineSetting() {
        return !isEnabled() ? OPTIFINE_OFF : fancy() ? OPTIFINE_FANCY : OPTIFINE_FAST;
    }

    /** Takes on OptiFine's setting (at start-up, and after a change in OptiFine's own Video Settings). */
    public void followOptiFine(int setting) {
        setEnabled(setting == OPTIFINE_FAST || setting == OPTIFINE_FANCY);
        if (isEnabled()) quality.setIndex(setting == OPTIFINE_FANCY ? 1 : 0);
    }
}
