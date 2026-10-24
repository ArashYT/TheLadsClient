package com.thelads.core.v1_8_9.social;

import com.thelads.core.LadsVersion;
import com.thelads.core.client.social.HostOptions;
import com.thelads.core.client.social.SocialBridge;
import com.thelads.core.v1_8_9.social.mixin.IntegratedServerAccessor;
import com.thelads.core.v1_8_9.social.mixin.NetworkSystemAccessor;
import io.netty.channel.ChannelFuture;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.multiplayer.GuiConnecting;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ThreadLanServerPing;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.event.ClickEvent;
import net.minecraft.network.NetworkSystem;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.server.management.UserListBansEntry;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.WorldServer;
import net.minecraft.world.WorldSettings;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Minecraft 1.8.9 (Forge) implementation of SocialBridge.
 */
public final class SocialBridge189 implements SocialBridge {
    private static final Logger LOGGER = LogManager.getLogger("TheLadsCore-Social");
    private static volatile boolean loopbackOnly;
    private static volatile Predicate<UUID> loginFilter;
    private static volatile int connectingPort = -1;
    private static volatile String connectingDisplayName;
    private static final Set<UUID> mutedGuests = Collections.synchronizedSet(new HashSet<>());

    private final List<GuestInfo> guestSnapshot = new CopyOnWriteArrayList<>();
    private volatile long lastGuestRefreshTime = 0;

    private static Minecraft mc() {
        return Minecraft.getMinecraft();
    }

    public static boolean isLoopbackOnly() {
        return loopbackOnly;
    }

    public static void setLoopbackOnly(boolean value) {
        loopbackOnly = value;
    }

    public static synchronized String getConnectingDisplayName() {
        return connectingDisplayName;
    }

    public static synchronized void setConnectingTarget(int port, String name) {
        connectingPort = port;
        connectingDisplayName = name;
    }

    public static synchronized void clearConnectingTarget() {
        connectingPort = -1;
        connectingDisplayName = null;
    }

    public static synchronized boolean isTargetLoopback(String ip, int port) {
        if (connectingPort <= 0 || connectingDisplayName == null) {
            return false;
        }
        if (port != connectingPort) {
            return false;
        }
        return "127.0.0.1".equals(ip) || "localhost".equalsIgnoreCase(ip);
    }

    public static boolean isLoginFiltered(UUID uuid) {
        Predicate<UUID> filter = loginFilter;
        if (filter != null) {
            if (uuid == null) {
                return true;
            }
            Minecraft mc = mc();
            // The host is never rejected by the login filter
            if (mc != null && mc.getSession() != null && mc.getSession().getProfile() != null) {
                if (uuid.equals(mc.getSession().getProfile().getId())) return false;
            }
            return !filter.test(uuid);
        }
        return false;
    }

    public static int onlineFriendsCount() {
        // TODO(S3): return (int) SocialState.get().friends().stream().filter(f -> f.presence() != null && !"offline".equals(f.presence().state())).count();
        return 0;
    }

    public static void handleChatAction(String actionId) {
        if (actionId == null || actionId.isEmpty()) return;
        LOGGER.info("Handling client-side social chat action: {}", actionId);
        // TODO(S3): SocialState.get().handleAction(actionId);
        if (actionId.startsWith("accept_invite:") || actionId.startsWith("invite_accept:")) {
            LOGGER.info("Accepting invite action: {}", actionId);
        } else if (actionId.startsWith("decline_invite:") || actionId.startsWith("invite_decline:")) {
            LOGGER.info("Declining invite action: {}", actionId);
        } else if (actionId.startsWith("accept_join:") || actionId.startsWith("join_accept:")) {
            LOGGER.info("Accepting join request: {}", actionId);
        } else if (actionId.startsWith("deny_join:") || actionId.startsWith("join_deny:")) {
            LOGGER.info("Denying join request: {}", actionId);
        }
    }

    @Override
    public boolean canHost() {
        Minecraft mc = mc();
        return mc != null && mc.isIntegratedServerRunning() && mc.getIntegratedServer() != null && !mc.getIntegratedServer().getPublic();
    }

