package com.thelads.core.client;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.thelads.core.client.bridge.LadsGameBridge;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server rules: keeps the client from getting players banned (the owner's permanent rule: the least possible ban risk).
 * Hypixel's banned modules are off on Hypixel. The risky ones (Xaero's minimap and world map, Stack Refill, use-while-mining) are off on
 * every server that bans them AND on every server whose rules we don't know; only singleplayer, LAN, our own servers and servers known
 * to allow them keep them. A player can still turn a blocked module on for one server, through the red "at your own risk" warning
 * (LadsSettingsScreen -> {@link #bypass}); that choice is kept per server in server_bypasses.json, never in shared configs or profiles.
 */
public final class HypixelSafetyManager {
    /** Why a module is off on this server. */
    public enum Block { NONE, BANNED, UNKNOWN, SERVER }

    /** The 1.7 Animations option that sends a use packet while digging (OldAnimationsModule.useWhileMining): gated like a risky module. */
    public static final String USE_WHILE_MINING = "UseWhileMining";
    /** The TNT Timer option that renders the timer through blocks (ESP-like): gated like a risky module. */
    public static final String TNT_THROUGH_BLOCKS = "TntThroughBlocks";

    private static final Set<String> DISALLOWED_MODULES = new HashSet<>();

    static {
        // Hypixel officially disallows:
        // - Minimaps with player radar, mob radar, or cave mapping (Xaero/VoxelMap).
        // - Automated reconnections bypassing AFK/lobby kicks (AutoReconnect).
        // - Automated inventory manipulation & fast packet bursts (MouseTweaks).
        // - Combat hit distance / reach measurement HUDs (ReachDisplay).
        // - Tile entity, container, and NBT data inspection HUDs (Jade).
        // - In-world container item previews and ESP-like badges (ShulkerBoxUtils).
        DISALLOWED_MODULES.add("Minimap");
        DISALLOWED_MODULES.add("XaeroMinimap");
        DISALLOWED_MODULES.add("XaeroWorldMap");
        DISALLOWED_MODULES.add("AutoReconnect");
        DISALLOWED_MODULES.add("MouseTweaks");
        DISALLOWED_MODULES.add("ReachDisplay");
        DISALLOWED_MODULES.add("Jade");
        DISALLOWED_MODULES.add("ShulkerBoxUtils");
    }
    /** The same names for the per-frame check: an array loop allocates no iterator. */
    private static final String[] DISALLOWED = DISALLOWED_MODULES.toArray(new String[0]);

    /** QA only (the Hypixel QA routines set it for their run): the singleplayer QA world counts as Hypixel. */
    public static volatile boolean qaOverride;
    /** QA only (UiCapture's server-rules steps): the singleplayer QA world counts as this server address. */
    public static volatile String qaAddress;

    /**
     * Big PvP and minigame networks whose rules ban minimaps or their player/mob radar (Pika's rules say so outright), each with its
     * subdomains. Their anticheats also ban hidden inventory clicks (Stack Refill's AutoRefill pattern got a Minemen.club ban).
     */
    private static final String[] MINIMAP_BANNED_SERVERS = {"hypixel.net", "hypixel.io", "minemen.club", "pvp.land", "lunar.gg", "stray.gg",
        "cubecraft.net", "mccisland.net", "hoplite.gg", "pika-network.net", "jartexnetwork.com", "blocksmc.com", "mineplex.com", "gommehd.net"};
    /** Servers whose published rules allow minimaps (without radar: Xaero obeys their fair-play codes). */
    private static final String[] MINIMAP_ALLOWED_SERVERS = {};
    /**
     * Servers whose published rules explicitly allow Fullbright / Gamma adjustments:
     * - Hypixel: https://hypixel.net/rules/#mods (Brightness & Gamma Adjustment Modifications)
     * - CubeCraft: https://www.cubecraft.net/rules/ (Allowed Utility Mods - Gamma/Brightness mods)
     * - MCC Island: https://mccisland.net/community-rules (Approved Mods - Gamma/Brightness adjustments)
     * - PikaNetwork: https://pika-network.net/rules/ (Allowed Modifications - brightness adjustments / fullbright)
     */
    private static final String[] FULLBRIGHT_ALLOWED_SERVERS = {"hypixel.net", "hypixel.io", "cubecraft.net", "mccisland.net", "pika-network.net"};
    /**
     * Servers whose published rules explicitly ban Fullbright:
     * - GommeHD: https://www.gommehd.net/rules-minecraft (Prohibited Client Modifications - Automatic Potion Effects / Fullbright)
     */
    private static final String[] FULLBRIGHT_BANNED_SERVERS = {"gommehd.net"};
    /** Our own servers (Zero SMP, the arashyt.ca hosts) and 2b2t (anarchy: no rules): everything is allowed there. */
    private static final String[] TRUSTED_SERVERS = {"nikzerosmp.my.pebble.host", "arashyt.ca", "2b2t.org"};

    private static final int HYPIXEL = 1, NO_MINIMAP = 2, NO_AUTOMATION = 4, TRUSTED = 8, MINIMAP_OK = 16, FULLBRIGHT_OK = 32, NO_FULLBRIGHT = 64;
    private static final int QA_HYPIXEL = HYPIXEL | NO_MINIMAP | NO_AUTOMATION | FULLBRIGHT_OK;

    /** What an address resolved to: its flags and the host bypasses are kept under. */
    private record Server(int flags, String host) {}
    private static final Server LOCAL = new Server(TRUSTED, "");

    private static final Map<String, Server> CACHED_ADDRESSES = new ConcurrentHashMap<>();
    private static volatile String lastAddress;
    private static volatile Server lastServer = LOCAL;
    private static volatile String p2pSessionKey;
    private static volatile Server p2pServer;
    private static volatile String p2pProxyAddress;

    /**
     * A P2P guest session: while the game's connection address IS proxyAddress (the guest's loopback proxy, "127.0.0.1:port") it is an
     * unknown server under key. Any other address is classified as usual, so a key left over by a missed cleanup can't mask or grant.
     */
    public static void setP2PSession(String keyOrNull, String proxyAddress) {
        p2pSessionKey = keyOrNull;
        p2pProxyAddress = keyOrNull != null ? proxyAddress : null;
        p2pServer = keyOrNull != null ? new Server(0, keyOrNull) : null;
    }

    /** address is the P2P guest's loopback proxy (a friend's world through the tunnel, never a server to reconnect to). */
    public static boolean isP2PProxy(String address) {
        String proxy = p2pProxyAddress;
        return proxy != null && proxy.equals(address);
    }

    public static String getP2PSession() {
        return p2pSessionKey;
    }

    /** host -> bypassed module keys ({@link #bypassKey}). Loaded once, written on every change. */
    private static final Map<String, Set<String>> BYPASSES = new ConcurrentHashMap<>();
    private static volatile boolean bypassesLoaded;
    private static volatile File bypassFile; // null = ClientPaths base dir; tests point it at a temp file

    private HypixelSafetyManager() {}

    /**
     * Checks if the currently active game connection is on Hypixel.
     */
    public static boolean isHypixel() {
        return qaOverride || isHypixelAddress(LadsGameBridge.get().getServerAddress());
    }

    /**
     * Determines whether a given server IP/hostname belongs to Hypixel.
     */
    public static boolean isHypixelAddress(String addr) {
        return (server(addr).flags & HYPIXEL) != 0;
    }

    /** Xaero's minimap and world map are off here: banned, or this server's rules are unknown, and not bypassed. Asked every frame. */
    public static boolean isMinimapBanned() {
        return isDisallowed("Minimap");
    }

    /** This address is on a network whose rules ban minimaps. */
    public static boolean isMinimapBannedAddress(String addr) {
        return (server(addr).flags & NO_MINIMAP) != 0;
    }

    /** Asked per module every frame: the last address first, before any string work. */
    private static Server server(String addr) {
        Server p2p = p2pServer;
        if (p2p != null && addr != null && addr.equals(p2pProxyAddress)) return p2p;
        if (addr == null || addr.isEmpty()) return LOCAL;
        if (addr.equals(lastAddress)) return lastServer;
        Server server = CACHED_ADDRESSES.computeIfAbsent(addr, HypixelSafetyManager::computeServer);
        lastServer = server;
        lastAddress = addr;
        return server;
    }

    private static Server computeServer(String addr) {
        if (addr.equalsIgnoreCase("Singleplayer")) return LOCAL;
        String host = host(addr);
        if (host.isEmpty() || host.equals("unknown")) return new Server(0, "unknown"); // a server we can't name: unknown rules
        if (isLocalHost(host)) return new Server(TRUSTED, host);
        for (String own : TRUSTED_SERVERS) if (isHost(host, own)) return new Server(TRUSTED, host);
        int flags = isHost(host, "hypixel.net") || isHost(host, "hypixel.io") ? HYPIXEL : 0;
        for (String banned : MINIMAP_BANNED_SERVERS) if (isHost(host, banned)) flags |= (NO_MINIMAP | NO_AUTOMATION);
        for (String allowed : MINIMAP_ALLOWED_SERVERS) if (isHost(host, allowed)) flags |= MINIMAP_OK;
        for (String fbAllowed : FULLBRIGHT_ALLOWED_SERVERS) if (isHost(host, fbAllowed)) flags |= FULLBRIGHT_OK;
        for (String fbBanned : FULLBRIGHT_BANNED_SERVERS) if (isHost(host, fbBanned)) flags |= NO_FULLBRIGHT;
        return new Server(flags, host);
    }

    /** The lower-case host of an address, without port or trailing dots. */
    static String host(String addr) {
        String host = addr.trim().toLowerCase(Locale.ROOT);
        if (host.startsWith("[")) { // [::1]:25565
            int end = host.indexOf(']');
            return end == -1 ? host : host.substring(1, end);
        }
        int colon = host.indexOf(':');
        if (colon != -1 && host.indexOf(':', colon + 1) == -1) host = host.substring(0, colon); // one colon = a port; more = bare IPv6
        while (host.endsWith(".")) host = host.substring(0, host.length() - 1);
        return host;
    }

    /** This computer or the local network (an Open to LAN world, a server the player runs): no network anticheat to ban anyone. */
    private static boolean isLocalHost(String host) {
        if (host.equals("localhost") || host.endsWith(".localhost") || host.endsWith(".local") || host.equals("::1")) return true;
        String[] parts = host.split("\\.");
        if (parts.length != 4) return false;
        int[] ip = new int[4];
        for (int i = 0; i < 4; i++) {
            try { ip[i] = Integer.parseInt(parts[i]); } catch (NumberFormatException e) { return false; }
            if (ip[i] < 0 || ip[i] > 255) return false;
        }
        return ip[0] == 127 || ip[0] == 10 || ip[0] == 192 && ip[1] == 168 || ip[0] == 172 && ip[1] >= 16 && ip[1] <= 31;
    }

    /** {@code host} is {@code domain} or one of its subdomains (not merely ending in the same letters). */
    private static boolean isHost(String host, String domain) {
        return host.equals(domain) || host.endsWith(domain) && host.charAt(host.length() - domain.length() - 1) == '.';
    }

    /** Xaero's minimap and world map, as the Lads modules name them. */
    public static boolean isMinimapModule(String moduleName) {
        return "Minimap".equalsIgnoreCase(moduleName) || "XaeroMinimap".equalsIgnoreCase(moduleName) || "XaeroWorldMap".equalsIgnoreCase(moduleName);
    }

    /**
     * Checks if a specific module is disallowed on Hypixel.
     */
    public static boolean isDisallowedModule(String moduleName) {
        if (moduleName == null) return false;
        for (String disallowed : DISALLOWED) {
            if (disallowed.equalsIgnoreCase(moduleName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Risky on servers, so only on for singleplayer, LAN and trusted servers: Stack Refill clicks the inventory with no inventory open,
     * which anticheats ban as an AutoRefill cheat (a Minemen.club ban); use-while-mining sends a use packet mid-dig; Hide Foliage's
     * Tall Plants show a player crouching in tall grass. Asked per module every frame: no allocation.
     */
    public static boolean isSingleplayerOnlyModule(String moduleName) {
        return "StackRefill".equalsIgnoreCase(moduleName) || USE_WHILE_MINING.equalsIgnoreCase(moduleName) || HideFoliage.TALL_GATE.equalsIgnoreCase(moduleName);
    }

    public static boolean isFullbrightModule(String moduleName) {
        return "Fullbright".equalsIgnoreCase(moduleName);
    }

    public static boolean isTntThroughBlocks(String moduleName) {
        return TNT_THROUGH_BLOCKS.equalsIgnoreCase(moduleName)
            || "TntTimerThroughBlocks".equalsIgnoreCase(moduleName)
            || "TNT Timer Through blocks".equalsIgnoreCase(moduleName)
            || "Through blocks".equalsIgnoreCase(moduleName);
    }

    /**
     * Risky module set under the Anti-Ban rule: modules that send unusual packets, automate clicks/actions,
     * or expose unvanilla information (AutoReconnect, MouseTweaks, ReachDisplay, Jade, ShulkerBoxUtils,
     * Fullbright, TNT timer Through-blocks, Stack Refill, use-while-mining).
     * These are default OFF on unknown servers and banned servers; allowed in singleplayer, LAN, and trusted servers
     * (or servers whose published rules explicitly allow them, like Fullbright on Hypixel/CubeCraft/MCC Island/Pika).
     * Allocation-free: asked per module every frame.
     */
    public static boolean isRiskyModule(String moduleName) {
        if (moduleName == null) return false;
        return isMinimapModule(moduleName)
            || com.thelads.core.client.hud.HudExtras.BLOCK_INFO.equalsIgnoreCase(moduleName)
            || isSingleplayerOnlyModule(moduleName)
            || isFullbrightModule(moduleName)
            || isTntThroughBlocks(moduleName)
            || "AutoReconnect".equalsIgnoreCase(moduleName)
            || "MouseTweaks".equalsIgnoreCase(moduleName)
            || "ReachDisplay".equalsIgnoreCase(moduleName)
            || "Jade".equalsIgnoreCase(moduleName)
            || "ShulkerBoxUtils".equalsIgnoreCase(moduleName)
            || "Freelook".equalsIgnoreCase(moduleName) || "FogClearView".equalsIgnoreCase(moduleName)
            || com.thelads.core.modules.HitboxesModule.THROUGH_WALLS.equalsIgnoreCase(moduleName) // FreelookModule, FogCustomizerModule.CLEAR_VIEW
            || PvpVisuals.NAMETAG_HEALTH.equalsIgnoreCase(moduleName)
            || "Light Overlay".equalsIgnoreCase(moduleName) || "Loot Beams".equalsIgnoreCase(moduleName) // pvp rows 32-36: info vanilla does not show
            || "Mob Overlay".equalsIgnoreCase(moduleName) || "Mob Size".equalsIgnoreCase(moduleName)
            // parity-1023 chat lane: commands past vanilla's length, keys that send chat, walking and slot clicks with a screen open
            || ChatTweaks.LONG_COMMANDS.equalsIgnoreCase(moduleName) || com.thelads.core.modules.AutoTextModule.NAME.equalsIgnoreCase(moduleName)
            || InventoryTweaks.MOVE.equalsIgnoreCase(moduleName) || InventoryTweaks.ARMOR_SWAP.equalsIgnoreCase(moduleName)
            || BreedingTimer.NAME.equalsIgnoreCase(moduleName) || EnderChestPeek.NAME.equalsIgnoreCase(moduleName) // hidden cooldown, chest away from it
            || isGatedHud(moduleName);
    }

    /** HUD widgets that show what vanilla does not: other players' exact health, a horse's hidden stats, a nearby-player radar, totem pops. */
    private static boolean isGatedHud(String moduleName) {
        return com.thelads.core.client.hud.HudExtras.DAMAGE_INDICATOR.equalsIgnoreCase(moduleName)
            || com.thelads.core.client.hud.HudExtras.HORSE.equalsIgnoreCase(moduleName)
            || com.thelads.core.client.hud.HudExtras.PLAYERS.equalsIgnoreCase(moduleName)
            || com.thelads.core.client.hud.HudExtras.TOTEMS.equalsIgnoreCase(moduleName);
    }

    public static boolean isGatedHypixelModule(String moduleName) {
        return "Auto GL".equalsIgnoreCase(moduleName) || "AutoGL".equalsIgnoreCase(moduleName)
            || "Opened Chest Highlight".equalsIgnoreCase(moduleName);
    }

    /** Why this module is off on the current server, bypass or not. Allocation-free: asked per module every frame. */
    public static Block blockOf(String moduleName) {
        if (moduleName == null) return Block.NONE;
        com.thelads.core.client.ladsapi.ServerPolicy policy = com.thelads.core.client.ladsapi.ServerPolicy.active();
        if (policy != null) {
            com.thelads.core.client.ladsapi.ModuleState state = policy.module(moduleName);
            if (state == com.thelads.core.client.ladsapi.ModuleState.BLOCK) return Block.SERVER;
            if (state == com.thelads.core.client.ladsapi.ModuleState.ALLOW) return Block.NONE;
        }
        LadsGameBridge game = LadsGameBridge.get();
        if (!qaOverride && qaAddress == null && !game.isIngame()) return Block.NONE; // menus: nothing to ban
        int flags = qaOverride ? QA_HYPIXEL : server(address(game)).flags; // the last address first: no string work
        if ((flags & TRUSTED) != 0) return Block.NONE;
        if ((flags & HYPIXEL) != 0 && isDisallowedModule(moduleName)) return Block.BANNED;
        if (isFullbrightModule(moduleName)) {
            if ((flags & FULLBRIGHT_OK) != 0) return Block.NONE;
            if ((flags & NO_FULLBRIGHT) != 0 || (flags & NO_AUTOMATION) != 0) return Block.BANNED;
            return Block.UNKNOWN;
        }
        boolean minimap = isMinimapModule(moduleName);
        boolean risky = isRiskyModule(moduleName) || ((flags & HYPIXEL) == 0 && isGatedHypixelModule(moduleName));
        if (!risky) return Block.NONE;
        if (minimap) return (flags & NO_MINIMAP) != 0 ? Block.BANNED : (flags & MINIMAP_OK) != 0 ? Block.NONE : Block.UNKNOWN;
        return (flags & NO_AUTOMATION) != 0 ? Block.BANNED : Block.UNKNOWN;
    }

    /**
     * The module is off on this server right now: blocked by its rules (or unknown rules) and not bypassed by the player.
     */
    public static boolean isDisallowed(String moduleName) {
        Block block = blockOf(moduleName);
        if (block == Block.NONE) return false;
        if (block == Block.SERVER) return true; // policy BLOCK, NOT bypassable!
        return !isBypassed(moduleName);
    }

    /** Singleplayer, LAN, or one of our own servers (not in the Hypixel QA). */
    public static boolean isTrustedServer() {
        return !qaOverride && (server(address(LadsGameBridge.get())).flags & TRUSTED) != 0;
    }

    /** Any server, LAN or Realms (anything but a local world); "Unknown" counts as a server, so a missed case stays off. */
    public static boolean isMultiplayer() {
        return qaOverride || !"Singleplayer".equalsIgnoreCase(LadsGameBridge.get().getServerAddress()); // the P2P proxy is an address too
    }

    /**
     * Returns the set of modules blocked on Hypixel.
     */
    public static Set<String> getDisallowedModules() {
        return Collections.unmodifiableSet(DISALLOWED_MODULES);
    }

    /** The host the current server's bypasses are kept under ("" for a local world). */
    public static String currentHost() {
        return server(address(LadsGameBridge.get())).host;
    }

    private static String address(LadsGameBridge game) {
        String qa = qaAddress;
        return qa != null ? qa : game.getServerAddress();
    }

    /** The minimap and world map share one bypass (one mod); everything else by its own name. */
    private static String bypassKey(String moduleName) {
        if (isMinimapModule(moduleName)) return "Minimap";
        if (isTntThroughBlocks(moduleName)) return TNT_THROUGH_BLOCKS;
        return moduleName;
    }

    /** The player turned this module on for the current server through the red warning. */
    public static boolean isBypassed(String moduleName) {
        if (!bypassesLoaded) loadBypasses();
        if (qaOverride || BYPASSES.isEmpty()) return false;
        Set<String> modules = BYPASSES.get(currentHost());
        return modules != null && modules.contains(bypassKey(moduleName));
    }

    /** At the player's own risk (only after the red warning): keeps the module on for the current server. */
    public static void bypass(String moduleName) {
        String host = currentHost();
        if (moduleName == null || host.isEmpty() || qaOverride) return;
        if (!bypassesLoaded) loadBypasses();
        BYPASSES.computeIfAbsent(host, h -> ConcurrentHashMap.newKeySet()).add(bypassKey(moduleName));
        saveBypasses();
    }

    /** Takes a bypass back: the server's rules apply again. */
    public static void removeBypass(String moduleName) {
        if (!bypassesLoaded) loadBypasses();
        Set<String> modules = BYPASSES.get(currentHost());
        if (modules != null && modules.remove(bypassKey(moduleName))) {
            if (modules.isEmpty()) BYPASSES.remove(currentHost());
            saveBypasses();
        }
    }

    /** Tests: keep bypasses in this file instead of the client folder (and forget the loaded ones). */
    public static synchronized void setBypassFile(File file) {
        bypassFile = file;
        BYPASSES.clear();
        bypassesLoaded = false;
    }

    private static File bypassFile() {
        File file = bypassFile;
        return file != null ? file : com.thelads.core.client.util.ClientPaths.getBaseDir().resolve("server_bypasses.json").toFile();
    }

    private static synchronized void loadBypasses() {
        if (bypassesLoaded) return;
        try {
            File file = bypassFile();
            if (file.isFile()) {
                JsonObject json = new Gson().fromJson(Files.readString(file.toPath(), StandardCharsets.UTF_8), JsonObject.class);
                if (json != null) for (Map.Entry<String, JsonElement> e : json.entrySet()) {
                    if (!e.getValue().isJsonArray()) continue;
                    Set<String> modules = ConcurrentHashMap.newKeySet();
                    for (JsonElement m : e.getValue().getAsJsonArray()) modules.add(m.getAsString());
                    if (!modules.isEmpty()) BYPASSES.put(e.getKey(), modules);
                }
            }
        } catch (Exception e) {
            System.err.println("[TheLadsCore] server_bypasses.json unreadable, no bypasses: " + e);
        }
        bypassesLoaded = true;
    }

    private static synchronized void saveBypasses() {
        JsonObject json = new JsonObject();
        BYPASSES.forEach((host, modules) -> {
            JsonArray list = new JsonArray();
            modules.stream().sorted().forEach(list::add);
            json.add(host, list);
        });
        try {
            File file = bypassFile();
            if (file.getParentFile() != null) file.getParentFile().mkdirs();
            Files.writeString(file.toPath(), json.toString(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            System.err.println("[TheLadsCore] could not save server_bypasses.json: " + e);
        }
    }

    /** One line on why the module is off here (the settings notice and the warning say more). */
    public static String getBlockedNotice(String moduleName) {
        Block block = blockOf(moduleName);
        if (block == Block.SERVER) {
            com.thelads.core.client.ladsapi.ServerPolicy p = com.thelads.core.client.ladsapi.ServerPolicy.active();
            String server = (p != null && !p.serverName().isEmpty()) ? p.serverName() : "this server";
            return moduleName + " is blocked by " + server + ".";
        }
        String name = isMinimapModule(moduleName) ? "The minimap" : isTntThroughBlocks(moduleName) ? "TNT Through Blocks" : moduleName;
        return block == Block.UNKNOWN
            ? name + " is off on this server: we don't know its rules and its anticheat could ban you."
            : name + " is off on this server: its rules ban it.";
    }
}
