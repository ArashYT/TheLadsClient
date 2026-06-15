/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.Minecraft
 */
package com.thelads.core.modules;

import com.thelads.core.config.BoolOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.Option;
import net.minecraft.client.Minecraft;

public class ToggleSneakModule
extends Module {
    public ToggleSneakModule() {
        super("ToggleSneak", "Always sneak without holding the key.");
    }

    public void tick(Minecraft mc) {
        if (mc == null || mc.options == null || mc.player == null) {
            return;
        }
        if (!this.isEnabled()) {
            return;
        }
        boolean disableOnJump = this.optBool("Disable on jump", false);
        if (disableOnJump && mc.player.getDeltaMovement().y > 0.05) {
            mc.options.keyShift.setDown(false);
        } else {
            mc.options.keyShift.setDown(true);
        }
    }

    private boolean optBool(String name, boolean def) {
        Option o = this.getOption(name);
        return o instanceof BoolOption ? ((BoolOption)o).get() : def;
    }
}

