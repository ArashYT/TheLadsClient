package com.thelads.core.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Whether The Lads Network is up, how many are on it, its latency and MOTD: a server-list ping of our own server on a background
 * thread, at most once a minute and only while something asks. Never blocks the caller; any failure simply reads as offline.
 */
public final class LadsNetworkStatus {
    private static final long INTERVAL_MS = 60_000L;
    private static final int TIMEOUT_MS = 4_000, PORT = 25565, MAX_REPLY = 1 << 20;
    private static final AtomicLong NEXT_PING = new AtomicLong();
    private static final AtomicBoolean IN_FLIGHT = new AtomicBoolean();
    private static volatile Snapshot cached = Snapshot.IDLE;
    private static volatile boolean online;
    private static volatile int players = -1;

    public record Snapshot(
        boolean online,
        boolean checking,
        int onlinePlayers,
        int maxPlayers,
        long pingMs,
        String motd,
        long updatedAtMs
    ) {
        public static final Snapshot IDLE = new Snapshot(false, false, -1, 0, -1L, "", 0L);
    }

    private LadsNetworkStatus() {}

    /** Latest cached status snapshot without blocking. */
    public static Snapshot cached() {
        return cached;
    }

    /** Test hook to inject a deterministic snapshot and reset rate limiting when IDLE. */
    public static void setCachedForTest(Snapshot snapshot) {
        Snapshot next = snapshot != null ? snapshot : Snapshot.IDLE;
        cached = next;
        online = next.online();
        players = next.online() ? next.onlinePlayers() : -1;
        if (next == Snapshot.IDLE) {
            NEXT_PING.set(0L);
        } else {
            NEXT_PING.set(System.currentTimeMillis() + INTERVAL_MS);
        }
    }

    /** False until the first ping answers, and whenever the last one failed. */
    public static boolean online() {
        refreshIfStale();
        return online;
    }

    /** Players on the network, or -1 when unknown. */
    public static int players() {
        refreshIfStale();
        return players;
    }

    /** Triggers a background ping if the cached snapshot is older than the refresh interval. */
    public static void refreshIfStale() {
        long now = System.currentTimeMillis(), due = NEXT_PING.get();
        if (now < due || !NEXT_PING.compareAndSet(due, now + INTERVAL_MS)) return;
        startPing();
    }

    /** Forces an immediate background refresh (e.g. when the user clicks Refresh in the Network Hub). */
    public static void forceRefresh() {
        NEXT_PING.set(System.currentTimeMillis() + INTERVAL_MS);
        startPing();
    }

    /** Compact uppercase summary line for the Network Hub / title telemetry. */
    public static String titleStatusLine() {
        Snapshot snap = cached;
        if (snap.online()) {
            int max = Math.max(Math.max(0, snap.onlinePlayers()), snap.maxPlayers());
            String count = snap.onlinePlayers() >= 0
                ? (max > 0 ? snap.onlinePlayers() + "/" + max + " ONLINE" : snap.onlinePlayers() + " ONLINE")
                : "ONLINE";
            String latency = snap.pingMs() > 0 ? " · " + snap.pingMs() + "MS" : "";
            return count + latency + " · " + LadsNetwork.ADDRESS.toUpperCase(Locale.ROOT);
        }
        if (snap.checking()) {
            return "PINGING " + LadsNetwork.ADDRESS.toUpperCase(Locale.ROOT) + "...";
        }
        return LadsNetwork.ADDRESS.toUpperCase(Locale.ROOT) + " · OFFLINE";
    }

    private static void startPing() {
        if (!IN_FLIGHT.compareAndSet(false, true)) return;
        Snapshot prev = cached;
        cached = new Snapshot(prev.online(), true, prev.onlinePlayers(), prev.maxPlayers(), prev.pingMs(), prev.motd(), prev.updatedAtMs());
        Thread thread = new Thread(LadsNetworkStatus::ping, "Lads-NetworkStatus");
        thread.setDaemon(true);
        thread.start();
    }

    private static void ping() {
        try {
            // No SRV lookup: mc.arashyt.ca listens directly on 25565.
            Snapshot next = pingHost(LadsNetwork.ADDRESS, PORT, TIMEOUT_MS);
            cached = next;
            online = next.online();
            players = next.onlinePlayers();
        } catch (IOException | RuntimeException failure) {
            online = false;
            players = -1;
            cached = new Snapshot(false, false, -1, 0, -1L, "", System.currentTimeMillis());
        } finally {
            IN_FLIGHT.set(false);
        }
    }

    /** One server-list ping (protocol since 1.7): the player count it reports, -1 when it reports none; throws when it does not answer. */
    public static int query(String host, int port, int timeoutMs) throws IOException {
        return pingHost(host, port, timeoutMs).onlinePlayers();
    }