    @Override
    public int openToFriends(HostOptions options) {
        Minecraft mc = mc();
        if (mc == null) return -1;
        IntegratedServer server = mc.getIntegratedServer();
        if (server == null) return -1;

        setLoopbackOnly(true);
        String portStr = null;
        try {
            if (options != null && options.backupOnOpen) {
                WorldBackupHelper189.backup(server.getFolderName());
            }

            WorldSettings.GameType gameType = WorldSettings.GameType.getByName(options != null && options.guestGameMode != null ? options.guestGameMode : "survival");
            if (gameType == null) gameType = WorldSettings.GameType.SURVIVAL;
            boolean allowCheats = options != null && options.guestOpLevel > 0;

            portStr = server.shareToLAN(gameType, allowCheats);
        } finally {
            setLoopbackOnly(false);
        }

        if (portStr == null) {
            return -1;
        }

        int port;
        try {
            port = Integer.parseInt(portStr);
        } catch (NumberFormatException e) {
            return -1;
        }

        applyHostOptions(options);
        LOGGER.info("Opened integrated server to friends on 127.0.0.1:{}", port);
        return port;
    }

    @Override
    public void openToLan(HostOptions options) {
        Minecraft mc = mc();
        if (mc == null) return;
        IntegratedServer server = mc.getIntegratedServer();
        if (server == null) return;

        setLoopbackOnly(false);
        if (options != null && options.backupOnOpen) {
            WorldBackupHelper189.backup(server.getFolderName());
        }

        WorldSettings.GameType gameType = WorldSettings.GameType.getByName(options != null && options.guestGameMode != null ? options.guestGameMode : "survival");
        if (gameType == null) gameType = WorldSettings.GameType.SURVIVAL;
        boolean allowCheats = options != null && options.guestOpLevel > 0;

        server.shareToLAN(gameType, allowCheats);
        applyHostOptions(options);
        LOGGER.info("Opened integrated server to LAN");
    }

    @Override
    public void closeHosting(String reason) {
        setLoopbackOnly(false);
        loginFilter = null;
        guestSnapshot.clear();

        Minecraft mc = mc();
        if (mc == null) return;
        IntegratedServer server = mc.getIntegratedServer();
        if (server == null) return;

        if (server instanceof IntegratedServer) {
            try {
                ((IntegratedServerAccessor) (Object) server).setIsPublic(false);
            } catch (Throwable ignored) {}
        }

        server.addScheduledTask(() -> {
            UUID hostId = null;
            if (mc.getSession() != null && mc.getSession().getProfile() != null) {
                hostId = mc.getSession().getProfile().getId();
            }
            for (EntityPlayerMP player : new ArrayList<>(server.getConfigurationManager().getPlayerList())) {
                if (hostId == null || !player.getUniqueID().equals(hostId)) {
                    player.playerNetServerHandler.kickPlayerFromServer(reason != null ? reason : "Host closed the world");
                }
            }

            // Close LAN listening endpoints
            NetworkSystem netSystem = server.getNetworkSystem();
            if (netSystem != null) {
                try {
                    List<ChannelFuture> endpoints = ((NetworkSystemAccessor) (Object) netSystem).getEndpoints();
                    if (endpoints != null) {
                        synchronized (endpoints) {
                            Iterator<ChannelFuture> it = endpoints.iterator();
                            while (it.hasNext()) {
                                ChannelFuture future = it.next();
                                if (future.channel().localAddress() instanceof java.net.InetSocketAddress
                                        || future.channel() instanceof io.netty.channel.socket.ServerSocketChannel) {
                                    try {
                                        future.channel().close().sync();
                                    } catch (Throwable ignored) {}
                                    it.remove();
                                }
                            }
                        }
                    }
                } catch (Throwable t) {
                    LOGGER.warn("Failed to close LAN endpoints: {}", t.getMessage());
                }
            }

            // Stop LAN server discovery ping thread if running and reset isPublic
            if (server instanceof IntegratedServer) {
                try {
                    IntegratedServerAccessor isAcc = (IntegratedServerAccessor) (Object) server;
                    ThreadLanServerPing ping = isAcc.getLanServerPing();
                    if (ping != null) {
                        try {
                            ping.interrupt();
                        } catch (Throwable ignored) {}
                        isAcc.setLanServerPing(null);
                    }
                    isAcc.setIsPublic(false);
                } catch (Throwable t) {
                    LOGGER.warn("Failed to stop LAN ping: {}", t.getMessage());
                }
            }
        });
        LOGGER.info("Closed hosting: {}", reason);
    }

