package com.thelads.core.client.media;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * TeamSpeak 3 ClientQuery Bridge (Lunar / Badlion TeamSpeak HUD):
 * Connects to TeamSpeak 3 local client query on 127.0.0.1:25639 to monitor channel
 * presence and live voice activity (who is currently talking).
 * Client-only, visual only, not gated.
 */
public final class TeamSpeakBridge {
    public static final int DEFAULT_PORT = 25639;
    public static final String HOST = "127.0.0.1";

    public record TsClient(int clid, String nickname, boolean talking) {}

    private static final AtomicBoolean RUNNING = new AtomicBoolean(false);
    private static volatile boolean connected = false;
    private static volatile String currentChannel = "Default Channel";
    private static final Map<Integer, TsClient> CLIENTS = new ConcurrentHashMap<>();
    private static Thread workerThread;

    private TeamSpeakBridge() {}

    public static boolean isConnected() {
        return connected;
    }

    public static String getCurrentChannel() {
        return currentChannel;
    }

    public static List<TsClient> getClients() {
        if (!connected) return Collections.emptyList();
        return new ArrayList<>(CLIENTS.values());
    }

    public static synchronized void start() {
        if (RUNNING.getAndSet(true)) return;

        workerThread = new Thread(TeamSpeakBridge::runLoop, "Lads-TeamSpeakBridge");
        workerThread.setDaemon(true);
        workerThread.start();
    }

    public static synchronized void stop() {
        RUNNING.set(false);
        if (workerThread != null) {
            workerThread.interrupt();
            workerThread = null;
        }
        connected = false;
        CLIENTS.clear();
    }

    private static void runLoop() {
        while (RUNNING.get()) {
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(HOST, DEFAULT_PORT), 3000);
                socket.setSoTimeout(10000);
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                     PrintWriter writer = new PrintWriter(socket.getOutputStream(), true, StandardCharsets.UTF_8)) {

                    connected = true;
                    // Register for events and query client status
                    writer.println("clientnotifyregister schandlerid=0 event=any");
                    writer.println("whoami");
                    writer.println("channelclientlist");

                    String line;
                    while (RUNNING.get() && (line = reader.readLine()) != null) {
                        parseLine(line);
                    }
                }
            } catch (Throwable t) {
                connected = false;
                CLIENTS.clear();
            }

            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                break;
            }
        }
        connected = false;
    }

    /** Parses lines received from TeamSpeak ClientQuery. Package-private for unit testing. */
    public static void parseLine(String line) {
        if (line == null || line.isEmpty()) return;

        if (line.startsWith("notifytalkstatuschange")) {
            // notifytalkstatuschange status=1 is_received_whisper=0 clid=123
            int status = parseFieldInt(line, "status");
            int clid = parseFieldInt(line, "clid");
            if (clid != -1) {
                TsClient existing = CLIENTS.get(clid);
                if (existing != null) {
                    CLIENTS.put(clid, new TsClient(clid, existing.nickname(), status == 1));
                }
            }
        } else if (line.contains("client_nickname=") && line.contains("clid=")) {
            // channelclientlist response: clid=1 client_nickname=User ...
            String[] parts = line.split("\\|");
            for (String part : parts) {
                int clid = parseFieldInt(part, "clid");
                String nick = parseFieldString(part, "client_nickname");
                if (clid != -1 && nick != null) {
                    TsClient prev = CLIENTS.get(clid);
                    boolean isTalking = prev != null && prev.talking();
                    CLIENTS.put(clid, new TsClient(clid, unescapeTs(nick), isTalking));
                }
            }
        } else if (line.startsWith("notifyclientleftview")) {
            int clid = parseFieldInt(line, "clid");
            if (clid != -1) CLIENTS.remove(clid);
        } else if (line.startsWith("notifyclientmoved")) {
            // If moved, refresh clients
            int clid = parseFieldInt(line, "clid");
            int ctid = parseFieldInt(line, "ctid");
            if (clid != -1 && ctid != -1) {
                // Client moved
            }
        } else if (line.contains("channel_name=")) {
            String ch = parseFieldString(line, "channel_name");
            if (ch != null && !ch.isEmpty()) {
                currentChannel = unescapeTs(ch);
            }
        }
    }

    public static int parseFieldInt(String line, String key) {
        String prefix = key + "=";
        int idx = line.indexOf(prefix);
        if (idx == -1) return -1;
        int start = idx + prefix.length();
        int end = line.indexOf(' ', start);
        if (end == -1) end = line.length();
        try {
            return Integer.parseInt(line.substring(start, end).trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    public static String parseFieldString(String line, String key) {
        String prefix = key + "=";
        int idx = line.indexOf(prefix);
        if (idx == -1) return null;
        int start = idx + prefix.length();
        int end = line.indexOf(' ', start);
        if (end == -1) end = line.length();
        return line.substring(start, end).trim();
    }

    private static String unescapeTs(String input) {
        if (input == null) return "";
        return input.replace("\\s", " ")
                    .replace("\\p", "|")
                    .replace("\\/", "/")
                    .replace("\\\\", "\\");
    }
}
