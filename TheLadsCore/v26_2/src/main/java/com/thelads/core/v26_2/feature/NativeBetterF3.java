package com.thelads.core.v26_2.feature;

import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.ModuleSupport;
import com.thelads.core.modules.BetterF3Module;
import com.thelads.core.v26_2.adapter.GuiGraphicsExtractorLadsAdapter;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Better F3 on 26.x: the game keeps choosing and building the debug entries (its Debug Options screen still picks them);
 * BetterF3Mixin hands each finished column to BetterF3Module instead of drawing it as plain grey text. A BetterF3 jar left in
 * the mods folder keeps the debug screen to itself.
 */
public final class NativeBetterF3 {
    private static boolean active;
    private NativeBetterF3() {}

    public static void register() {
        if (active || FabricLoader.getInstance().isModLoaded("betterf3")) return;
        active = true;
        ModuleSupport.registerBuiltIn("BetterF3");
    }

    /** One column of the debug screen: drawn by Better F3 (true), or left to the game (false). */
    public static boolean draw(GuiGraphicsExtractor graphics, Font font, List<String> column, boolean left, int width) {
        if (!active || !(ModuleManager.getInstance().getModule("BetterF3") instanceof BetterF3Module f3) || !f3.isEnabled()) return false;
        f3.draw(new GuiGraphicsExtractorLadsAdapter(graphics, font), column, left, width, Minecraft.getInstance().debugEntries.isOverlayVisible());
        return true;
    }
}
