package com.thelads.core.modules;

import com.thelads.core.config.CycleOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.Option;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/** Replaces the external Toggle Nametags mod — hide nametags via keybind/module. */
public class ToggleNametagsModule extends Module {

    public ToggleNametagsModule() {
        super("ToggleNametags", "Hide nametags above players and mobs (toggle with keybind).");
    }

    /** Queried by EntityRendererMixin for every nametag render check. */
    public static boolean shouldHide(Entity entity) {
        Module m = ModuleManager.getInstance().getModule("ToggleNametags");
        if (!(m instanceof ToggleNametagsModule tn) || !tn.isEnabled()) return false;

        Option o = tn.getOption("Hide");
        int idx = (o instanceof CycleOption c) ? c.getIndex() : 0;
        boolean isPlayer = entity instanceof Player;
        return switch (idx) {
            case 1 -> isPlayer;   // Players only
            case 2 -> !isPlayer;  // Mobs only
            default -> true;      // Everything
        };
    }
}
