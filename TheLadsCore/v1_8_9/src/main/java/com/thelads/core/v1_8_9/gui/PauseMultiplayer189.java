package com.thelads.core.v1_8_9.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiMultiplayer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiYesNo;

/** The pause menu's Multiplayer: leave through the pause menu's own save/disconnect path once the player confirms (PauseMultiplayer). */
public final class PauseMultiplayer189 {
    private PauseMultiplayer189() {}

    public static void open(GuiScreen pause) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null) {
            mc.displayGuiScreen(new GuiMultiplayer(new GuiMainMenu()));
            return;
        }
        boolean local = mc.isIntegratedServerRunning();
        mc.displayGuiScreen(new GuiYesNo((confirmed, id) -> {
            if (!confirmed) {
                mc.displayGuiScreen(pause);
                return;
            }
            // As the pause menu's Save and Quit / Disconnect button.
            mc.theWorld.sendQuittingDisconnectingPacket();
            mc.loadWorld(null);
            mc.displayGuiScreen(new GuiMultiplayer(new GuiMainMenu()));
        }, local ? "Save and open Multiplayer?" : "Leave this server?",
            local ? "Your world will be saved before Multiplayer opens." : "Disconnect from this server and open Multiplayer?",
            local ? "Save & continue" : "Disconnect & continue", "Stay in game", 0));
    }
}
