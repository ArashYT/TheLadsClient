package com.thelads.core.modules;

import com.thelads.core.config.DropdownOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.Option;

public class ToggleSneakModule extends Module {
    private boolean toggled = false;

    public ToggleSneakModule() {
        super("ToggleSneak", "Toggle sneaking with a keybind (no holding).");
        addOption(new DropdownOption("Mode", 0, "Toggle", "Hold"));
    }

    public void onToggleKey() { toggled = !toggled; }
    public boolean isToggled() { return toggled; }

    public boolean evaluateSneak(boolean defaultSneak) {
        if (!isEnabled()) {
            toggled = false;
            return defaultSneak;
        }
        int mode = optCycle("Mode", 0);
        return (mode == 0) ? (toggled || defaultSneak) : defaultSneak;
    }

    private int optCycle(String name, int def) {
        Option o = getOption(name);
        return (o instanceof DropdownOption c) ? c.getIndex() : def;
    }
}
