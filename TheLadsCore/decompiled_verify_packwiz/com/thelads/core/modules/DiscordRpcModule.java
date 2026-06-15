/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  club.minnced.discord.rpc.DiscordEventHandlers
 *  club.minnced.discord.rpc.DiscordRPC
 *  club.minnced.discord.rpc.DiscordRichPresence
 *  net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
 *  net.fabricmc.loader.api.FabricLoader
 *  net.minecraft.client.Minecraft
 */
package com.thelads.core.modules;

import club.minnced.discord.rpc.DiscordEventHandlers;
import club.minnced.discord.rpc.DiscordRPC;
import club.minnced.discord.rpc.DiscordRichPresence;
import com.thelads.core.config.Module;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileAttribute;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

public class DiscordRpcModule
extends Module {
    private static final String DEFAULT_CLIENT_ID = "1381291760984940618";
    private static boolean rpcInitialized = false;
    private static String loadedClientId = null;
    private static DiscordRPC lib = null;
    private static boolean nativeLoadAttempted = false;
    private long lastUpdate = 0L;
    private long startTimestamp = 0L;
    private static String mcVersion = null;

    public DiscordRpcModule() {
        super("DiscordRPC", "Show your game status in Discord.");
        ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
        this.loadClientId();
        this.resolveVersion();
    }

    private static DiscordRPC getLib() {
        if (!nativeLoadAttempted) {
            nativeLoadAttempted = true;
            try {
                DiscordRpcModule.extractNativeLibrary();
                lib = DiscordRPC.INSTANCE;
            }
            catch (Throwable t) {
                System.err.println("[LadsRPC] Discord RPC natives unavailable, module disabled: " + String.valueOf(t));
            }
        }
        return lib;
    }

    private static void extractNativeLibrary() throws IOException {
        String resource = DiscordRpcModule.resourcePathForPlatform();
        if (resource == null) {
            return;
        }
        try (InputStream in = DiscordRpcModule.class.getResourceAsStream(resource);){
            if (in == null) {
                return;
            }
            Path dir = FabricLoader.getInstance().getGameDir().resolve("natives").resolve("discord-rpc");
            Files.createDirectories(dir, new FileAttribute[0]);
            Path target = dir.resolve(resource.substring(resource.lastIndexOf(47) + 1));
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            String existing = System.getProperty("jna.library.path");
            System.setProperty("jna.library.path", (String)(existing == null || existing.isEmpty() ? dir.toString() : existing + File.pathSeparator + String.valueOf(dir)));
        }
    }

    private static String resourcePathForPlatform() {
        boolean x64;
        String os = System.getProperty("os.name", "").toLowerCase();
        String arch = System.getProperty("os.arch", "").toLowerCase();
        boolean bl = x64 = arch.contains("64") && !arch.contains("aarch") && !arch.contains("arm");
        if (os.contains("win")) {
            return x64 ? "/win32-x86-64/discord-rpc.dll" : "/win32-x86/discord-rpc.dll";
        }
        if (os.contains("mac")) {
            return "/darwin/libdiscord-rpc.dylib";
        }
        if (os.contains("linux")) {
            return x64 ? "/linux-x86-64/libdiscord-rpc.so" : null;
        }
        return null;
    }

    private void loadClientId() {
        if (loadedClientId != null) {
            return;
        }
        Path cfg = Paths.get("config", "thelads_discord_rpc.txt");
        try {
            String content;
            if (!Files.exists(cfg, new LinkOption[0])) {
                Files.createDirectories(cfg.getParent(), new FileAttribute[0]);
                Files.writeString(cfg, (CharSequence)"1381291760984940618\n", new OpenOption[0]);
            }
            loadedClientId = !(content = Files.readString(cfg).strip()).isEmpty() && !content.equals("YOUR_DISCORD_CLIENT_ID_HERE") ? content : DEFAULT_CLIENT_ID;
        }
        catch (IOException e) {
            loadedClientId = DEFAULT_CLIENT_ID;
        }
    }

    private void resolveVersion() {
        if (mcVersion != null) {
            return;
        }
        try {
            mcVersion = FabricLoader.getInstance().getModContainer("minecraft").map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("Unknown");
        }
        catch (Exception e) {
            mcVersion = "26.1.2";
        }
    }

    @Override
    public void onEnable() {
        DiscordRPC rpc = DiscordRpcModule.getLib();
        if (rpc == null) {
            return;
        }
        if (!rpcInitialized && loadedClientId != null) {
            DiscordEventHandlers handlers = new DiscordEventHandlers();
            handlers.ready = user -> System.out.println("[LadsRPC] Connected as " + user.username);
            rpc.Discord_Initialize(loadedClientId, handlers, true, null);
            rpcInitialized = true;
        }
        this.startTimestamp = System.currentTimeMillis() / 1000L;
        this.lastUpdate = 0L;
    }

    @Override
    public void onDisable() {
        if (rpcInitialized && lib != null) {
            lib.Discord_ClearPresence();
        }
    }

    private void onTick(Minecraft mc) {
        if (!rpcInitialized || lib == null) {
            return;
        }
        lib.Discord_RunCallbacks();
        if (!this.isEnabled()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - this.lastUpdate < 3000L) {
            return;
        }
        this.lastUpdate = now;
        DiscordRichPresence p = new DiscordRichPresence();
        p.startTimestamp = this.startTimestamp;
        p.largeImageKey = "lads";
        p.largeImageText = "The Lads Client";
        p.smallImageKey = "minecraft";
        p.smallImageText = "MC " + mcVersion;
        if (mc.level == null) {
            p.state = "Main Menu";
            p.details = "MC " + mcVersion;
        } else if (mc.isLocalServer()) {
            p.state = "Singleplayer";
            p.details = "MC " + mcVersion;
        } else if (mc.getCurrentServer() != null) {
            String serverName = mc.getCurrentServer().name;
            String serverIp = mc.getCurrentServer().ip;
            p.state = serverName != null && !serverName.isBlank() ? serverName : serverIp;
            p.details = "MC " + mcVersion + " \u00b7 " + serverIp;
        } else {
            p.state = "In Game";
            p.details = "MC " + mcVersion;
        }
        lib.Discord_UpdatePresence(p);
    }
}

