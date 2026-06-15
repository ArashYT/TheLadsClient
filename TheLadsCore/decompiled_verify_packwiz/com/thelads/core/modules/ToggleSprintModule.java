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

public class ToggleSprintModule
extends Module {
    public ToggleSprintModule() {
        super("ToggleSprint", "Always sprint without holding the key.");
    }

    public void tick(Minecraft mc) {
        if (mc == null || mc.options == null || mc.player == null) {
            return;
        }
        if (!this.isEnabled()) {
            return;
        }
        boolean disableOnSneak = this.optBool("Disable on sneak", true);
        if (disableOnSneak && mc.player.isCrouching()) {
            mc.options.keySprint.setDown(false);
        } else {
            mc.options.keySprint.setDown(true);
        }
    }

    private boolean optBool(String name, boolean def) {
        Option o = this.getOption(name);
        return o instanceof BoolOption ? ((BoolOption)o).get() : def;
    }
}

