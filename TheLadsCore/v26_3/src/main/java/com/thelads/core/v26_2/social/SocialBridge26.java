package com.thelads.core.v26_2.social;

import com.thelads.core.client.social.HostOptions;
import com.thelads.core.client.social.SocialBridge;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.server.players.UserBanList;
import net.minecraft.server.players.UserBanListEntry;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.ServerSocket;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Predicate;
import java.lang.reflect.Method;

/**
 * Fabric 26.3 implementation of {@link SocialBridge}.
 */
public class SocialBridge26 implements SocialBridge {
    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore-Social");
    private static volatile boolean loopbackScope = false;
    private static volatile Predicate<UUID> loginFilter = null;
    private static final Set<UUID> mutedGuests = Collections.synchronizedSet(new HashSet<>());
    private final java.util.concurrent.CopyOnWriteArrayList<GuestInfo> guestSnapshot = new java.util.concurrent.CopyOnWriteArrayList<>();

    public static boolean isMuted(UUID id) {
        return id != null && mutedGuests.contains(id);
    }

    public static boolean isLoopbackScope() {
        return loopbackScope;
    }

    public static void setLoopbackScope(boolean enabled) {
        loopbackScope = enabled;
    }

    public static Predicate<UUID> getLoginFilter() {
        return loginFilter;
    }

    @Override
    public boolean canHost() {
        IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
        return server != null && server.isRunning() && !server.isPublished();
    }

    @Override
    public int openToFriends(HostOptions options) {
        IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
        if (server == null || !server.isRunning()) return -1;

        try {
            if (options.backupOnOpen) {
                BackupHelper26.backupWorld(server);
            }

            Path worldDir = server.getWorldPath(LevelResource.ROOT);
            options.save(worldDir);

            loopbackScope = true;
            int port = findFreePort();
            boolean success = false;
            try {
                success = server.publishServer(MinecraftServer.MultiplayerScope.LAN, options.guestOpLevel > 0, port);
            } finally {
                loopbackScope = false;
            }
            if (!success) {
                return -1;
            }

            applyHostOptions(options);
            return server.getPort();
        } catch (Throwable t) {
            LOGGER.error("Failed to open world to friends", t);
            loopbackScope = false;
            return -1;
        }
    }

    @Override
    public void openToLan(HostOptions options) {
        IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
        if (server == null || !server.isRunning()) return;

        loopbackScope = false;
        server.publishServer(MinecraftServer.MultiplayerScope.LAN, options.guestOpLevel > 0, 0);
        applyHostOptions(options);
    }

    @Override
    public void closeHosting(String reason) {
        IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
        loopbackScope = false;
        loginFilter = null;
        mutedGuests.clear();
        if (server != null) {
            server.execute(() -> {
                if (server.getPlayerList() != null) {
                    UUID hostId = server.getSingleplayerProfile() != null ? server.getSingleplayerProfile().id() : null;
                    for (ServerPlayer player : new ArrayList<>(server.getPlayerList().getPlayers())) {
                        if (hostId != null && hostId.equals(player.getUUID())) continue;
                        if (player.connection != null) {
                            player.connection.disconnect(Component.literal(reason != null && !reason.isEmpty() ? reason : "Host closed the session"));
                        }
                    }
                }
                if (server.isPublished()) {
                    server.unpublishServer();
                    LOGGER.info("Closed hosting: {}", reason);
                }
            });
        }
    }

    @Override
    public void applyHostOptions(HostOptions options) {
        IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
        if (server == null) return;

        server.execute(() -> {
            if (options.guestGameMode != null) {
                GameType mode = GameType.byName(options.guestGameMode);
                if (mode != null) {
                    server.setDefaultGameType(mode);
                    server.enforceGameTypeForPlayers(mode);
                }
            }
            server.setGuestCommandAccess(options.guestOpLevel > 0);

            if (server.getGameRules() != null) {
                server.getGameRules().set(net.minecraft.world.level.gamerules.GameRules.PVP, options.pvp, server);
            }

            if (options.difficulty != null) {
                Difficulty diff = Difficulty.byName(options.difficulty);
                if (diff != null) {
                    server.setDifficulty(diff, true);
                }
            }

            if (options.viewDistanceCap > 0 && server.getPlayerList() != null) {
                server.getPlayerList().setViewDistance(options.viewDistanceCap);
            }
        });
    }

