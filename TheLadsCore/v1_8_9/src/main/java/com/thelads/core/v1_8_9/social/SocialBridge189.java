package com.thelads.core.v1_8_9.social;

import com.thelads.core.LadsVersion;
import com.thelads.core.client.social.HostOptions;
import com.thelads.core.client.social.SocialBridge;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.multiplayer.GuiConnecting;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.event.ClickEvent;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.server.management.UserListBansEntry;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.WorldServer;
import net.minecraft.world.WorldSettings;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Minecraft 1.8.9 (Forge) implementation of SocialBridge.
 */
public final class SocialBridge189 implements SocialBridge {
    private static final Logger LOGGER = LogManager.getLogger("TheLadsCore-Social");
    private static volatile boolean loopbackOnly;
    private static volatile Predicate<UUID> loginFilter;
    private static volatile String connectingDisplayName;
    private static final Set<UUID> mutedGuests = Collections.synchronizedSet(new HashSet<>());

    private static Minecraft mc() {
        return Minecraft.getMinecraft();
    }

    public static boolean isLoopbackOnly() {
        return loopbackOnly;
    }

    public static void setLoopbackOnly(boolean value) {
        loopbackOnly = value;
    }

    public static String getConnectingDisplayName() {
        return connectingDisplayName;
    }

    public static void setConnectingDisplayName(String name) {
        connectingDisplayName = name;
    }

    public static boolean isLoginFiltered(UUID uuid) {
        if (uuid == null) return false;
        Minecraft mc = mc();
        // The host is never rejected by the login filter
        if (mc != null && mc.getSession() != null && mc.getSession().getProfile() != null) {
            if (uuid.equals(mc.getSession().getProfile().getId())) return false;
        }
        Predicate<UUID> filter = loginFilter;
        if (filter != null) {
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
        // TODO(S3/S4): route action to SocialState / SocialToasts
        if (actionId.startsWith("accept_invite:")) {
            String inviteId = actionId.substring("accept_invite:".length());
            LOGGER.info("Accepting invite {}", inviteId);
        } else if (actionId.startsWith("decline_invite:")) {
            String inviteId = actionId.substring("decline_invite:".length());
            LOGGER.info("Declining invite {}", inviteId);
        } else if (actionId.startsWith("accept_join:")) {
            String reqId = actionId.substring("accept_join:".length());
            LOGGER.info("Accepting join request {}", reqId);
        } else if (actionId.startsWith("deny_join:")) {
            String reqId = actionId.substring("deny_join:".length());
            LOGGER.info("Denying join request {}", reqId);
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
        if (options != null && options.backupOnOpen) {
            WorldBackupHelper189.backup(server.getFolderName());
        }

        WorldSettings.GameType gameType = WorldSettings.GameType.getByName(options != null && options.guestGameMode != null ? options.guestGameMode : "survival");
        if (gameType == null) gameType = WorldSettings.GameType.SURVIVAL;
        boolean allowCheats = options != null && options.guestOpLevel > 0;

        String portStr = server.shareToLAN(gameType, allowCheats);
        if (portStr == null) {
            setLoopbackOnly(false);
            return -1;
        }

        int port;
        try {
            port = Integer.parseInt(portStr);
        } catch (NumberFormatException e) {
            setLoopbackOnly(false);
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
        Minecraft mc = mc();
        if (mc == null) return;
        IntegratedServer server = mc.getIntegratedServer();
        if (server == null) return;

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
        if (server == null) return Collections.emptyList();

        UUID hostId = null;
        if (mc.getSession() != null && mc.getSession().getProfile() != null) {
            hostId = mc.getSession().getProfile().getId();
        }

        List<GuestInfo> result = new ArrayList<>();
        for (EntityPlayerMP player : server.getConfigurationManager().getPlayerList()) {
            if (hostId != null && player.getUniqueID().equals(hostId)) continue;
            if (player.getName().equalsIgnoreCase(server.getServerOwner())) continue;

            boolean op = server.getConfigurationManager().canSendCommands(player.getGameProfile());
            String gm = player.theItemInWorldManager.getGameType().getName();
            result.add(new GuestInfo(player.getUniqueID(), player.getName(), player.ping, op, gm));
        }
        return result;
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
                            guest.setPositionAndUpdate(host.posX, host.posY, host.posZ);
                        }
                    }
                    break;
                case MUTE:
                    if (mutedGuests.contains(id)) mutedGuests.remove(id);
                    else mutedGuests.add(id);
                    break;
            }
        });
    }

    @Override
    public void setLoginFilter(Predicate<UUID> allowed) {
        loginFilter = allowed;
    }

    @Override
    public void connectToLocal(int port, String displayName) {
        Minecraft mc = mc();
        if (mc == null) return;
        setConnectingDisplayName(displayName != null ? displayName : "P2P Host");
        ServerData data = new ServerData(displayName != null ? displayName : "P2P Host", "127.0.0.1:" + port, false);
        mc.displayGuiScreen(new GuiConnecting(mc.currentScreen, mc, data));
    }

    @Override
    public void connectToServer(String address) {
        Minecraft mc = mc();
        if (mc == null || address == null) return;
        setConnectingDisplayName(null);
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
