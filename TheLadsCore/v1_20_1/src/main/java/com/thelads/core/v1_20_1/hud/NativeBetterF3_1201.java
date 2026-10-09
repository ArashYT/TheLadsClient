package com.thelads.core.v1_20_1.hud;

import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.ModuleSupport;
import com.thelads.core.modules.BetterF3Module;
import com.thelads.core.v1_20_1.adapter.GuiGraphicsLadsAdapter1201;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Better F3 on 1.20.1: the game keeps building the debug text; BetterF3Mixin hands each finished column to BetterF3Module instead of
 * drawing it as plain grey text. A BetterF3 jar left in the mods folder keeps the debug screen to itself.
 */
public final class NativeBetterF3_1201 {
    private static boolean active;

    private NativeBetterF3_1201() {}

    public static void register() {
        if (active) return;
        if (Mods1201.loaded("betterf3")) {
            ModuleSupport.registerExternal("BetterF3", "BetterF3", "betterf3", true);
            return;
        }
        active = true;
        ModuleSupport.registerBuiltIn("BetterF3");
    }

    /** One column of the debug screen: drawn by Better F3 (true), or left to the game (false). */
    public static boolean draw(GuiGraphics graphics, Font font, List<String> column, boolean left) {
        if (!active || !(ModuleManager.getInstance().getModule("BetterF3") instanceof BetterF3Module f3) || !f3.isEnabled()) return false;
        f3.draw(new GuiGraphicsLadsAdapter1201(graphics, font), column, left, graphics.guiWidth(), true);
        return true;
    }
}
