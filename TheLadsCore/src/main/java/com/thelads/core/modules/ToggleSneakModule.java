package com.thelads.core.modules;

import com.thelads.core.config.BoolOption;
import com.thelads.core.config.DropdownOption;
import com.thelads.core.config.SliderOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.Option;
import net.minecraft.client.Minecraft;

/**
 * Sneak toggle. With Mode = "Toggle" (default) the dedicated Toggle-Sneak keybind flips
 * sneak on/off (no holding). The vanilla Sneak key still works as hold-to-sneak. Mode =
 * "Always" reproduces the old always-sneak behaviour. While toggled on, the crouch pose is
 * kept even when an inventory / pause screen is open.
 */
public class ToggleSneakModule extends Module {
    private boolean toggled = false;
    private boolean lastWantSneak = false;

    public ToggleSneakModule() {
        super("ToggleSneak", "Toggle sneak with a keybind (no holding); stays sneaking in menus.");
    }

    /** Called when the Toggle-Sneak keybind is pressed. */
    public void onToggleKey() { toggled = !toggled; }
    public boolean isToggled() { return toggled; }

    public void tick(Minecraft mc) {
        if (mc == null || mc.options == null || mc.player == null) return;
        if (!isEnabled()) {
            if (toggled || lastWantSneak) { mc.options.keyShift.setDown(false); toggled = false; lastWantSneak = false; }
            return;
        }

        int mode = optCycle("Mode", 0); // 0 = Toggle, 1 = Always
        boolean wantSneak = (mode == 1) || toggled;

        // Optionally cancel sneak when jumping.
        if (optBool("Disable on jump", false) && mc.player.getDeltaMovement().y > 0.05) {
            wantSneak = false;
            toggled = false;
        }

        if (wantSneak) {
            mc.options.keyShift.setDown(true);
            // Keep crouching even while a screen is open (vanilla releases sneak otherwise).
            if (mc.gui.screen() != null) mc.player.setShiftKeyDown(true);
        } else if (lastWantSneak) {
            // Just turned off: release once. When steady-off we don't touch the key,
            // so the vanilla hold-to-sneak key keeps working normally.
            mc.options.keyShift.setDown(false);
        }
        lastWantSneak = wantSneak;
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
