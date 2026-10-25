package com.thelads.core.client.media;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
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
 * Protocol: TeamSpeak 3 ClientQuery.
 * Client-only, visual only, not gated.
 */
public final class TeamSpeakBridge {
    public static final int DEFAULT_PORT = 25639;
    public static final String HOST = "127.0.0.1";
    public static final String OPTION_API_KEY = "ClientQuery API Key";
    private static volatile String apiKey = "";

    public record TsClient(int clid, String nickname, boolean talking) {}

    private static final AtomicBoolean RUNNING = new AtomicBoolean(false);
    private static volatile boolean connected = false;
    private static volatile String currentChannel = "Default Channel";
    private static volatile int myClid = -1;
    private static volatile int myCid = -1;
    private static volatile PrintWriter activeWriter = null;
    private static final Map<Integer, TsClient> CLIENTS = new ConcurrentHashMap<>();
    private static Thread workerThread;

    private TeamSpeakBridge() {}

    public static String getApiKey() {
        if (apiKey != null && !apiKey.trim().isEmpty()) {
            return apiKey.trim();
        }
        try {
            com.thelads.core.config.Module m = com.thelads.core.config.ModuleManager.getInstance().getModule("TeamSpeak");
            if (m != null) {
                com.thelads.core.config.Option opt = m.getOption(OPTION_API_KEY);
                if (opt instanceof com.thelads.core.config.TextOption textOpt) {
                    return textOpt.getValue();
                } else if (opt instanceof com.thelads.core.config.StringOption strOpt) {
                    return strOpt.get();
                }
            }
        } catch (Throwable ignored) {}
        return "";
    }

    public static void setApiKey(String key) {
        apiKey = key != null ? key : "";
    }

    public static boolean isConnected() {
        return connected;
    }

    public static String getCurrentChannel() {
        return currentChannel;
    }

    public static int getMyClid() {
        return myClid;
    }

    public static int getMyCid() {
        return myCid;
    }

    public static List<TsClient> getClients() {
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
        activeWriter = null;
        connected = false;
        myClid = -1;
        myCid = -1;
        CLIENTS.clear();
    }

    private static void runLoop() {
        while (RUNNING.get()) {
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(HOST, DEFAULT_PORT), 3000);
                socket.setSoTimeout(10000);
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                     PrintWriter writer = new PrintWriter(socket.getOutputStream(), true, StandardCharsets.UTF_8)) {

                    activeWriter = writer;
                    connected = true;
                    String key = getApiKey();
                    if (key != null && !key.trim().isEmpty()) {
                        // TeamSpeak 3 ClientQuery command: auth apikey=<key>
                        writer.println("auth apikey=" + key.trim());
                    }
                    // Register for events and query client identity
                    writer.println("clientnotifyregister schandlerid=0 event=any");
                    writer.println("whoami");

                    while (RUNNING.get()) {
                        String line;
                        try {
                            line = reader.readLine();
                        } catch (SocketTimeoutException e) {
                            // Keep connection alive across idle intervals
                            continue;
                        }
                        if (line == null) {
                            break; // Connection closed by host
                        }
                        parseLine(line);
                    }
                }
            } catch (Throwable t) {
                connected = false;
                CLIENTS.clear();
            } finally {
                activeWriter = null;
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

        if (line.contains("clid=") && line.contains("cid=") && !line.startsWith("notify")) {
            // whoami reply: clid=123 cid=456 ...
            int clid = parseFieldInt(line, "clid");
            int cid = parseFieldInt(line, "cid");
            if (clid != -1 && cid != -1) {
                myClid = clid;
                myCid = cid;
                if (activeWriter != null) {
                    activeWriter.println("channelclientlist cid=" + cid + " -voice");
                    activeWriter.println("channelinfo cid=" + cid);
                }
            }
        } else if (line.startsWith("notifytalkstatuschange")) {
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
                    int talking = parseFieldInt(part, "client_flag_talking");
                    TsClient prev = CLIENTS.get(clid);
                    boolean isTalking = talking == 1 || (talking == -1 && prev != null && prev.talking());
                    CLIENTS.put(clid, new TsClient(clid, unescapeTs(nick), isTalking));
                }
            }
        } else if (line.startsWith("notifyclientleftview")) {
            int clid = parseFieldInt(line, "clid");
            if (clid != -1) CLIENTS.remove(clid);
        } else if (line.startsWith("notifyclientmoved")) {
            int clid = parseFieldInt(line, "clid");
            int ctid = parseFieldInt(line, "ctid");
            if (clid != -1 && ctid != -1) {
                if (clid == myClid) {
                    // Local client moved channel
                    myCid = ctid;
                    CLIENTS.clear();
                    if (activeWriter != null) {
                        activeWriter.println("channelclientlist cid=" + ctid + " -voice");
                        activeWriter.println("channelinfo cid=" + ctid);
                    }
                } else if (ctid != myCid) {
                    // Another client moved out of our channel
                    CLIENTS.remove(clid);
                } else {
                    // Another client moved into our channel
                    if (activeWriter != null && myCid != -1) {
                        activeWriter.println("channelclientlist cid=" + myCid + " -voice");
                    }
                }
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
