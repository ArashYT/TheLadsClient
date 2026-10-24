package com.thelads.core.v1_20_1.social;

import com.mojang.authlib.GameProfile;
import com.thelads.core.LadsVersion;
import com.thelads.core.client.social.HostOptions;
import com.thelads.core.client.social.SocialBridge;
import com.thelads.core.client.social.SocialState;
import com.thelads.core.client.social.ui.SocialToasts;
import com.thelads.core.v1_20_1.mixin.social.IntegratedServerAccessor1201;
import com.thelads.core.v1_20_1.mixin.social.ServerConnectionListenerAccessor1201;
import io.netty.channel.ChannelFuture;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.client.server.LanServerPinger;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerConnectionListener;
import net.minecraft.server.players.UserBanList;
import net.minecraft.server.players.UserBanListEntry;
import net.minecraft.util.HttpUtil;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 1.20.1 implementation of SocialBridge.
 */
public final class SocialBridge1201 implements SocialBridge {
    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore-1.20.1-social");

    private static volatile boolean loopbackPublish = false;
    private static volatile boolean suppressLanPinger = false;
    private static volatile Predicate<UUID> loginFilter = null;
    private static volatile int localConnectPort = -1;
    private static volatile String localConnectDisplayName = null;
    private static final Set<UUID> mutedGuests = Collections.synchronizedSet(new HashSet<>());
    private static volatile List<GuestInfo> cachedGuests = Collections.emptyList();

    public static boolean isLoopbackPublish() {
        return loopbackPublish;
    }

    public static boolean isSuppressLanPinger() {
        return suppressLanPinger;
    }

    public static Predicate<UUID> getLoginFilter() {
        return loginFilter;
    }

    public static synchronized String consumeLocalConnectDisplayName(String host, int port) {
        if (("127.0.0.1".equals(host) || "localhost".equalsIgnoreCase(host)) && port == localConnectPort) {
            String name = localConnectDisplayName;
            localConnectDisplayName = null;
            localConnectPort = -1;
            return name;
        }
        return null;
    }

    public static boolean isMuted(UUID uuid) {
        return uuid != null && mutedGuests.contains(uuid);
    }

    public static synchronized void resetLoopbackState() {
        loopbackPublish = false;
        suppressLanPinger = false;
        localConnectPort = -1;
        localConnectDisplayName = null;
        loginFilter = null;
        mutedGuests.clear();
        cachedGuests = Collections.emptyList();
    }