    private void updateGuestSnapshot(IntegratedServer server) {
        if (server == null || server.getPlayerList() == null) {
            guestSnapshot.clear();
            return;
        }
        UUID hostId = server.getSingleplayerProfile() != null ? server.getSingleplayerProfile().id() : null;
        List<GuestInfo> list = new ArrayList<>();
        for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
            if (hostId != null && hostId.equals(sp.getUUID())) continue;
            int ping = sp.connection != null ? sp.connection.latency() : 0;
            boolean op = server.getPlayerList().isOp(new NameAndId(sp.getGameProfile()));
            String mode = sp.gameMode.getGameModeForPlayer().getName();
            list.add(new GuestInfo(sp.getUUID(), sp.getGameProfile().name(), ping, op, mode));
        }
        guestSnapshot.clear();
        guestSnapshot.addAll(list);
    }

    @Override
    public List<GuestInfo> guests() {
        IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
        if (server == null) return Collections.emptyList();
        if (server.isSameThread()) {
            updateGuestSnapshot(server);
        } else {
            server.execute(() -> updateGuestSnapshot(server));
        }
        return new ArrayList<>(guestSnapshot);
    }

    @Override
    public void guestAction(UUID id, GuestAction action) {
        IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
        if (server == null || server.getPlayerList() == null) return;

        server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            NameAndId target = player != null ? new NameAndId(player.getGameProfile()) : new NameAndId(id, "");

            switch (action) {
                case KICK -> {
                    if (player != null && player.connection != null) {
                        player.connection.disconnect(Component.literal("Kicked by host"));
                    }
                }
                case BAN -> {
                    UserBanList bans = server.getPlayerList().getBans();
                    bans.add(new UserBanListEntry(target));
                    if (player != null && player.connection != null) {
                        player.connection.disconnect(Component.literal("Banned by host"));
                    }
                }
                case OP -> server.getPlayerList().op(target);
                case DEOP -> server.getPlayerList().deop(target);
                case SURVIVAL -> {
                    if (player != null) player.setGameMode(GameType.SURVIVAL);
                }
                case CREATIVE -> {
                    if (player != null) player.setGameMode(GameType.CREATIVE);
                }
                case ADVENTURE -> {
                    if (player != null) player.setGameMode(GameType.ADVENTURE);
                }
                case SPECTATOR -> {
                    if (player != null) player.setGameMode(GameType.SPECTATOR);
                }
                case TP_TO_ME -> {
                    if (player != null && server.getSingleplayerProfile() != null) {
                        ServerPlayer host = server.getPlayerList().getPlayer(server.getSingleplayerProfile().id());
                        if (host != null) {
                            player.teleportTo(host.level(), host.getX(), host.getY(), host.getZ(), Collections.emptySet(), host.getYRot(), host.getXRot(), true);
                        }
                    }
                }
                case MUTE -> {
                    mutedGuests.add(id);
                    Minecraft mc = Minecraft.getInstance();
                    if (mc != null) {
                        mc.execute(() -> {
                            if (mc.getPlayerSocialManager() != null) {
                                mc.getPlayerSocialManager().hidePlayer(id);
                            }
                        });
                    }
                }
            }
        });
    }

    @Override
    public void setLoginFilter(Predicate<UUID> allowed) {
        loginFilter = allowed;
    }

    @Override
    public void connectToLocal(int port, String displayName) {
        leaveWorld(() -> {
            Minecraft mc = Minecraft.getInstance();
            ServerData data = new ServerData(displayName, "127.0.0.1:" + port, ServerData.Type.OTHER);
            ConnectScreen.startConnecting(null, mc, ServerAddress.parseString("127.0.0.1:" + port), data, false, null);
        });
    }

    @Override
    public void connectToServer(String address) {
        leaveWorld(() -> {
            Minecraft mc = Minecraft.getInstance();
            ServerData data = new ServerData(address, address, ServerData.Type.OTHER);
            ConnectScreen.startConnecting(null, mc, ServerAddress.parseString(address), data, false, null);
        });
    }

    @Override
    public boolean inWorldOrServer() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null;
    }

    public static void onWorldDisconnect() {
        loopbackScope = false;
        loginFilter = null;
        mutedGuests.clear();
    }

    @Override
    public void leaveWorld(Runnable then) {
        onWorldDisconnect();
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            mc.disconnect(new TitleScreen(), false);
        }
        if (then != null) {
            mc.execute(then);
        }
    }

    @Override
    public GameInfo gameInfo() {
        String mcVer = "26.3";
        try {
            if (SharedConstants.getCurrentVersion() != null) {
                mcVer = SharedConstants.getCurrentVersion().name();
            }
        } catch (Throwable ignored) {}
        return new GameInfo(mcVer, "fabric", "plain", "", "", "", "1.9.18");
    }

    @Override
    public void openFriendsScreen() {
        Minecraft mc = Minecraft.getInstance();
        mc.setScreenAndShow(new FriendsScreen26(mc.gui.screen()));
    }

    @Override
    public void openHostScreen() {
        Minecraft mc = Minecraft.getInstance();
        mc.setScreenAndShow(new HostScreen26(mc.gui.screen()));
    }

    @Override
    public void toast(String title, String body, String acceptAction, String denyAction) {
        SocialToast26.show(title, body, acceptAction, denyAction);
    }

    @Override
    public void chatAction(String text, String actionId) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui != null && mc.gui.chatListener() != null) {
            Component comp = Component.literal(text).withStyle(style ->
                style.withClickEvent(new ClickEvent.RunCommand("/lads:social " + actionId))
                     .withUnderlined(true)
                     .withColor(0x3B82F6));
            mc.gui.chatListener().handleSystemMessage(comp, false);
        }
    }

    @Override
    public String worldId() {
        IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
        return server != null ? server.getWorldPath(LevelResource.ROOT).getFileName().toString() : null;
    }

    @Override
    public String worldName() {
        IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
        return server != null ? server.getWorldData().getLevelName() : null;
    }

    public static void onChatActionClicked(String actionId) {
        if (actionId == null || actionId.isEmpty()) return;
        LOGGER.info("P2P chat action clicked: {}", actionId);
        // TODO(S3): SocialState.get().handleAction(actionId) once S3 merges
        try {
            Class<?> stateClass = Class.forName("com.thelads.core.client.social.SocialState");
            Method getMethod = stateClass.getMethod("get");
            Object instance = getMethod.invoke(null);
            if (instance != null) {
                Method handleMethod = stateClass.getMethod("handleAction", String.class);
                handleMethod.invoke(instance, actionId);
            }
        } catch (ClassNotFoundException | NoSuchMethodException ignored) {
            // S3 not merged into this lane yet; logged above
        } catch (Throwable t) {
            LOGGER.warn("Failed to dispatch action {} to SocialState", actionId, t);
        }
    }

    public static int getOnlineFriendsCount() {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.getPlayerSocialManager() != null && mc.getPlayerSocialManager().isFriendListEnabled()) {
                var handler = mc.getPlayerSocialManager().getPresenceHandler();
                var presence = handler != null ? handler.getLatestPresence() : null;
                if (presence != null && presence.presence() != null) {
                    int count = 0;
                    for (var p : presence.presence()) {
                        if (p.status() != com.mojang.authlib.services.response.PresenceStatus.OFFLINE) {
                            count++;
                        }
                    }
                    return count;
                }
            }
        } catch (Throwable ignored) {}
        return 0;
    }

    public static Component friendsButtonText() {
        int count = getOnlineFriendsCount();
        return count > 0 ? Component.literal("Friends (" + count + ")") : Component.literal("Friends");
    }

    private static int findFreePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            socket.setReuseAddress(true);
            return socket.getLocalPort();
        } catch (Throwable t) {
            return 25565;
        }
    }
}
