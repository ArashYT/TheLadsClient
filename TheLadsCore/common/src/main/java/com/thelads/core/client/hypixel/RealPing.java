package com.thelads.core.client.hypixel;

import com.thelads.core.client.HypixelSafetyManager;
import com.thelads.core.client.bridge.LadsGameBridge;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Your real ping where the tab list's is fake. Hypixel lists every player at 1 ms (and some networks do the same): there the
 * Ping HUD and PingView show a measured value for you and no number ("-") for everyone else. Detected by the Hypixel hostname, or
 * when every listed player (two or more) is at 1 ms or less on a server that is not on this PC or LAN.
 * Measured every 5 s: on Hypixel with the official Hypixel Mod API ping (plugin channel hypixel:ping, request/response as in
 * github.com/HypixelDev/ModAPI: serverbound VarInt version 1; clientbound boolean success, then VarInt version and a String, or an
 * error VarInt), timed from send to arrival on the network thread; elsewhere, or when the Mod API stays silent or answers with an
 * error, with a server-list status ping to the connected address (what Patcher and Essential do).
 */
public final class RealPing {
    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore");
    public static final String CHANNEL = "hypixel:ping";
    private static final byte[] REQUEST = {1};
    static final long INTERVAL = 5_000_000_000L, TIMEOUT = 5_000_000_000L, STALE = 30_000_000_000L;
    private static final int STATUS_PROTOCOL = 47, MAX_PACKET = 1 << 20;