    public static void updateGuestSnapshot(IntegratedServer server) {
        if (server == null) {
            cachedGuests = Collections.emptyList();
            return;
        }
        List<GuestInfo> list = new ArrayList<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!server.isSingleplayerOwner(player.getGameProfile())) {
                list.add(new GuestInfo(
                    player.getUUID(),
                    player.getGameProfile().getName(),
                    player.latency,
                    server.getPlayerList().isOp(player.getGameProfile()),
                    player.gameMode.getGameModeForPlayer().getName()
                ));
            }
        }
        cachedGuests = Collections.unmodifiableList(list);
    }

    /** A clicked "/lads:social <token>" chat link: handled here, never sent to the server. */
    public static void handleChatAction(String token) {
        SocialState.get().handleChatClick(token);
    }

    public static Component friendsButtonLabel() {
        int count = SocialState.get().onlineFriendCount();
        if (count > 0) {
            return Component.literal("Friends (" + count + ")");
        }
        return Component.literal("Friends");
    }

    @Override
    public boolean canHost() {
        Minecraft mc = Minecraft.getInstance();
        return mc.hasSingleplayerServer() && mc.getSingleplayerServer() != null && !mc.getSingleplayerServer().isPublished();
    }

    @Override
    public synchronized int openToFriends(HostOptions options) {
        Minecraft mc = Minecraft.getInstance();
        IntegratedServer server = mc.getSingleplayerServer();
        if (server == null || server.isPublished()) {
            return -1;
        }

        if (options != null) {
            Path worldDir = server.getWorldPath(LevelResource.ROOT);
            try {
                options.save(worldDir);
            } catch (Exception e) {
                LOGGER.warn("Failed to persist lads-host.json: {}", e.getMessage());
            }
        }

        int port = HttpUtil.getAvailablePort();
        GameType gameType = parseGameType(options != null ? options.guestGameMode : "survival");
        boolean allowCheats = options != null && options.guestOpLevel > 0;

        loopbackPublish = true;
        suppressLanPinger = true;
        try {
            boolean success = server.publishServer(gameType, allowCheats, port);
            if (!success) {
                return -1;
            }
            if (options != null) {
                applyHostOptions(options);
            }
            LOGGER.info("World published peer-to-peer on loopback port {}", port);
            return port;
        } finally {
            loopbackPublish = false;
            suppressLanPinger = false;
        }
    }

    @Override
    public synchronized void openToLan(HostOptions options) {
        Minecraft mc = Minecraft.getInstance();
        IntegratedServer server = mc.getSingleplayerServer();
        if (server == null || server.isPublished()) return;

        if (options != null) {
            Path worldDir = server.getWorldPath(LevelResource.ROOT);
            try {
                options.save(worldDir);
            } catch (Exception e) {
                LOGGER.warn("Failed to persist lads-host.json: {}", e.getMessage());
            }
        }

        int port = HttpUtil.getAvailablePort();
        GameType gameType = parseGameType(options != null ? options.guestGameMode : "survival");
        boolean allowCheats = options != null && options.guestOpLevel > 0;

        server.publishServer(gameType, allowCheats, port);
        if (options != null) {
            applyHostOptions(options);
        }
        LOGGER.info("World published to LAN on port {}", port);
    }

    @Override
    public synchronized void closeHosting(String reason) {
        Minecraft mc = Minecraft.getInstance();
        IntegratedServer server = mc.getSingleplayerServer();
        if (server == null) return;

        server.execute(() -> {
            Component kickMsg = Component.literal(reason != null ? reason : "Host closed the world");
            for (ServerPlayer player : List.copyOf(server.getPlayerList().getPlayers())) {
                if (!server.isSingleplayerOwner(player.getGameProfile())) {
                    player.connection.disconnect(kickMsg);
                }
            }

            // Close TCP channels on ServerConnectionListener via Accessor mixin
            ServerConnectionListener connection = server.getConnection();
            if (connection instanceof ServerConnectionListenerAccessor1201 connAccessor) {
                List<ChannelFuture> channels = connAccessor.ladsGetChannels();
                if (channels != null) {
                    synchronized (channels) {
                        for (ChannelFuture future : channels) {
                            if (future != null && future.channel() != null) {
                                future.channel().close().syncUninterruptibly();
                            }
                        }
                        channels.clear();
                    }
                }
            }

            // Interrupt LAN pinger if running via Accessor mixin
            if (server instanceof IntegratedServerAccessor1201 isAccessor) {
                LanServerPinger pinger = isAccessor.ladsGetLanPinger();
                if (pinger != null) {
                    pinger.interrupt();
                    isAccessor.ladsSetLanPinger(null);
                }
                // Unpublish port so isPublished() is false and canHost() is true
                isAccessor.ladsSetPublishedPort(-1);
            }

            updateGuestSnapshot(server);
            resetLoopbackState();
        });
    }

    @Override
    public void applyHostOptions(HostOptions options) {
        if (options == null) return;
        Minecraft mc = Minecraft.getInstance();
        IntegratedServer server = mc.getSingleplayerServer();
        if (server == null) return;

        server.execute(() -> {
            server.setPvpAllowed(options.pvp);
            if (options.difficulty != null) {
                try {
                    Difficulty diff = Difficulty.byName(options.difficulty.toLowerCase(Locale.ROOT));
                    if (diff != null) {
                        server.setDifficulty(diff, true);
                    }
                } catch (Exception ignored) {}
            }
            GameType gameType = parseGameType(options.guestGameMode);
            server.setDefaultGameType(gameType);
            server.getPlayerList().setAllowCheatsForAllPlayers(options.guestOpLevel > 0);

            if (options.viewDistanceCap > 0) {
                server.getPlayerList().setViewDistance(options.viewDistanceCap);
            }

            if (options.gamerules != null && !options.gamerules.isEmpty()) {
                GameRules rules = server.getGameRules();
                GameRules.visitGameRuleTypes(new GameRules.GameRuleTypeVisitor() {
                    @Override
                    @SuppressWarnings("unchecked")
                    public <T extends GameRules.Value<T>> void visit(GameRules.Key<T> key, GameRules.Type<T> type) {
                        String value = options.gamerules.get(key.getId());
                        if (value != null) {
                            GameRules.Value<T> rule = rules.getRule(key);
                            if (rule instanceof GameRules.BooleanValue boolRule) {
                                boolRule.set(Boolean.parseBoolean(value), server);
                            } else if (rule instanceof GameRules.IntegerValue intRule) {
                                try {
                                    intRule.set(Integer.parseInt(value), server);
                                } catch (NumberFormatException ignored) {}
                            }
                        }
                    }
                });
            }
        });
    }

    @Override
    public List<GuestInfo> guests() {
        Minecraft mc = Minecraft.getInstance();
        IntegratedServer server = mc.getSingleplayerServer();
        if (server == null) return Collections.emptyList();
        if (server.isSameThread()) {
            updateGuestSnapshot(server);
        } else {
            server.execute(() -> updateGuestSnapshot(server));
        }
        return cachedGuests;
    }

    @Override
    public void guestAction(UUID id, GuestAction action) {
        if (id == null || action == null) return;
        if (action == GuestAction.MUTE) {
            if (mutedGuests.contains(id)) mutedGuests.remove(id);
            else mutedGuests.add(id);
            Minecraft mc = Minecraft.getInstance();
            if (mc != null) {
                IntegratedServer server = mc.getSingleplayerServer();
                if (server != null) updateGuestSnapshot(server);
            }
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;
        IntegratedServer server = mc.getSingleplayerServer();
        if (server == null) return;

        server.execute(() -> {
            ServerPlayer target = server.getPlayerList().getPlayer(id);
            switch (action) {
                case KICK -> {
                    if (target != null) {
                        target.connection.disconnect(Component.literal("Kicked by host"));
                    }
                }
                case BAN -> {
                    UserBanList bans = server.getPlayerList().getBans();
                    GameProfile profile = target != null ? target.getGameProfile() : new GameProfile(id, null);
                    bans.add(new UserBanListEntry(profile, null, "Host", null, "Banned by host"));
                    if (target != null) {
                        target.connection.disconnect(Component.literal("Banned by host"));
                    }
                }
                case OP -> {
                    if (target != null) server.getPlayerList().op(target.getGameProfile());
                }
                case DEOP -> {
                    if (target != null) server.getPlayerList().deop(target.getGameProfile());
                }
                case SURVIVAL -> {
                    if (target != null) target.setGameMode(GameType.SURVIVAL);
                }
                case CREATIVE -> {
                    if (target != null) target.setGameMode(GameType.CREATIVE);
                }
                case ADVENTURE -> {
                    if (target != null) target.setGameMode(GameType.ADVENTURE);
                }
                case SPECTATOR -> {
                    if (target != null) target.setGameMode(GameType.SPECTATOR);
                }
                case TP_TO_ME -> {
                    if (target != null && mc.player != null) {
                        ServerPlayer host = server.getPlayerList().getPlayer(mc.player.getUUID());
                        if (host != null) {
                            target.stopRiding();
                            target.teleportTo(host.serverLevel(), host.getX(), host.getY(), host.getZ(), host.getYRot(), host.getXRot());
                        }
                    }
                }
                case MUTE -> {
                    if (mutedGuests.contains(id)) mutedGuests.remove(id);
                    else mutedGuests.add(id);
                }
            }
            updateGuestSnapshot(server);
        });
    }

    @Override
    public void setLoginFilter(Predicate<UUID> allowed) {
        loginFilter = allowed;
    }

    @Override
    public void connectToLocal(int port, String displayName) {
        synchronized (SocialBridge1201.class) {
            localConnectPort = port;
            localConnectDisplayName = displayName;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;
        ServerAddress address = new ServerAddress("127.0.0.1", port);
        ServerData data = new ServerData(displayName != null ? displayName : "P2P Host", "127.0.0.1:" + port, false);
        mc.execute(() -> ConnectScreen.startConnecting(mc.screen != null ? mc.screen : new TitleScreen(), mc, address, data, false)); // social threads call this
    }

    @Override
    public void connectToServer(String address) {
        synchronized (SocialBridge1201.class) {
            localConnectPort = -1;
            localConnectDisplayName = null;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;
        ServerAddress addr = ServerAddress.parseString(address);
        ServerData data = new ServerData(address, address, false);
        mc.execute(() -> ConnectScreen.startConnecting(mc.screen != null ? mc.screen : new TitleScreen(), mc, addr, data, false));
    }

    @Override
    public boolean inWorldOrServer() {
        Minecraft mc = Minecraft.getInstance();
        return mc != null && mc.level != null;
    }

    @Override
    public void leaveWorld(Runnable then) {
        Minecraft mc = Minecraft.getInstance();
        resetLoopbackState();
        mc.execute(() -> { // social threads call this; runs at once on the game thread
            if (mc.level != null) {
                mc.level.disconnect();
            }
            if (mc.hasSingleplayerServer()) {
                mc.clearLevel(new net.minecraft.client.gui.screens.GenericDirtMessageScreen(Component.translatable("menu.savingLevel")));
            } else {
                mc.clearLevel();
            }
            mc.setScreen(new TitleScreen());
            if (then != null) {
                then.run();
            }
        });
    }

    @Override
    public GameInfo gameInfo() {
        String mcVer = Minecraft.getInstance().getLaunchedVersion();
        if (mcVer == null || mcVer.isEmpty()) mcVer = "1.20.1";
        String loader = "fabric";
        if (FabricLoader.getInstance().isModLoaded("connectormod")) {
            loader = "forge";
        }
        String profileKind = "plain";
        String packId = "";
        String packVersion = "";
        Path gameDir = FabricLoader.getInstance().getGameDir();
        Path packJson = gameDir.resolve("pack.json");
        if (Files.isRegularFile(packJson)) {
            profileKind = "pack";
            try {
                String content = Files.readString(packJson);
                if (content.contains("\"id\"")) {
                    packId = extractJsonValue(content, "id");
                }
                if (content.contains("\"version\"")) {
                    packVersion = extractJsonValue(content, "version");
                }
            } catch (Exception ignored) {}
        }
        return new GameInfo(mcVer, loader, profileKind, packId, packVersion, "", LadsVersion.VERSION);
    }

    @Override
    public void openFriendsScreen() {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null) mc.setScreen(new FriendsScreen1201(mc.screen));
    }

    @Override
    public void openHostScreen() {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null) mc.setScreen(new HostScreen1201(mc.screen));
    }

    @Override
    public void toast(String title, String body, String acceptAction, String denyAction) {
        SocialToasts.showAction(title, body, acceptAction, denyAction);
    }

    @Override
    public void chatAction(String text, String actionId) {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.gui != null && mc.gui.getChat() != null) {
            MutableComponent comp = Component.literal(text != null ? text : "");
            if (actionId != null && !actionId.isEmpty()) {
                comp.withStyle(style -> style
                    .withColor(TextColor.fromRgb(0x55FFFF))
                    .withUnderlined(true)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/lads:social " + actionId))
                );
            }
            mc.execute(() -> mc.gui.getChat().addMessage(comp));
        }
    }

    @Override
    public String worldId() {
        Minecraft mc = Minecraft.getInstance();
        IntegratedServer server = mc.getSingleplayerServer();
        if (server != null) {
            return server.getWorldData().getLevelName();
        }
        return null;
    }

    @Override
    public String worldName() {
        return worldId();
    }

    @Override
    public Path worldDir() {
        IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
        return server == null ? null : server.getWorldPath(LevelResource.ROOT);
    }

    @Override
    public CompletableFuture<Void> saveWorld() {
        IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
        return server == null ? CompletableFuture.completedFuture(null)
            : CompletableFuture.runAsync(() -> server.saveEverything(true, true, true), server); // save-all flush, on the server thread
    }

    @Override
    public void runOnGameThread(Runnable task) {
        Minecraft.getInstance().execute(task);
    }

    private static GameType parseGameType(String mode) {
        if (mode == null) return GameType.SURVIVAL;
        return switch (mode.toLowerCase(Locale.ROOT)) {
            case "creative" -> GameType.CREATIVE;
            case "adventure" -> GameType.ADVENTURE;
            case "spectator" -> GameType.SPECTATOR;
            default -> GameType.SURVIVAL;
        };
    }

    private static String extractJsonValue(String json, String key) {
        String pattern = "\"" + key + "\"\\s*:\\s*\"([^\"]+)\"";
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern);
        java.util.regex.Matcher m = p.matcher(json);
        if (m.find()) {
            return m.group(1);
        }
        return "";
    }
}
