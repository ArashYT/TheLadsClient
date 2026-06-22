package com.thelads.core.modules;

import com.thelads.core.config.DropdownOption;
import com.thelads.core.config.SliderOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.Option;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/** Replaces the external Toggle Nametags mod — hide nametags via keybind/module. */
public class ToggleNametagsModule extends Module {

    public ToggleNametagsModule() {
        super("Nametags", "Customize nametags: hide them, remove the background, add a shadow, highlight your own.");
        addOption(new com.thelads.core.config.DropdownOption("Hide", 0, "None", "Players", "Mobs", "All"));
        addOption(new com.thelads.core.config.BoolOption("Draw Background", true));
        addOption(new com.thelads.core.config.BoolOption("Text Shadow", false));
        addOption(new com.thelads.core.config.BoolOption("Show Own Nametag", false));
    }

    public static boolean shouldShowOwn(Entity entity) {
        Module m = ModuleManager.getInstance().getModule("Nametags");
        if (!(m instanceof ToggleNametagsModule tn) || !tn.isEnabled()) return false;
        Option o = tn.getOption("Show Own Nametag");
        return o instanceof com.thelads.core.config.BoolOption b && b.get() && entity == net.minecraft.client.Minecraft.getInstance().player;
    }

    /** Queried by EntityRendererMixin for every nametag render check. */
    public static boolean shouldHide(Entity entity) {
        Module m = ModuleManager.getInstance().getModule("Nametags");
        if (!(m instanceof ToggleNametagsModule tn) || !tn.isEnabled()) return false;

        Option o = tn.getOption("Hide");
        int idx = (o instanceof DropdownOption c) ? c.getIndex() : 0;
        boolean isPlayer = entity instanceof Player;
        return switch (idx) {
            case 1 -> isPlayer;   // Players only
            case 2 -> !isPlayer;  // Mobs only
            default -> true;      // Everything
        };
    }

    public static boolean isBackgroundDisabled() {
        Module m = ModuleManager.getInstance().getModule("Nametags");
        if (!(m instanceof ToggleNametagsModule tn)) return false;
        Option o = tn.getOption("Draw Background");
        return o instanceof com.thelads.core.config.BoolOption b && !b.get();
    }

    public static boolean isShadowEnabled() {
        Module m = ModuleManager.getInstance().getModule("Nametags");
        if (!(m instanceof ToggleNametagsModule tn)) return false;
        Option o = tn.getOption("Text Shadow");
        return o instanceof com.thelads.core.config.BoolOption b && b.get();
    }
}
