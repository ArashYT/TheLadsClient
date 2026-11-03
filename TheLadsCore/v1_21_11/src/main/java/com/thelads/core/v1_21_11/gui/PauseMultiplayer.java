package com.thelads.core.v1_21_11.gui;

import com.thelads.core.client.LadsNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.AlertScreen;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.multiplayer.SafetyScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;

/** Leave through Minecraft's saving/disconnect path only after the player confirms (the 26.x pause Multiplayer flow). */
public final class PauseMultiplayer {
    private PauseMultiplayer() {}
    public static void open(Screen pause) {
        Minecraft mc=Minecraft.getInstance();
        if (!mc.allowsMultiplayer()) {
            mc.setScreen(new AlertScreen(() -> mc.setScreen(pause),
                Component.translatable("menu.multiplayer"), Component.literal("Multiplayer is unavailable for this account.")));
            return;
        }
        // As the title screen's Multiplayer button: the safety screen until the player skips it.
        Runnable showServers=()->{
            Screen title=new TitleScreen();
            mc.setScreen(mc.options.skipMultiplayerWarning ? new JoinMultiplayerScreen(title) : new SafetyScreen(title));
        };
        leaveAndOpen(pause,"Multiplayer",showServers);
    }
    /**
     * The pause menu's Network button (LadsNetwork.pauseLabel names it): on the network the hub command, else asks, leaves this world
     * or server and joins the network.
     */
    public static void network(Screen pause) {
        Minecraft mc=Minecraft.getInstance();
        String hub=LadsNetwork.hubCommand(PauseMultiplayer::serverHas);
        if(hub!=null){
            com.thelads.core.client.bridge.LadsGameBridge.get().sendChatLine(hub);
            mc.setScreen(null);
            return;
        }
        leaveAndOpen(pause,LadsNetwork.NAME,()->joinNetwork(new JoinMultiplayerScreen(new TitleScreen())));
    }
    public static void joinNetwork(Screen parent) {
        connectToServer(parent, LadsNetwork.NAME, LadsNetwork.ADDRESS);
    }
    public static void connectToServer(Screen parent, String name, String address) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            mc.disconnectFromWorld(ClientLevel.DEFAULT_QUIT_MESSAGE);
        }
        ServerData data = ensureServerEntry(mc, name, address);
        ConnectScreen.startConnecting(parent, mc, ServerAddress.parseString(data.ip), data, false, null);
    }
    /** Reuses an existing saved server entry (preserving Disabled if chosen) or registers a new one with server packs Enabled. */
    public static ServerData ensureServerEntry(Minecraft mc, String name, String address) {
        String targetIp = address == null ? LadsNetwork.ADDRESS : address.trim();
        String display = (name != null && !name.isEmpty()) ? name : targetIp;
        try {
            ServerList list = new ServerList(mc);
            list.load();
            for (int i = 0; i < list.size(); i++) {
                ServerData existing = list.get(i);
                if (existing != null && existing.ip != null && existing.ip.trim().equalsIgnoreCase(targetIp)) {
                    if (existing.getResourcePackStatus() == ServerData.ServerPackStatus.PROMPT) {
                        existing.setResourcePackStatus(ServerData.ServerPackStatus.ENABLED);
                        list.save();
                    }
                    return existing;
                }
            }
            ServerData created = new ServerData(display, targetIp, ServerData.Type.OTHER);
            created.setResourcePackStatus(ServerData.ServerPackStatus.ENABLED);
            list.add(created, false);
            list.save();
            return created;
        } catch (Exception ignored) {
            ServerData fallback = new ServerData(display, targetIp, ServerData.Type.OTHER);
            fallback.setResourcePackStatus(ServerData.ServerPackStatus.ENABLED);
            return fallback;
        }
    }
    public static boolean serverHas(String command) {
        var connection=Minecraft.getInstance().getConnection();
        return connection!=null&&connection.getCommands().getRoot().getChild(command)!=null;
    }
    public static void leaveAndOpen(Screen pause,String destination,Runnable next) {
        Minecraft mc=Minecraft.getInstance();
        if(mc.level==null){next.run();return;}
        boolean local=mc.isLocalServer();
        mc.setScreen(new ConfirmScreen(confirmed->{
            if(!confirmed){mc.setScreen(pause);return;}
            // The vanilla Disconnect / Save and Quit action: unsent-report prompt, then saving or disconnecting.
            mc.getReportingContext().draftReportHandled(mc,pause,()->{
                mc.disconnectFromWorld(ClientLevel.DEFAULT_QUIT_MESSAGE);
                next.run();
            },true);
        },Component.literal(local?"Save and open "+destination+"?":"Leave this server?"),
          Component.literal(local?"Your world will be saved before "+destination+" opens.":"Disconnect from this server and open "+destination+"?"),
          Component.literal(local?"Save & continue":"Disconnect & continue"),Component.literal("Stay in game")));
    }
}
