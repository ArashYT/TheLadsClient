package com.thelads.core.modules;

import com.thelads.core.config.*;
import com.thelads.core.config.Module;

/**
 * OneClient VanillaHUD parity toggles.
 * Always-hardcore hearts, hide XP bar / level, persistent held item tooltip, and status effect icons layout.
 * Visual-only.
 */
public class VanillaHudModule extends Module {
    public static final String NAME = "VanillaHUD";

    public VanillaHudModule() {
        super(NAME, "Vanilla HUD toggles: hardcore hearts, hide XP bar/level, persistent held item name, and status icons.");
        addOption(new BoolOption("Always Hardcore Hearts", false));
        addOption(new BoolOption("Hide XP Bar", false));
        addOption(new BoolOption("Hide XP Level", false));
        addOption(new BoolOption("Persistent Item Name", false));
        addOption(new BoolOption("Hide Status Effect Icons", false));
        addOption(new SliderOption("Status Effects X Offset", 0, -300, 300, 1));
        addOption(new SliderOption("Status Effects Y Offset", 0, -300, 300, 1));
        setEnabled(false);
    }

    public static boolean isAlwaysHardcoreHearts() {
        Module m = ModuleManager.getInstance().getModule(NAME);
        if (m == null || !m.isEnabled()) return false;
        Option o = m.getOption("Always Hardcore Hearts");
        return (o instanceof BoolOption bo) ? bo.get() : false;
    }

    public static boolean isHideXpBar() {
        Module m = ModuleManager.getInstance().getModule(NAME);
        if (m == null || !m.isEnabled()) return false;
        Option o = m.getOption("Hide XP Bar");
        return (o instanceof BoolOption bo) ? bo.get() : false;
    }

    public static boolean isHideXpLevel() {
        Module m = ModuleManager.getInstance().getModule(NAME);
        if (m == null || !m.isEnabled()) return false;
        Option o = m.getOption("Hide XP Level");
        return (o instanceof BoolOption bo) ? bo.get() : false;
    }

    public static boolean isPersistentItemName() {
        Module m = ModuleManager.getInstance().getModule(NAME);
        if (m == null || !m.isEnabled()) return false;
        Option o = m.getOption("Persistent Item Name");
        return (o instanceof BoolOption bo) ? bo.get() : false;
    }

    public static boolean isHideStatusEffectIcons() {
        Module m = ModuleManager.getInstance().getModule(NAME);
        if (m == null || !m.isEnabled()) return false;
        Option o = m.getOption("Hide Status Effect Icons");
        return (o instanceof BoolOption bo) ? bo.get() : false;
    }

    public static int getStatusEffectsXOffset() {
        Module m = ModuleManager.getInstance().getModule(NAME);
        if (m == null || !m.isEnabled()) return 0;
        Option o = m.getOption("Status Effects X Offset");
        return (o instanceof SliderOption so) ? so.getIntValue() : 0;
    }

    public static int getStatusEffectsYOffset() {
        Module m = ModuleManager.getInstance().getModule(NAME);
        if (m == null || !m.isEnabled()) return 0;
        Option o = m.getOption("Status Effects Y Offset");
        return (o instanceof SliderOption so) ? so.getIntValue() : 0;
    }
}
