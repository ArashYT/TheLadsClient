/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.world.entity.Entity
 *  net.minecraft.world.entity.player.Player
 */
package com.thelads.core.modules;

import com.thelads.core.config.CycleOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.Option;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

public class ToggleNametagsModule
extends Module {
    public ToggleNametagsModule() {
        super("ToggleNametags", "Hide nametags above players and mobs (toggle with keybind).");
    }

    public static boolean shouldHide(Entity entity) {
        int n;
        ToggleNametagsModule tn;
        Module m = ModuleManager.getInstance().getModule("ToggleNametags");
        if (!(m instanceof ToggleNametagsModule) || !(tn = (ToggleNametagsModule)m).isEnabled()) {
            return false;
        }
        Option o = tn.getOption("Hide");
        if (o instanceof CycleOption) {
            CycleOption c = (CycleOption)o;
            n = c.getIndex();
        } else {
            n = 0;
        }
        int idx = n;
        boolean isPlayer = entity instanceof Player;
        return switch (idx) {
            case 1 -> isPlayer;
            case 2 -> {
                if (!isPlayer) {
                    yield true;
                }
                yield false;
            }
            default -> true;
        };
    }
}

