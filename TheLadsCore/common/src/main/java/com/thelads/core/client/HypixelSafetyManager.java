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
    public enum Block { NONE, BANNED, UNKNOWN }

    /** The 1.7 Animations option that sends a use packet while digging (OldAnimationsModule.useWhileMining): gated like a risky module. */
    public static final String USE_WHILE_MINING = "UseWhileMining";

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
    /** Our own servers (Zero SMP, the arashyt.ca hosts) and 2b2t (anarchy: no rules): everything is allowed there. */
    private static final String[] TRUSTED_SERVERS = {"nikzerosmp.my.pebble.host", "arashyt.ca", "2b2t.org"};

    private static final int HYPIXEL = 1, NO_MINIMAP = 2, NO_AUTOMATION = 4, TRUSTED = 8, MINIMAP_OK = 16;
    private static final int QA_HYPIXEL = HYPIXEL | NO_MINIMAP | NO_AUTOMATION;

    /** What an address resolved to: its flags and the host bypasses are kept under. */
    private record Server(int flags, String host) {}
    private static final Server LOCAL = new Server(TRUSTED, "");

    private static final Map<String, Server> CACHED_ADDRESSES = new ConcurrentHashMap<>();
    private static volatile String lastAddress;
    private static volatile Server lastServer = LOCAL;

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
        for (String banned : MINIMAP_BANNED_SERVERS) if (isHost(host, banned)) return new Server(flags | NO_MINIMAP | NO_AUTOMATION, host);
        for (String allowed : MINIMAP_ALLOWED_SERVERS) if (isHost(host, allowed)) return new Server(flags | MINIMAP_OK, host);
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
     * which anticheats ban as an AutoRefill cheat (a Minemen.club ban); use-while-mining sends a use packet mid-dig.
     * Asked per module every frame: no allocation.
     */
    public static boolean isSingleplayerOnlyModule(String moduleName) {
        return "StackRefill".equalsIgnoreCase(moduleName) || USE_WHILE_MINING.equalsIgnoreCase(moduleName);
    }

    /** Why this module is off on the current server, bypass or not. Allocation-free: asked per module every frame. */
    public static Block blockOf(String moduleName) {
        if (moduleName == null) return Block.NONE;
        LadsGameBridge game = LadsGameBridge.get();
        int flags = qaOverride ? QA_HYPIXEL : server(address(game)).flags; // the last address first: no string work
        if ((flags & HYPIXEL) != 0 && isDisallowedModule(moduleName)) return Block.BANNED;
        boolean minimap = isMinimapModule(moduleName), risky = minimap || isSingleplayerOnlyModule(moduleName)
            || "Light Overlay".equalsIgnoreCase(moduleName) || "Loot Beams".equalsIgnoreCase(moduleName) || "Mob Overlay".equalsIgnoreCase(moduleName) || "Mob Size".equalsIgnoreCase(moduleName); // pvp rows 32-36
        if (!risky || (flags & TRUSTED) != 0 || !qaOverride && !game.isIngame()) return Block.NONE; // menus: nothing to ban
        if (minimap) return (flags & NO_MINIMAP) != 0 ? Block.BANNED : (flags & MINIMAP_OK) != 0 ? Block.NONE : Block.UNKNOWN;
        return (flags & NO_AUTOMATION) != 0 ? Block.BANNED : Block.UNKNOWN;
    }

    /**
     * The module is off on this server right now: blocked by its rules (or unknown rules) and not bypassed by the player.
     */
    public static boolean isDisallowed(String moduleName) {
        return blockOf(moduleName) != Block.NONE && !isBypassed(moduleName);
    }

    /** Singleplayer, LAN, or one of our own servers (not in the Hypixel QA). */
    public static boolean isTrustedServer() {
        return !qaOverride && (server(address(LadsGameBridge.get())).flags & TRUSTED) != 0;
    }

    /** Any server, LAN or Realms (anything but a local world); "Unknown" counts as a server, so a missed case stays off. */
    public static boolean isMultiplayer() {
        return qaOverride || !"Singleplayer".equalsIgnoreCase(LadsGameBridge.get().getServerAddress());
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
        return isMinimapModule(moduleName) ? "Minimap" : moduleName;
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
        String name = isMinimapModule(moduleName) ? "The minimap" : moduleName;
        return blockOf(moduleName) == Block.UNKNOWN
            ? name + " is off on this server: we don't know its rules and its anticheat could ban you."
            : name + " is off on this server: its rules ban it.";
    }
}
