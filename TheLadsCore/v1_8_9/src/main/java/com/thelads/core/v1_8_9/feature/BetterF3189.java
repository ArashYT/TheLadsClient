package com.thelads.core.v1_8_9.feature;

import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.ModuleSupport;
import com.thelads.core.modules.BetterF3Module;
import com.thelads.core.v1_8_9.adapter.GuiLadsAdapter;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Better F3 on 1.8.9: Forge collects the debug screen's text (vanilla's, OptiFine's and other mods' lines) in its text event and
 * draws it after; last in that event, BetterF3Module draws both columns instead and leaves Forge nothing to draw.
 */
public final class BetterF3189 {
    private BetterF3189() {}

    public static void register() {
        if (Loader.isModLoaded("betterf3")) {
            ModuleSupport.registerExternal("BetterF3", "BetterF3", "betterf3", true);
            return;
        }
        ModuleSupport.registerBuiltIn("BetterF3");
        MinecraftForge.EVENT_BUS.register(new BetterF3189());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void text(RenderGameOverlayEvent.Text event) {
        Minecraft mc = Minecraft.getMinecraft();
        Module module = ModuleManager.getInstance().getModule("BetterF3");
        if (!mc.gameSettings.showDebugInfo || !(module instanceof BetterF3Module) || !module.isEnabled()) return;
        int width = event.resolution.getScaledWidth();
        GuiLadsAdapter graphics = new GuiLadsAdapter(mc.fontRendererObj, width, event.resolution.getScaledHeight());
        ((BetterF3Module) module).draw(graphics, event.left, true, width, true);
        ((BetterF3Module) module).draw(graphics, event.right, false, width, true);
        event.left.clear();
        event.right.clear();
    }
}