    private static volatile Object connection;
    private static volatile boolean fake;
    private static volatile int measured = -1;
    private static volatile long measuredAt, sentAt, clock;
    private static volatile boolean statusBusy;
    /** 5 s, doubled (up to a minute) while Hypixel answers "rate limited". */
    private static volatile long interval = INTERVAL;
    private static long nextAt, checkedAt;
    // Written by the answer on the network thread, read by the client tick.
    private static volatile int misses;
    private static volatile boolean modApiOff;
    private static final ExecutorService STATUS = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "Lads Real Ping");
        thread.setDaemon(true);
        return thread;
    });

    private RealPing() {}

    /** The Mod API ping request's payload (serverbound VarInt version 1). */
    public static byte[] request() { return REQUEST.clone(); }

    /**
     * What a clientbound hypixel:ping payload says: 1 = a pong of version 1, 0 = not readable or another version (ignored, as the
     * Mod API does), or the negated error id (1 disabled, 2 internal error, 3 rate limited, 4 invalid version, 5 no longer supported).
     */
    public static int decode(byte[] data) {
        try {
            int[] at = {0};
            if (data == null || data.length == 0) return 0;
            boolean success = data[at[0]++] != 0;
            if (!success) return -Math.max(1, readVarInt(data, at));
            if (readVarInt(data, at) != 1) return 0;
            int length = readVarInt(data, at);
            if (length < 0 || at[0] + length > data.length) return 0;
            return 1;
        } catch (IndexOutOfBoundsException malformed) {
            return 0;
        }
    }

    /** A clientbound plugin message, on whatever thread it arrived (the network thread: its arrival time is the reply's). */
    public static boolean receive(String channel, byte[] data, long nanos) {
        if (!CHANNEL.equals(channel)) return false;
        int result = decode(data);
        long sent = sentAt;
        if (result == 1 && sent != 0) {
            measured((nanos - sent) / 1_000_000L, nanos);
            sentAt = 0;
            misses = 0;
            interval = INTERVAL;
        } else if (result < 0) {
            sentAt = 0;
            if (result == -3) interval = Math.min(12 * INTERVAL, interval * 2); // rate limited: slower
            else modApiOff = true; // any other error: status pings instead
            LOGGER.info("Lads real ping: Hypixel Mod API ping answered with error {}", -result);
        }
        return true;
    }

    private static void measured(long ms, long nanos) {
        measured = (int) Math.max(0, Math.min(9999, ms));
        measuredAt = nanos;
    }

    /** Each client tick; {@code current} is the connection (any object unique to it), null when not connected. */
    public static void tick(LadsGameBridge game, Object current, long now) {
        clock = now;
        if (current != connection) {
            connection = current;
            fake = false;
            measured = -1;
            sentAt = 0;
            misses = 0;
            modApiOff = false;
            interval = INTERVAL;
            nextAt = now + 1_000_000_000L; // let the join settle
            checkedAt = now - 1_000_000_000L;
        }
        if (current == null) return;
        boolean hypixel = HypixelSafetyManager.isHypixel();
        InetSocketAddress address = game.getServerSocketAddress();
        if (hypixel) fake = true;
        else if (now - checkedAt >= 1_000_000_000L) { // the whole tab list, once a second
            checkedAt = now;
            fake = fakeListed(game.getListedLatencies(), address);
        }
        if (!fake || now - nextAt < 0) return;
        long sent = sentAt;
        if (sent != 0) {
            if (now - sent < TIMEOUT) return;
            sentAt = 0;
            if (++misses >= 3) { modApiOff = true; LOGGER.info("Lads real ping: no Hypixel Mod API answer, using status pings"); }
        }
        nextAt = now + interval;
        if (hypixel && !modApiOff) {
            sentAt = now;
            if (game.sendPluginMessage(CHANNEL, REQUEST)) return;
            sentAt = 0;
            modApiOff = true;
        }
        if (address != null && !statusBusy) {
            statusBusy = true;
            String host = host(game.getServerAddress(), address);
            STATUS.execute(() -> {
                try {
                    int ms = statusPing(address, host, 3000);
                    if (connection == current) measured(ms, System.nanoTime());
                } catch (IOException | RuntimeException failure) {
                    LOGGER.debug("Lads real ping: status ping to {} failed: {}", address, failure.toString());
                } finally {
                    statusBusy = false;
                }
            });
        }
    }

    /** Every listed latency (two or more players) is 1 ms or less, on a server that is not this PC or the LAN: fake. */
    static boolean fakeListed(int[] latencies, InetSocketAddress address) {
        if (latencies == null || latencies.length < 2 || address == null) return false;
        InetAddress ip = address.getAddress();
        if (ip != null && (ip.isLoopbackAddress() || ip.isSiteLocalAddress() || ip.isLinkLocalAddress() || ip.isAnyLocalAddress())) return false;
        for (int latency : latencies) if (latency > 1) return false;
        return true;
    }

    /** QA: how long the Mod API ping has been waiting for its answer (ns), or -1. */
    public static long waiting(long now) { long sent = sentAt; return sent == 0 ? -1 : now - sent; }

    /** True while the listed latencies on this server are fake (Hypixel, or all at 1 ms or less). */
    public static boolean fakeListing() { return fake; }

    /** "No real value" (shown as "-"): kept apart from the -1 Minecraft lists for a latency it does not know yet. */
    public static final int NONE = Integer.MIN_VALUE;

    /** The ping to show for yourself: the measured one where the listing is fake ({@link #NONE} until there is one), else the listed one. */
    public static int own(int listed) {
        if (!fake) return listed;
        return measured >= 0 && clock - measuredAt < STALE ? measured : NONE;
    }

    /** A tab-list row's ping: your own as {@link #own}; anyone else's {@link #NONE} where the listing is fake. */
    public static int row(int listed, boolean self) {
        return self ? own(listed) : fake ? NONE : listed;
    }

    /** The host name for the status handshake: the address typed (without its port), else the IP. */
    private static String host(String typed, InetSocketAddress address) {
        String host = typed == null ? "" : typed.trim();
        int colon = host.lastIndexOf(':');
        if (colon > 0 && host.indexOf(':') == colon) host = host.substring(0, colon);
        if (host.isEmpty() || host.equalsIgnoreCase("Singleplayer") || host.equalsIgnoreCase("Unknown")) host = address.getHostString();
        return host;
    }

    /**
     * A server-list status ping (handshake, status request and response, then ping and pong): the milliseconds between sending the
     * ping and its pong.
     */
    public static int statusPing(InetSocketAddress address, String host, int timeoutMs) throws IOException {
        try (Socket socket = new Socket()) {
            socket.setTcpNoDelay(true);
            socket.connect(address, timeoutMs);
            socket.setSoTimeout(timeoutMs);
            OutputStream out = socket.getOutputStream();
            DataInputStream in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
            ByteArrayOutputStream handshake = new ByteArrayOutputStream();
            writeVarInt(handshake, 0);
            writeVarInt(handshake, STATUS_PROTOCOL);
            byte[] name = host.getBytes(StandardCharsets.UTF_8);
            writeVarInt(handshake, name.length);
            handshake.write(name);
            handshake.write(address.getPort() >> 8);
            handshake.write(address.getPort());
            writeVarInt(handshake, 1);
            ByteArrayOutputStream first = new ByteArrayOutputStream();
            packet(first, handshake.toByteArray());
            packet(first, new byte[] {0});
            out.write(first.toByteArray());
            out.flush();
            if (packetId(in) != 0) throw new IOException("not a status response");
            long start = System.nanoTime();
            ByteArrayOutputStream ping = new ByteArrayOutputStream();
            packet(ping, new byte[] {1, 0, 0, 0, 0, 0, 0, 0, 42});
            out.write(ping.toByteArray());
            out.flush();
            if (packetId(in) != 1) throw new IOException("not a pong");
            return (int) ((System.nanoTime() - start) / 1_000_000L);
        }
    }

    /** Reads one packet whole and returns its id. */
    private static int packetId(DataInputStream in) throws IOException {
        int length = readVarInt(in);
        if (length <= 0 || length > MAX_PACKET) throw new IOException("bad packet length " + length);
        byte[] body = new byte[length];
        in.readFully(body);
        return readVarInt(body, new int[] {0});
    }

    private static void packet(ByteArrayOutputStream out, byte[] body) {
        writeVarInt(out, body.length);
        out.write(body, 0, body.length);
    }

    static void writeVarInt(ByteArrayOutputStream out, int value) {
        while ((value & ~0x7F) != 0) {
            out.write((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        out.write(value);
    }

    private static int readVarInt(byte[] data, int[] at) {
        int value = 0;
        for (int shift = 0; shift < 35; shift += 7) {
            byte b = data[at[0]++];
            value |= (b & 0x7F) << shift;
            if ((b & 0x80) == 0) return value;
        }
        throw new IndexOutOfBoundsException("VarInt too big");
    }

    private static int readVarInt(InputStream in) throws IOException {
        int value = 0;
        for (int shift = 0; shift < 35; shift += 7) {
            int b = in.read();
            if (b < 0) throw new EOFException();
            value |= (b & 0x7F) << shift;
            if ((b & 0x80) == 0) return value;
        }
        throw new IOException("VarInt too big");
    }
}