    /** Full server-list ping returning player counts, cleaned MOTD, and round-trip latency; throws when the host does not answer. */
    public static Snapshot pingHost(String host, int port, int timeoutMs) throws IOException {
        try (Socket socket = new Socket()) {
            long startNs = System.nanoTime();
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            socket.setSoTimeout(timeoutMs);
            ByteArrayOutputStream handshake = new ByteArrayOutputStream();
            DataOutputStream body = new DataOutputStream(handshake);
            writeVarInt(body, 0); // handshake packet
            writeVarInt(body, -1); // "any protocol", as a status query
            byte[] address = host.getBytes(StandardCharsets.UTF_8);
            writeVarInt(body, address.length);
            body.write(address);
            body.writeShort(port);
            writeVarInt(body, 1); // next state: status
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            writeVarInt(out, handshake.size());
            handshake.writeTo(out);
            out.writeByte(1); // status request: length 1, packet 0
            out.writeByte(0);
            out.flush();
            DataInputStream in = new DataInputStream(socket.getInputStream());
            readVarInt(in); // reply length
            if (readVarInt(in) != 0) throw new IOException("not a status reply");
            int size = readVarInt(in);
            if (size < 0 || size > MAX_REPLY) throw new IOException("bad status size");
            byte[] jsonBytes = new byte[size];
            in.readFully(jsonBytes);
            long rttMs = Math.max(1L, (System.nanoTime() - startNs) / 1_000_000L);

            // Optional SLP ping/pong frame for accurate post-handshake RTT; minimal servers may close after status JSON.
            try {
                long pingSentNs = System.nanoTime();
                ByteArrayOutputStream pingFrame = new ByteArrayOutputStream();
                DataOutputStream pingBody = new DataOutputStream(pingFrame);
                writeVarInt(pingBody, 1); // ping packet id
                pingBody.writeLong(pingSentNs);
                writeVarInt(out, pingFrame.size());
                pingFrame.writeTo(out);
                out.flush();
                int pongLen = readVarInt(in);
                if (pongLen >= 9 && readVarInt(in) == 1) {
                    in.readLong();
                    rttMs = Math.max(1L, (System.nanoTime() - pingSentNs) / 1_000_000L);
                }
            } catch (IOException ignored) {
                // Keep handshake+status elapsed RTT when server closes immediately after status response.
            }

            return parseStatusJson(new String(jsonBytes, StandardCharsets.UTF_8), rttMs);
        }
    }

    private static Snapshot parseStatusJson(String json, long pingMs) {
        int onlinePlayers = -1;
        int maxPlayers = 0;
        String motd = "";
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            if (root.has("players") && root.get("players").isJsonObject()) {
                JsonObject p = root.getAsJsonObject("players");
                if (p.has("online") && p.get("online").isJsonPrimitive()) {
                    onlinePlayers = p.get("online").getAsInt();
                }
                if (p.has("max") && p.get("max").isJsonPrimitive()) {
                    maxPlayers = p.get("max").getAsInt();
                }
            }
            if (root.has("description")) {
                motd = cleanMotd(extractText(root.get("description")));
            }
        } catch (RuntimeException ignored) {
            // Unreadable optional fields fall back cleanly while preserving online reachability.
        }
        return new Snapshot(true, false, onlinePlayers, maxPlayers, pingMs, motd, System.currentTimeMillis());
    }

    private static String extractText(JsonElement el) {
        if (el == null || el.isJsonNull()) return "";
        if (el.isJsonPrimitive()) return el.getAsString();
        if (el.isJsonArray()) {
            StringBuilder sb = new StringBuilder();
            JsonArray arr = el.getAsJsonArray();
            for (JsonElement child : arr) sb.append(extractText(child));
            return sb.toString();
        }
        if (el.isJsonObject()) {
            JsonObject obj = el.getAsJsonObject();
            StringBuilder sb = new StringBuilder();
            if (obj.has("text") && obj.get("text").isJsonPrimitive()) {
                sb.append(obj.get("text").getAsString());
            }
            if (obj.has("extra") && obj.get("extra").isJsonArray()) {
                for (JsonElement extra : obj.getAsJsonArray("extra")) {
                    sb.append(extractText(extra));
                }
            }
            return sb.toString();
        }
        return "";
    }

    static String cleanMotd(String raw) {
        if (raw == null || raw.isEmpty()) return "";
        StringBuilder stripped = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '§' && i + 1 < raw.length()) {
                i++;
                continue;
            }
            stripped.append(c == '\n' || c == '\r' ? ' ' : c);
        }
        return stripped.toString().replaceAll("\\s+", " ").trim();
    }

    private static void writeVarInt(DataOutputStream out, int value) throws IOException {
        while ((value & ~0x7F) != 0) {
            out.writeByte((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        out.writeByte(value);
    }

    private static int readVarInt(DataInputStream in) throws IOException {
        int value = 0;
        for (int shift = 0; shift < 35; shift += 7) {
            int b = in.readUnsignedByte();
            value |= (b & 0x7F) << shift;
            if ((b & 0x80) == 0) return value;
        }
        throw new IOException("varint too long");
    }
}
