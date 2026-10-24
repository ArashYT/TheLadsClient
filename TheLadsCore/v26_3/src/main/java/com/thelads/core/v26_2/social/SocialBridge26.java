package com.thelads.core.v26_2.social;

import com.thelads.core.client.social.HostOptions;
import com.thelads.core.client.social.SocialBridge;
import com.thelads.core.client.social.SocialState;
import com.thelads.core.client.social.ui.SocialToasts;
import com.thelads.core.v26_2.adapter.GuiGraphicsExtractorLadsAdapter;
import com.thelads.core.v26_2.gui.CommonInput;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
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

/**
 * Fabric 26.3 implementation of {@link SocialBridge}.
 */
public class SocialBridge26 implements SocialBridge {
    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore-Social");
    private final java.util.concurrent.CopyOnWriteArrayList<GuestInfo> guestSnapshot = new java.util.concurrent.CopyOnWriteArrayList<>();

    public static boolean isMuted(UUID id) {
        return P2PHostState26.isMuted(id);
    }

    public static void muteGuest(UUID id) {
        P2PHostState26.muteGuest(id);
    }

    public static void clearMutedGuests() {
        P2PHostState26.clearMutedGuests();
    }

    public static boolean isLoopbackScope() {
        return P2PHostState26.isLoopbackScope();
    }

    public static void setLoopbackScope(boolean enabled) {
        P2PHostState26.setLoopbackScope(enabled);
    }

    public static Predicate<UUID> getLoginFilter() {
        return P2PHostState26.getLoginFilter();
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
            Path worldDir = server.getWorldPath(LevelResource.ROOT);
            options.save(worldDir);

            P2PHostState26.setLoopbackScope(true);
            int port = findFreePort();
            boolean success = false;
            try {
                success = server.publishServer(MinecraftServer.MultiplayerScope.LAN, options.guestOpLevel > 0, port);
            } finally {
                P2PHostState26.setLoopbackScope(false);
            }
            if (!success) {
                return -1;
            }

            applyHostOptions(options);
            return server.getPort();
        } catch (Throwable t) {
            LOGGER.error("Failed to open world to friends", t);
            P2PHostState26.setLoopbackScope(false);
            return -1;
        }
    }

    @Override
    public void openToLan(HostOptions options) {
        IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
        if (server == null || !server.isRunning()) return;

        P2PHostState26.setLoopbackScope(false);
        server.publishServer(MinecraftServer.MultiplayerScope.LAN, options.guestOpLevel > 0, 0);
        applyHostOptions(options);
    }

    @Override
    public void closeHosting(String reason) {
        IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
        P2PHostState26.reset();
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
                    P2PHostState26.muteGuest(id);
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
        P2PHostState26.setLoginFilter(allowed);
    }

    @Override
    public void connectToLocal(int port, String displayName) {
        Minecraft.getInstance().execute(() -> { // social threads call this; SocialState left any world before
            Minecraft mc = Minecraft.getInstance();
            ServerData data = new ServerData(displayName, "127.0.0.1:" + port, ServerData.Type.OTHER);
            ConnectScreen.startConnecting(null, mc, ServerAddress.parseString("127.0.0.1:" + port), data, false, null);
        });
    }

    @Override
    public void connectToServer(String address) {
        Minecraft.getInstance().execute(() -> {
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
        P2PHostState26.reset();
    }

    @Override
    public void leaveWorld(Runnable then) {
        onWorldDisconnect();
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> { // social threads call this; runs at once on the game thread
            if (mc.level != null) {
                mc.disconnect(new TitleScreen(), false);
            }
            if (then != null) {
                mc.execute(then);
            }
        });
    }

    @Override
    public GameInfo gameInfo() {
        String mcVer = "26.3";
        try {
            if (SharedConstants.getCurrentVersion() != null) {
                mcVer = SharedConstants.getCurrentVersion().name();
            }
        } catch (Throwable ignored) {}
        String ladsVer = "1.9.18";
        try {
            ladsVer = net.fabricmc.loader.api.FabricLoader.getInstance()
                .getModContainer("theladscore")
                .map(c -> c.getMetadata().getVersion().getFriendlyString())
                .orElse("1.9.18");
        } catch (Throwable ignored) {}
        return new GameInfo(mcVer, "fabric", "plain", "", "", "", ladsVer);
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
        SocialToasts.showAction(title, body, acceptAction, denyAction);
    }

    @Override
    public void chatAction(String text, String actionId) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui != null && mc.gui.chatListener() != null) {
            Component comp = Component.literal(text).withStyle(style ->
                style.withClickEvent(new ClickEvent.RunCommand("/lads:social " + actionId))
                     .withUnderlined(true)
                     .withColor(0x3B82F6));
            mc.execute(() -> mc.gui.chatListener().handleSystemMessage(comp, false));
        }
    }

    @Override
    public String worldId() {
        IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
        return server != null ? server.getWorldPath(LevelResource.ROOT).getFileName().toString() : null;
    }

    @Override
    public Path worldDir() {
        IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
        return server == null ? null : server.getWorldPath(LevelResource.ROOT);
    }

    @Override
    public java.util.concurrent.CompletableFuture<Void> saveWorld() {
        IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
        return server == null ? java.util.concurrent.CompletableFuture.completedFuture(null)
            : java.util.concurrent.CompletableFuture.runAsync(() -> server.saveEverything(true, true, true), server); // on the server thread
    }

    @Override
    public void runOnGameThread(Runnable task) {
        Minecraft.getInstance().execute(task);
    }

    @Override
    public String worldName() {
        IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
        return server != null ? server.getWorldData().getLevelName() : null;
    }

    /** The social toasts over every screen (menus too) and clickable there; with no screen open the HUD draws them (GuiMixin). */
    public static void registerToasts() {
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            ScreenEvents.afterExtract(screen).register((s, g, mx, my, delta) -> {
                if (!SocialToasts.isEmpty()) SocialToasts.render(new GuiGraphicsExtractorLadsAdapter(g, client.font), s.width, s.height, mx, my);
            });
            ScreenMouseEvents.allowMouseClick(screen).register((s, click) ->
                !SocialToasts.mouseClicked(click.x(), click.y(), CommonInput.button(click.button()), s.width, s.height));
        });
    }

    /** A clicked "/lads:social <id>" chat link: handled here, never sent to the server. */
    public static void onChatActionClicked(String actionId) {
        SocialState.get().handleAction(actionId);
    }

    public static Component friendsButtonText() {
        int count = SocialState.get().onlineFriendCount();
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
