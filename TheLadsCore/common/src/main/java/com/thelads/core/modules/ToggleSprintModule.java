package com.thelads.core.modules;

import com.thelads.core.config.BoolOption;
import com.thelads.core.config.DropdownOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.Option;

public class ToggleSprintModule extends Module {
    private boolean toggled = false;
    private boolean lastWantSprint = false;

    public ToggleSprintModule() {
        super("ToggleSprint", "Toggle sprint with a keybind (no holding).");
        addOption(new DropdownOption("Mode", 0, "Toggle", "Always"));
        addOption(new BoolOption("Disable on sneak", true));
    }

    public void onToggleKey() { toggled = !toggled; }
    public boolean isToggled() { return toggled; }

    public boolean evaluateSprint(boolean isCrouching) {
        if (!isEnabled()) {
            toggled = false;
            lastWantSprint = false;
            return false;
        }

        int mode = optCycle("Mode", 0);
        boolean wantSprint = (mode == 1) || toggled;
        if (optBool("Disable on sneak", true) && isCrouching) {
            wantSprint = false;
        }
        lastWantSprint = wantSprint;
        return wantSprint;
    }

    private boolean optBool(String name, boolean def) {
        Option o = getOption(name);
        return (o instanceof BoolOption b) ? b.get() : def;
    }

    private int optCycle(String name, int def) {
        Option o = getOption(name);
        return (o instanceof DropdownOption c) ? c.getIndex() : def;
    }
}
