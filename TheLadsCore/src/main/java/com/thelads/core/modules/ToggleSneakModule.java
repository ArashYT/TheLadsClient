package com.thelads.core.modules;

import com.thelads.core.config.BoolOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.Option;
import net.minecraft.client.Minecraft;

public class ToggleSneakModule extends Module {
    public ToggleSneakModule() {
        super("ToggleSneak", "Always sneak without holding the key.");
    }

    public void tick(Minecraft mc) {
        if (mc == null || mc.options == null || mc.player == null) return;
        if (!isEnabled()) return;

        boolean disableOnJump = optBool("Disable on jump", false);
        // Detect jumping by upward velocity (isOnGround may be unavailable at render-state level)
        if (disableOnJump && mc.player.getDeltaMovement().y > 0.05) {
            mc.options.keyShift.setDown(false);
        } else {
            mc.options.keyShift.setDown(true);
        }
    }

    private boolean optBool(String name, boolean def) {
        Option o = getOption(name);
        return (o instanceof BoolOption) ? ((BoolOption) o).get() : def;
    }
}