    @Override
    public void applyHostOptions(HostOptions options) {
        if (options == null) return;
        Minecraft mc = mc();
        if (mc == null) return;
        IntegratedServer server = mc.getIntegratedServer();
        if (server == null) return;

        server.addScheduledTask(() -> {
            if (options.guestGameMode != null) {
                WorldSettings.GameType gt = WorldSettings.GameType.getByName(options.guestGameMode);
                if (gt != null) server.setGameType(gt);
            }
            server.getConfigurationManager().setCommandsAllowedForAll(options.guestOpLevel > 0);
            server.setAllowPvp(options.pvp);
            if (options.difficulty != null) {
                try {
                    EnumDifficulty diff = EnumDifficulty.valueOf(options.difficulty.toUpperCase(Locale.ROOT));
                    server.setDifficultyForAllWorlds(diff);
                } catch (IllegalArgumentException ignored) {}
            }
            if (options.gamerules != null && server.worldServers != null) {
                for (WorldServer ws : server.worldServers) {
                    if (ws != null && ws.getGameRules() != null) {
                        for (Map.Entry<String, String> entry : options.gamerules.entrySet()) {
                            ws.getGameRules().setOrCreateGameRule(entry.getKey(), entry.getValue());
                        }
                    }
                }
            }
            if (options.viewDistanceCap > 0) {
                server.getConfigurationManager().setViewDistance(options.viewDistanceCap);
            }
        });
    }

    @Override
    public List<GuestInfo> guests() {
        Minecraft mc = mc();
        if (mc == null) return Collections.emptyList();
        IntegratedServer server = mc.getIntegratedServer();
        if (server == null || !server.isServerRunning()) {
            guestSnapshot.clear();
            return Collections.emptyList();
        }

        long now = System.currentTimeMillis();
        if (now - lastGuestRefreshTime >= 1000L) {
            lastGuestRefreshTime = now;
            server.addScheduledTask(() -> refreshGuestSnapshot(server));
        }

        return new ArrayList<>(guestSnapshot);
    }

    private void refreshGuestSnapshot(IntegratedServer server) {
        if (server == null || server.getConfigurationManager() == null) return;
        Minecraft mc = mc();
        UUID hostId = null;
        if (mc != null && mc.getSession() != null && mc.getSession().getProfile() != null) {
            hostId = mc.getSession().getProfile().getId();
        }
        List<GuestInfo> updated = new ArrayList<>();
        for (EntityPlayerMP player : server.getConfigurationManager().getPlayerList()) {
            if (hostId != null && player.getUniqueID().equals(hostId)) continue;
            if (player.getName().equalsIgnoreCase(server.getServerOwner())) continue;

            boolean op = server.getConfigurationManager().canSendCommands(player.getGameProfile());
            String gm = player.theItemInWorldManager.getGameType().getName();
            updated.add(new GuestInfo(player.getUniqueID(), player.getName(), player.ping, op, gm));
        }
        guestSnapshot.clear();
        guestSnapshot.addAll(updated);
    }

    @Override
    public void guestAction(UUID id, GuestAction action) {
        if (id == null || action == null) return;
        Minecraft mc = mc();
        if (mc == null) return;
        IntegratedServer server = mc.getIntegratedServer();
        if (server == null) return;

        server.addScheduledTask(() -> {
            EntityPlayerMP guest = server.getConfigurationManager().getPlayerByUUID(id);
            switch (action) {
                case KICK:
                    if (guest != null) guest.playerNetServerHandler.kickPlayerFromServer("Kicked by host");
                    break;
                case BAN:
                    if (guest != null) {
                        server.getConfigurationManager().getBannedPlayers().addEntry(
                            new UserListBansEntry(guest.getGameProfile(), (Date) null, "Host", (Date) null, "Banned by host"));
                        guest.playerNetServerHandler.kickPlayerFromServer("Banned by host");
                    }
                    break;
                case OP:
                    if (guest != null) server.getConfigurationManager().addOp(guest.getGameProfile());
                    break;
                case DEOP:
                    if (guest != null) server.getConfigurationManager().removeOp(guest.getGameProfile());
                    break;
                case SURVIVAL:
                    if (guest != null) guest.setGameType(WorldSettings.GameType.SURVIVAL);
                    break;
                case CREATIVE:
                    if (guest != null) guest.setGameType(WorldSettings.GameType.CREATIVE);
                    break;
                case ADVENTURE:
                    if (guest != null) guest.setGameType(WorldSettings.GameType.ADVENTURE);
                    break;
                case SPECTATOR:
                    if (guest != null) guest.setGameType(WorldSettings.GameType.SPECTATOR);
                    break;
                case TP_TO_ME:
                    if (guest != null) {
                        EntityPlayerMP host = server.getConfigurationManager().getPlayerByUsername(server.getServerOwner());
                        if (host != null) {
                            if (guest.dimension != host.dimension) {
                                server.getConfigurationManager().transferPlayerToDimension(guest, host.dimension);
                            }
                            guest.setPositionAndUpdate(host.posX, host.posY, host.posZ);
                        }
                    }
                    break;
                case MUTE:
                    if (guest != null) {
                        EntityPlayerMP host = server.getConfigurationManager().getPlayerByUsername(server.getServerOwner());
                        if (mutedGuests.contains(id)) {
                            mutedGuests.remove(id);
                            if (host != null) {
                                host.addChatMessage(new ChatComponentText(EnumChatFormatting.YELLOW + "Unmuted " + guest.getName()));
                            }
                        } else {
                            mutedGuests.add(id);
                            if (host != null) {
                                host.addChatMessage(new ChatComponentText(EnumChatFormatting.YELLOW + "Muted " + guest.getName()));
                            }
                        }
                    }
                    break;
            }
            refreshGuestSnapshot(server);
        });
    }

