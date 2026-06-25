package com.thelads.core.modules;

import com.thelads.core.config.BoolOption;
import com.thelads.core.config.DoubleOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.Option;
import net.minecraft.client.Minecraft;

public class ExordiumModule extends Module {
    public ExordiumModule() {
        super("Exordium", "Limits GUI framerate to save resources. Built-in native clone.");
        addOption(new BoolOption("Limit GUI", true));
        addOption(new DoubleOption("GUI FPS Limit", 60.0, 5.0, 144.0));
        addOption(new BoolOption("Limit Global", false));
        addOption(new DoubleOption("Global FPS", 60.0, 5.0, 240.0));
        addOption(new BoolOption("Limit Inventory", true));
        addOption(new DoubleOption("Inventory FPS", 60.0, 5.0, 144.0));
        addOption(new BoolOption("Limit Chat", true));
        addOption(new DoubleOption("Chat FPS", 60.0, 5.0, 144.0));
        addOption(new BoolOption("Limit Pause Menu", true));
        addOption(new DoubleOption("Pause Menu FPS", 60.0, 5.0, 144.0));
        addOption(new BoolOption("Limit Active Gameplay", false));
        addOption(new DoubleOption("Active Gameplay FPS", 60.0, 5.0, 144.0));
    }

    public static int getFpsLimit() {
        Module m = ModuleManager.getInstance().getModule("Exordium");
        if (m == null || !m.isEnabled()) return -1;

        Minecraft mc = Minecraft.getInstance();
        net.minecraft.client.gui.screens.Screen screen = mc.gui.screen();

        String optName = "Global FPS";
        String toggleName = "Limit Global";

        if (screen != null) {
            if (screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen) {
                optName = "Inventory FPS";
                toggleName = "Limit Inventory";
            } else if (screen instanceof net.minecraft.client.gui.screens.ChatScreen) {
                optName = "Chat FPS";
                toggleName = "Limit Chat";
            } else if (screen instanceof net.minecraft.client.gui.screens.PauseScreen) {
                optName = "Pause Menu FPS";
                toggleName = "Limit Pause Menu";
            } else {
                optName = "GUI FPS Limit";
                toggleName = "Limit GUI";
            }
        } else {
            optName = "Active Gameplay FPS";
            toggleName = "Limit Active Gameplay";
        }

        Option t = m.getOption(toggleName);
        if (t instanceof BoolOption && !((BoolOption) t).get()) {
            return -1;
        }

        Option o = m.getOption(optName);
        if (o instanceof DoubleOption) {
            return (int) ((DoubleOption) o).get();
        }
        return 60;
    }
}
