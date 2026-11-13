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
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRules;
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
    private volatile long lastGuestRefresh;

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

            // QA only (LADS_QA_MODSYNC=host against a local dev backend): both games are offline accounts, so the host does not ask Mojang
            if ("host".equals(System.getenv("LADS_QA_MODSYNC")) && com.thelads.core.client.social.SocialState.get().client().local()) server.setUsesAuthentication(false);
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
        // A real port, as openToFriends: vanilla binds port 0 to a random one but advertises "0" on the LAN and in getPort()
        if (!server.publishServer(MinecraftServer.MultiplayerScope.LAN, options.guestOpLevel > 0, lanPort(options))) {
            LOGGER.warn("Failed to open world to LAN");
            return;
        }
        applyHostOptions(options);
    }

    @Override
    public void closeHosting(String reason) {
        IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
        P2PHostState26.setLoopbackScope(false);
        P2PHostState26.clearMutedGuests(); // the login filter stays (HostSession leaves it refusing everyone) until the world closes
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
        if (server == null || options == null) return;

        server.execute(() -> {
            // As vanilla's World Options screen applies them, each only when it changes (some re-send game modes to everyone)
            GameType guestMode = options.guestGameMode != null ? GameType.byName(options.guestGameMode) : null;
            if (guestMode != null && guestMode != server.getDefaultGameType()) server.setWorldGameType(guestMode);
            if (options.forceGameMode != server.forceGameMode()) server.setForceGameMode(options.forceGameMode);
            if (options.allowCommands != null && options.allowCommands != server.getWorldData().isAllowCommands())
                server.setWorldAllowCommands(options.allowCommands);
            GameType hostMode = options.hostGameMode != null ? GameType.byName(options.hostGameMode) : null;
            if (hostMode != null && hostMode != server.getPersonalGameMode()) server.setPersonalGameType(hostMode);
            if (server.getGuestCommandAccess() != options.guestOpLevel > 0) server.setGuestCommandAccess(options.guestOpLevel > 0);

            if (server.getGameRules() != null) {
                server.getGameRules().set(net.minecraft.world.level.gamerules.GameRules.PVP, options.pvp, server);
                if (options.gamerules != null) options.gamerules.forEach((rule, value) -> setRule(server, rule, value));
            }

            if (options.difficulty != null) {
                Difficulty diff = Difficulty.byName(options.difficulty);
                if (diff != null) {
                    server.setDifficulty(diff, true);
                }
            }

            // Per guest (ChunkMapGuestViewMixin): the integrated server resets its own view distance to the host's every tick
            P2PHostState26.setGuestViewCap(options.viewDistanceCap);
        });
    }

    /** Pre-26 rule names (HostScreenLogic.RULES, kept in lads-host.json) and the 26.x rules they became. */
    private static final Map<String, GameRule<?>> RULES = rules();
    /** "disableElytraMovementCheck" became "elytra_movement_check" (the opposite); "doFireTick" a fire spread radius (0 = off). */
    private static final String INVERTED = "disableElytraMovementCheck", FIRE_TICK = "doFireTick";

    private static Map<String, GameRule<?>> rules() {
        Map<String, GameRule<?>> m = new LinkedHashMap<>();
        m.put("keepInventory", GameRules.KEEP_INVENTORY);
        m.put("doDaylightCycle", GameRules.ADVANCE_TIME);
        m.put("doWeatherCycle", GameRules.ADVANCE_WEATHER);
        m.put("mobGriefing", GameRules.MOB_GRIEFING);
        m.put(FIRE_TICK, GameRules.FIRE_SPREAD_RADIUS_AROUND_PLAYER);
        m.put("doMobSpawning", GameRules.SPAWN_MOBS);
        m.put("naturalRegeneration", GameRules.NATURAL_HEALTH_REGENERATION);
        m.put("playersSleepingPercentage", GameRules.PLAYERS_SLEEPING_PERCENTAGE);
        m.put("doImmediateRespawn", GameRules.IMMEDIATE_RESPAWN);
        m.put("showDeathMessages", GameRules.SHOW_DEATH_MESSAGES);
        m.put("announceAdvancements", GameRules.SHOW_ADVANCEMENT_MESSAGES);
        m.put("doInsomnia", GameRules.SPAWN_PHANTOMS);
        m.put("fallDamage", GameRules.FALL_DAMAGE);
        m.put("drowningDamage", GameRules.DROWNING_DAMAGE);
        m.put("fireDamage", GameRules.FIRE_DAMAGE);
        m.put("freezeDamage", GameRules.FREEZE_DAMAGE);
        m.put("doTileDrops", GameRules.BLOCK_DROPS);
        m.put("doEntityDrops", GameRules.ENTITY_DROPS);
        m.put("doMobLoot", GameRules.MOB_DROPS);
        m.put("forgiveDeadPlayers", GameRules.FORGIVE_DEAD_PLAYERS);
        m.put("universalAnger", GameRules.UNIVERSAL_ANGER);
        m.put(INVERTED, GameRules.ELYTRA_MOVEMENT_CHECK);
        return m;
    }

    /** The rule as the Host screen shows it: "true"/"false", or a number. Fire Tick is on while fire spreads at all. */
    private static String ruleValue(MinecraftServer server, String name) {
        Object v = server.getGameRules().get(RULES.get(name));
        if (FIRE_TICK.equals(name)) return String.valueOf(v instanceof Integer i && i != 0);
        if (v instanceof Boolean b) return String.valueOf(INVERTED.equals(name) != b);
        return String.valueOf(v);
    }

    @SuppressWarnings("unchecked")
    static void setRule(MinecraftServer server, String name, String value) {
        GameRule<?> rule = RULES.get(name);
        if (rule == null || value == null) return;
        GameRules rules = server.getGameRules();
        try {
            if (value.equals(ruleValue(server, name))) return;
            if (FIRE_TICK.equals(name)) {
                rules.set((GameRule<Integer>) rule, Boolean.parseBoolean(value) ? 128 : 0, server);
            } else if (rules.get(rule) instanceof Integer) {
                rules.set((GameRule<Integer>) rule, Integer.parseInt(value.trim()), server);
            } else {
                rules.set((GameRule<Boolean>) rule, INVERTED.equals(name) != Boolean.parseBoolean(value), server);
            }
        } catch (RuntimeException e) {
            LOGGER.warn("Game rule {} = {} not applied: {}", name, value, e.toString());
        }
    }

    @Override
    public Map<String, String> liveGamerules() {
        IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
        if (server == null || server.getGameRules() == null) return Map.of();
        Map<String, String> out = new LinkedHashMap<>();
        for (String name : RULES.keySet()) {
            try {
                out.put(name, ruleValue(server, name));
            } catch (RuntimeException ignored) { // a rule this world's feature flags lack
            }
        }
        return out;
    }

    @Override
    public void fillLiveOptions(HostOptions options) {
        IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
        if (server == null || options == null) return;
        options.allowCommands = server.getWorldData().isAllowCommands();
        if (server.getPersonalGameMode() != null) options.hostGameMode = server.getPersonalGameMode().getName();
    }

    @Override
    public boolean canSharePacks() {
        return true;
    }

    @Override
    public Map<String, String> shareablePacks() {
        return HostPacks26.shareable();
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
        long now = System.currentTimeMillis();
        if (server.isSameThread()) {
            updateGuestSnapshot(server);
        } else if (now - lastGuestRefresh >= 1000L) { // the host screen asks every frame: one server task a second, as on 1.8.9
            lastGuestRefresh = now;
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
    public Path gameDir() {
        return net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir();
    }

    @Override
    public void quitGame() {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(mc::stop);
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

    /** A clicked "/lads:social <token>" chat link: handled here, never sent to the server. */
    public static void onChatActionClicked(String token) {
        SocialState.get().handleChatClick(token);
    }

    public static Component friendsButtonText() {
        int count = SocialState.get().onlineFriendCount();
        return count > 0 ? Component.literal("Friends (" + count + ")") : Component.literal("Friends");
    }

    /** The host's LAN port when it is free, else any free one (vanilla's empty port box). */
    private static int lanPort(HostOptions options) {
        int p = options.port;
        if (p >= 1024 && p <= 65535) {
            try (ServerSocket socket = new ServerSocket(p)) {
                return p;
            } catch (Throwable t) {
                LOGGER.warn("LAN port {} is taken, using a free one", p);
            }
        }
        return findFreePort();
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
