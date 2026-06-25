package com.thelads.core.modules;

import com.thelads.core.config.Module;
import com.thelads.core.config.BoolOption;
import com.thelads.core.config.Option;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

public class DiscordRpcModule extends Module {

    private static String mcVersion = null;
    private long lastUpdate = 0;
    private long startTimestamp = 0;

    // TCP client state
    private Socket socket = null;
    private PrintWriter writer = null;
    private boolean isConnecting = false;
    private long lastConnectAttempt = 0;

    public DiscordRpcModule() {
        super("DiscordRPC", "Show your game status in Discord.");
        addOption(new BoolOption("Show MC Version", true));
        addOption(new BoolOption("Show Server Name", true));
        addOption(new BoolOption("Show Server IP", false));   // off by default for privacy
        addOption(new BoolOption("Show Elapsed Time", true));
        ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
        resolveVersion();
    }

    private boolean opt(String name, boolean def) {
        Option o = getOption(name);
        return (o instanceof BoolOption b) ? b.get() : def;
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

    @Override
    public void onEnable() {
        startTimestamp = System.currentTimeMillis() / 1000L;
        lastUpdate = 0;
        lastConnectAttempt = 0;
        tryConnectAsync();
    }

    @Override
    public void onDisable() {
        disconnect();
    }

    private synchronized void disconnect() {
        if (writer != null) {
            try { writer.close(); } catch (Exception e) {}
            writer = null;
        }
        if (socket != null) {
            try { socket.close(); } catch (Exception e) {}
            socket = null;
        }
    }

    private synchronized boolean isConnected() {
        return socket != null && socket.isConnected() && !socket.isClosed();
    }

    private void tryConnectAsync() {
        synchronized (this) {
            if (isConnected() || isConnecting) {
                return;
            }
            long now = System.currentTimeMillis();
            if (now - lastConnectAttempt < 5000) {
                return; // wait 5 seconds between attempts
            }
            isConnecting = true;
            lastConnectAttempt = now;
        }

        Thread connectThread = new Thread(() -> {
            Socket tempSocket = null;
            PrintWriter tempWriter = null;
            try {
                tempSocket = new Socket();
                tempSocket.connect(new InetSocketAddress("127.0.0.1", 24442), 2000);
                tempWriter = new PrintWriter(new OutputStreamWriter(tempSocket.getOutputStream(), StandardCharsets.UTF_8), true);
                
                synchronized (this) {
                    socket = tempSocket;
                    writer = tempWriter;
                }
                System.out.println("[LadsRPC] Connected to Launcher Discord IPC server.");
            } catch (Exception e) {
                if (tempSocket != null) {
                    try { tempSocket.close(); } catch (Exception ex) {}
                }
            } finally {
                synchronized (this) {
                    isConnecting = false;
                }
            }
        }, "LadsRPC-Connect-Thread");
        connectThread.setDaemon(true);
        connectThread.start();
    }

    private void onTick(Minecraft mc) {
        if (!isEnabled()) {
            disconnect();
            return;
        }

        if (!isConnected()) {
            tryConnectAsync();
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastUpdate < 3000) return;
        lastUpdate = now;

        try {
            boolean singleplayer = mc.isLocalServer();
            String serverIp = "";
            if (!singleplayer && mc.getCurrentServer() != null) {
                serverIp = mc.getCurrentServer().ip;
                if (serverIp == null) serverIp = "";
            }

            long elapsed = opt("Show Elapsed Time", true) ? startTimestamp : 0;

            String json = String.format(
                "{\"singleplayer\":%b,\"serverIp\":\"%s\",\"version\":\"%s\",\"elapsedTime\":%d}",
                singleplayer,
                escapeJson(serverIp),
                escapeJson(mcVersion != null ? mcVersion : "Unknown"),
                elapsed
            );

            synchronized (this) {
                if (writer != null) {
                    writer.println(json);
                    if (writer.checkError()) {
                        System.err.println("[LadsRPC] Writer error detected. Disconnecting.");
                        disconnect();
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[LadsRPC] Error during presence update tick: " + e.getMessage());
            disconnect();
        }
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\b", "\\b")
                .replace("\f", "\\f")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
