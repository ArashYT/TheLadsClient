package com.thelads.core.modules;

import com.thelads.core.config.*;
import com.thelads.core.config.Module;

/**
 * Feather-parity Toast Control module.
 * Suppresses or repositions recipe unlocks, tutorial, and advancement toast alerts.
 * Visual-only.
 */
public class ToastControlModule extends Module {
    public static final String NAME = "ToastControl";

    public ToastControlModule() {
        super(NAME, "Disable or reposition recipe, tutorial and advancement pop-up toast notifications.");
        addOption(new BoolOption("Hide Recipe Toasts", true));
        addOption(new BoolOption("Hide Tutorial Toasts", true));
        addOption(new BoolOption("Hide Advancement Toasts", false));
        addOption(new BoolOption("Hide All Toasts", false));
        addOption(new DropdownOption("Position", 0, "Top Right", "Top Left", "Bottom Right", "Bottom Left"));
        addOption(new SliderOption("X Offset", 0, -300, 300, 1));
        addOption(new SliderOption("Y Offset", 0, -300, 300, 1));
        setEnabled(false);
    }

    public static boolean shouldHide(String toastType) {
        Module m = ModuleManager.getInstance().getModule(NAME);
        if (m == null || !m.isEnabled()) return false;
        Option all = m.getOption("Hide All Toasts");
        if (all instanceof BoolOption bo && bo.get()) return true;

        if (toastType == null) return false;
        String lower = toastType.toLowerCase(java.util.Locale.ROOT);
        if (lower.contains("recipe")) {
            Option o = m.getOption("Hide Recipe Toasts");
            return (o instanceof BoolOption bo) && bo.get();
        }
        if (lower.contains("tutorial")) {
            Option o = m.getOption("Hide Tutorial Toasts");
            return (o instanceof BoolOption bo) && bo.get();
        }
        if (lower.contains("advancement") || lower.contains("achievement")) {
            Option o = m.getOption("Hide Advancement Toasts");
            return (o instanceof BoolOption bo) && bo.get();
        }
        return false;
    }

    public static int getXOffset() {
        Module m = ModuleManager.getInstance().getModule(NAME);
        if (m == null || !m.isEnabled()) return 0;
        Option o = m.getOption("X Offset");
        return (o instanceof SliderOption so) ? so.getIntValue() : 0;
    }

    public static int getYOffset() {
        Module m = ModuleManager.getInstance().getModule(NAME);
        if (m == null || !m.isEnabled()) return 0;
        Option o = m.getOption("Y Offset");
        return (o instanceof SliderOption so) ? so.getIntValue() : 0;
    }
}