    @SubscribeEvent
    public void onServerChat(ServerChatEvent event) {
        if (event.player != null && mutedGuests.contains(event.player.getUniqueID())) {
            event.setCanceled(true);
            event.player.addChatMessage(new ChatComponentText(EnumChatFormatting.RED + "You are muted in this world."));
        }
    }

    @SubscribeEvent
    public void onWorldUnload(WorldEvent.Unload event) {
        setLoopbackOnly(false);
        guestSnapshot.clear();
    }

    @Override
    public void setLoginFilter(Predicate<UUID> allowed) {
        loginFilter = allowed;
    }

    @Override
    public void connectToLocal(int port, String displayName) {
        Minecraft mc = mc();
        if (mc == null) return;
        setConnectingTarget(port, displayName != null ? displayName : "P2P Host");
        ServerData data = new ServerData(displayName != null ? displayName : "P2P Host", "127.0.0.1:" + port, false);
        mc.displayGuiScreen(new GuiConnecting(mc.currentScreen, mc, data));
    }

    @Override
    public void connectToServer(String address) {
        Minecraft mc = mc();
        if (mc == null || address == null) return;
        clearConnectingTarget();
        ServerData data = new ServerData(address, address, false);
        mc.displayGuiScreen(new GuiConnecting(mc.currentScreen, mc, data));
    }

    @Override
    public boolean inWorldOrServer() {
        Minecraft mc = mc();
        return mc != null && mc.theWorld != null;
    }

    @Override
    public void leaveWorld(Runnable then) {
        setLoopbackOnly(false);
        guestSnapshot.clear();
        clearConnectingTarget();
        Minecraft mc = mc();
        if (mc == null) {
            if (then != null) then.run();
            return;
        }
        if (mc.theWorld != null) {
            mc.theWorld.sendQuittingDisconnectingPacket();
        }
        mc.loadWorld(null);
        mc.displayGuiScreen(new GuiMainMenu());
        if (then != null) {
            mc.addScheduledTask(then);
        }
    }

    @Override
    public GameInfo gameInfo() {
        return new GameInfo("1.8.9", "forge", "plain", "", "", "", LadsVersion.clientName());
    }

    @Override
    public void openFriendsScreen() {
        Minecraft mc = mc();
        if (mc == null) return;
        mc.displayGuiScreen(new FriendsScreen189(mc.currentScreen));
    }

    @Override
    public void openHostScreen() {
        Minecraft mc = mc();
        if (mc == null) return;
        mc.displayGuiScreen(new HostScreen189(mc.currentScreen));
    }

    @Override
    public void toast(String title, String body, String acceptAction, String denyAction) {
        SocialToasts189.show(title, body, acceptAction, denyAction);
    }

    @Override
    public void chatAction(String text, String actionId) {
        Minecraft mc = mc();
        if (mc == null || mc.ingameGUI == null) return;
        ChatComponentText msg = new ChatComponentText(text != null ? text : "");
        ChatStyle style = new ChatStyle();
        style.setChatClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/lads:social " + actionId));
        style.setColor(EnumChatFormatting.AQUA);
        style.setUnderlined(true);
        msg.setChatStyle(style);
        mc.ingameGUI.getChatGUI().printChatMessage(msg);
    }

    @Override
    public String worldId() {
        Minecraft mc = mc();
        if (mc != null && mc.getIntegratedServer() != null) {
            return mc.getIntegratedServer().getFolderName();
        }
        return null;
    }

    @Override
    public String worldName() {
        Minecraft mc = mc();
        if (mc != null && mc.getIntegratedServer() != null) {
            return mc.getIntegratedServer().getWorldName();
        }
        return null;
    }
}
