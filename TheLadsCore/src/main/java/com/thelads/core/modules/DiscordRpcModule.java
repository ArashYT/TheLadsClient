package com.thelads.core.modules;

import com.thelads.core.config.Module;
import com.thelads.core.config.BoolOption;
import com.thelads.core.config.Option;
import club.minnced.discord.rpc.DiscordEventHandlers;
import club.minnced.discord.rpc.DiscordRPC;
import club.minnced.discord.rpc.DiscordRichPresence;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

public class DiscordRpcModule extends Module {

    private static final String DEFAULT_CLIENT_ID = "1381291760984940618";
    private static boolean rpcInitialized = false;
    private static String loadedClientId = null;

    // Loaded lazily: DiscordRPC.INSTANCE triggers a JNA native load that crashes
    // the game at startup when discord-rpc.dll can't be found (e.g. production
    // launcher, where JNA only sees the vanilla library classpath).
    private static DiscordRPC lib = null;
    private static boolean nativeLoadAttempted = false;

    private long lastUpdate = 0;
    private long startTimestamp = 0;

    // Minecraft version (resolved at init time)
    private static String mcVersion = null;

    public DiscordRpcModule() {
        super("DiscordRPC", "Show your game status in Discord.");
        addOption(new BoolOption("Show MC Version", true));
        addOption(new BoolOption("Show Server Name", true));
        addOption(new BoolOption("Show Server IP", false));   // off by default for privacy
        addOption(new BoolOption("Show Elapsed Time", true));
        ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
        loadClientId();
        resolveVersion();
    }

    private boolean opt(String name, boolean def) {
        Option o = getOption(name);
        return (o instanceof BoolOption b) ? b.get() : def;
    }

    // ── Native loading ───────────────────────────────────────────────────────

    private static DiscordRPC getLib() {
        if (!nativeLoadAttempted) {
            nativeLoadAttempted = true;
            try {
                extractNativeLibrary();
                lib = DiscordRPC.INSTANCE;
            } catch (Throwable t) {
                System.err.println("[LadsRPC] Discord RPC natives unavailable, module disabled: " + t);
            }
        }
        return lib;
    }

    /**
     * The discord-rpc native is bundled inside the jar-in-jar java-discord-rpc
     * dependency, which JNA cannot see (it scans the system classpath only).
     * Extract it next to the config dir and point jna.library.path at it.
     */
    private static void extractNativeLibrary() throws IOException {
        String resource = resourcePathForPlatform();
        if (resource == null) return;
        try (InputStream in = DiscordRpcModule.class.getResourceAsStream(resource)) {
            if (in == null) return; // not bundled; let JNA try its own lookup
            Path dir = FabricLoader.getInstance().getGameDir().resolve("natives").resolve("discord-rpc");
            Files.createDirectories(dir);
            Path target = dir.resolve(resource.substring(resource.lastIndexOf('/') + 1));
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            
            try {
                com.sun.jna.NativeLibrary.addSearchPath("discord-rpc", dir.toString());
            } catch (Throwable t) {
                String existing = System.getProperty("jna.library.path");
                System.setProperty("jna.library.path",
                    existing == null || existing.isEmpty() ? dir.toString() : existing + File.pathSeparator + dir);
            }
        }
    }

    private static String resourcePathForPlatform() {
        String os = System.getProperty("os.name", "").toLowerCase();
        String arch = System.getProperty("os.arch", "").toLowerCase();
        boolean x64 = arch.contains("64") && !arch.contains("aarch") && !arch.contains("arm");
        if (os.contains("win")) return x64 ? "/win32-x86-64/discord-rpc.dll" : "/win32-x86/discord-rpc.dll";
        if (os.contains("mac")) return "/darwin/libdiscord-rpc.dylib";
        if (os.contains("linux")) return x64 ? "/linux-x86-64/libdiscord-rpc.so" : null;
        return null;
    }

    // ── Setup ────────────────────────────────────────────────────────────────

    private void loadClientId() {
        if (loadedClientId != null) return;
        Path cfg = Paths.get("config", "thelads_discord_rpc.txt");
        try {
            if (!Files.exists(cfg)) {
                Files.createDirectories(cfg.getParent());
                Files.writeString(cfg, DEFAULT_CLIENT_ID + "\n");
            }
            String content = Files.readString(cfg).strip();
            if (!content.isEmpty() && !content.equals("YOUR_DISCORD_CLIENT_ID_HERE")) {
                loadedClientId = content;
            } else {
                loadedClientId = DEFAULT_CLIENT_ID;
            }
        } catch (IOException e) {
            loadedClientId = DEFAULT_CLIENT_ID;
        }
    }

    private void resolveVersion() {
        if (mcVersion != null) return;
        try {
            mcVersion = FabricLoader.getInstance()
                .getModContainer("minecraft")
                .map(c -> c.getMetadata().getVersion().getFriendlyString())
                .orElse("Unknown");
        } catch (Exception e) {
            mcVersion = "26.1.2";
        }
    }

    // ── Lifecycle ────────────────────────────────────────────────────────────

    @Override
    public void onEnable() {
        DiscordRPC rpc = getLib();
        if (rpc == null) return;
        if (!rpcInitialized && loadedClientId != null) {
            DiscordEventHandlers handlers = new DiscordEventHandlers();
            handlers.ready = user -> System.out.println("[LadsRPC] Connected as " + user.username);
            rpc.Discord_Initialize(loadedClientId, handlers, true, null);
            rpcInitialized = true;
        }
        startTimestamp = System.currentTimeMillis() / 1000L;
        lastUpdate = 0;
    }

    @Override
    public void onDisable() {
        if (rpcInitialized && lib != null) {
            lib.Discord_ClearPresence();
        }
    }

    // ── Tick ─────────────────────────────────────────────────────────────────

    private void onTick(Minecraft mc) {
        if (!rpcInitialized || lib == null) return;
        lib.Discord_RunCallbacks();
        if (!isEnabled()) return;

        long now = System.currentTimeMillis();
        if (now - lastUpdate < 3000) return;
        lastUpdate = now;

        boolean showVer = opt("Show MC Version", true);
        boolean showName = opt("Show Server Name", true);
        boolean showIp = opt("Show Server IP", false);
        String verPart = showVer ? "MC " + mcVersion : "";

        DiscordRichPresence p = new DiscordRichPresence();
        p.startTimestamp = opt("Show Elapsed Time", true) ? startTimestamp : 0;
        p.largeImageKey  = "lads";          // upload "lads" asset in Discord dev portal
        p.largeImageText = "The Lads Client";
        p.smallImageKey  = "minecraft";
        p.smallImageText = showVer ? "MC " + mcVersion : "The Lads Client";

        if (mc.level == null) {
            p.state   = "Main Menu";
            p.details = verPart;
        } else if (mc.isLocalServer()) {
            p.state   = "Singleplayer";
            p.details = verPart;
        } else if (mc.getCurrentServer() != null) {
            String serverName = mc.getCurrentServer().name;
            String serverIp   = mc.getCurrentServer().ip;
            p.state   = (showName && serverName != null && !serverName.isBlank()) ? serverName
                      : (showIp ? serverIp : "Multiplayer");
            p.details = showIp ? join(verPart, serverIp) : verPart;
        } else {
            p.state   = "In Game";
            p.details = verPart;
        }
        if (p.details == null || p.details.isBlank()) p.details = "Playing";

        lib.Discord_UpdatePresence(p);
    }

    private static String join(String a, String b) {
        if (a == null || a.isBlank()) return b;
        if (b == null || b.isBlank()) return a;
        return a + " · " + b;
    }
}
