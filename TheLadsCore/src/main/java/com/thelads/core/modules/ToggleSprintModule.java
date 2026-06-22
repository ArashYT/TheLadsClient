package com.thelads.core.modules;

import com.thelads.core.config.BoolOption;
import com.thelads.core.config.DropdownOption;
import com.thelads.core.config.SliderOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.Option;
import net.minecraft.client.Minecraft;

/**
 * Sprint toggle. With Mode = "Toggle" (default) the dedicated Toggle-Sprint keybind flips
 * sprint on/off (no holding); the vanilla Sprint key still works as hold-to-sprint. Mode =
 * "Always" reproduces the old always-sprint behaviour.
 */
public class ToggleSprintModule extends Module {
    private boolean toggled = false;
    private boolean lastWantSprint = false;

    public ToggleSprintModule() {
        super("ToggleSprint", "Toggle sprint with a keybind (no holding).");
    }

    /** Called when the Toggle-Sprint keybind is pressed. */
    public void onToggleKey() { toggled = !toggled; }
    public boolean isToggled() { return toggled; }

    public void tick(Minecraft mc) {
        if (mc == null || mc.options == null || mc.player == null) return;
        if (!isEnabled()) {
            if (toggled || lastWantSprint) { mc.options.keySprint.setDown(false); toggled = false; lastWantSprint = false; }
            return;
        }

        int mode = optCycle("Mode", 0); // 0 = Toggle, 1 = Always
        boolean wantSprint = (mode == 1) || toggled;

        if (optBool("Disable on sneak", true) && mc.player.isCrouching()) {
            wantSprint = false;
        }

        if (wantSprint) {
            mc.options.keySprint.setDown(true);
        } else if (lastWantSprint) {
            mc.options.keySprint.setDown(false); // release once; leave the vanilla hold key alone afterward
        }
        lastWantSprint = wantSprint;
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
