package com.thelads.core.v1_8_9.gui;

import com.thelads.core.client.LadsNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiMultiplayer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiYesNo;
import net.minecraft.client.multiplayer.GuiConnecting;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;
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
     * The pause menu's Network button (LadsNetwork.pauseLabel names it): on the network the hub command, else asks, leaves this world
     * or server and joins the network.
     */
    public static void network(GuiScreen pause) {
        Minecraft mc = Minecraft.getMinecraft();
        String hub = LadsNetwork.hubCommand(command -> false); // no command tree on 1.8: only what the hello names
        if (hub != null) {
            com.thelads.core.client.bridge.LadsGameBridge.get().sendChatLine(hub);
            mc.displayGuiScreen(null);
            return;
        }
        boolean local = mc.isIntegratedServerRunning();
        mc.displayGuiScreen(new GuiYesNo((yes, id) -> {
            if (yes) joinNetwork(new GuiMultiplayer(new GuiMainMenu()));
            else mc.displayGuiScreen(pause);
        }, local ? "Save and join " + LadsNetwork.NAME + "?" : "Leave this server?",
            local ? "Your world will be saved first." : "Disconnect from this server and join " + LadsNetwork.NAME + "?", 0));
    }

    public static void joinNetwork(GuiScreen parent) {
        connectToServer(parent, LadsNetwork.NAME, LadsNetwork.ADDRESS);
    }

    public static void connectToServer(GuiScreen parent, String name, String address) {
        Minecraft mc = Minecraft.getMinecraft();
        ServerData data = ensureServerEntry(mc, name, address);
        mc.displayGuiScreen(new GuiConnecting(parent, mc, data));
    }

    /** Reuses an existing saved server entry (preserving Disabled if chosen) or registers a new one with server packs Enabled. */
    public static ServerData ensureServerEntry(Minecraft mc, String name, String address) {
        String targetIp = address == null ? LadsNetwork.ADDRESS : address.trim();
        String display = (name != null && !name.isEmpty()) ? name : targetIp;
        try {
            ServerList list = new ServerList(mc);
            list.loadServerList();
            for (int i = 0; i < list.countServers(); i++) {
                ServerData existing = list.getServerData(i);
                if (existing != null && existing.serverIP != null && existing.serverIP.trim().equalsIgnoreCase(targetIp)) {
                    if (existing.getResourceMode() == ServerData.ServerResourceMode.PROMPT) {
                        existing.setResourceMode(ServerData.ServerResourceMode.ENABLED);
                        list.saveServerList();
                    }
                    return existing;
                }
            }
            ServerData created = new ServerData(display, targetIp, false);
            created.setResourceMode(ServerData.ServerResourceMode.ENABLED);
            list.addServerData(created);
            list.saveServerList();
            return created;
        } catch (Exception ignored) {
            ServerData fallback = new ServerData(display, targetIp, false);
            fallback.setResourceMode(ServerData.ServerResourceMode.ENABLED);
            return fallback;
        }
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
