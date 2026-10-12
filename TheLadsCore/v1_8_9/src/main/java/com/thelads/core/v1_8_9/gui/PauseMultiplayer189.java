package com.thelads.core.v1_8_9.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMultiplayer;
import net.minecraft.client.gui.GuiScreen;
import org.apache.logging.log4j.LogManager;

/**
 * The pause menu's Multiplayer: the server list opens over the world (Cancel returns to the pause menu), and only joining a server
 * from it leaves the world or server first (GuiConnectingMixin189). A pause menu shown after that, with no world, is the main menu
 * (MinecraftMixin).
 */
public final class PauseMultiplayer189 {
    private PauseMultiplayer189() {}

    public static void open(GuiScreen pause) {
        Minecraft.getMinecraft().displayGuiScreen(new GuiMultiplayer(pause));
    }

    /**
     * GuiConnecting (every join: server list, direct connect, LAN, Discover Servers), just before it unloads the world: an open world
     * is left as the pause menu's Save and Quit / Disconnect leave it, the quitting packet first (a server is told; the integrated
     * server then saves and stops in loadWorld).
     */
    public static void leaveWorld(Minecraft mc) {
        if (mc.theWorld == null) return;
        LogManager.getLogger("TheLadsCore").info("Lads pause multiplayer: leaving the {} before connecting",
            mc.isIntegratedServerRunning() ? "singleplayer world" : "server");
        mc.theWorld.sendQuittingDisconnectingPacket();
    }
}
