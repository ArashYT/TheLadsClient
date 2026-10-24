package com.thelads.core.modules;

import com.thelads.core.client.ScreenEffects;
import com.thelads.core.config.BoolOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.SliderOption;

/**
 * Menu Blur (Lunar, Feather, Badlion): blurs the world behind in-game menus, as Lunar's options: Blur Strength 0-20 (default 4) and
 * on/off for inventories (and other containers), the pause menu and every other in-game screen; on by default as Lunar's. Chat and
 * the title screen are never blurred. 26.x blurs its own menus (Video Settings > Menu Background Blur): there this module, while on,
 * sets that blur's strength and adds it to inventories, which 26.x leaves sharp. Visual only.
 */
public final class MenuBlurModule extends Module {
    public static final String NAME = "MenuBlur";

    public final SliderOption strength = addOption(new SliderOption("Blur Strength", 4, 0, 20, 1));
    public final BoolOption inventories = addOption(new BoolOption("Inventories", true));
    public final BoolOption pause = addOption(new BoolOption("Pause Menu", true));
    public final BoolOption other = addOption(new BoolOption("Other Menus", true));

    public MenuBlurModule() {
        super(NAME, "Blurs the world behind inventories, the pause menu and other in-game menus.");
        setEnabled(true);
    }

    /** The blur radius (pixels) for a screen of this kind ({@link ScreenEffects#INVENTORY}, PAUSE or OTHER); 0 = none. */
    public int radius(int kind) {
        if (!isEnabled()) return 0;
        BoolOption on = kind == ScreenEffects.INVENTORY ? inventories : kind == ScreenEffects.PAUSE ? pause : other;
        return on.get() ? strength.getIntValue() : 0;
    }
}
