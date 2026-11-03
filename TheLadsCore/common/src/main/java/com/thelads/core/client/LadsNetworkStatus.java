package com.thelads.core.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Whether The Lads Network is up, and how many are on it: a server-list ping of our own server on a background thread, at most
 * once a minute and only while something asks. Never blocks the caller; any failure simply reads as offline.
 */
public final class LadsNetworkStatus {
    private static final long INTERVAL_MS = 60_000L;
    private static final int TIMEOUT_MS = 4_000, PORT = 25565, MAX_REPLY = 1 << 20;
    private static final AtomicLong NEXT_PING = new AtomicLong();
    private static volatile boolean online;
    private static volatile int players = -1;

    private LadsNetworkStatus() {}

    /** False until the first ping answers, and whenever the last one failed. */
    public static boolean online() {
        refresh();
        return online;
    }

    /** Players on the network, or -1 when unknown. */
    public static int players() {
        refresh();
        return players;
    }

    private static void refresh() {
        long now = System.currentTimeMillis(), due = NEXT_PING.get();
        if (now < due || !NEXT_PING.compareAndSet(due, now + INTERVAL_MS)) return;
        Thread thread = new Thread(LadsNetworkStatus::ping, "Lads-NetworkStatus");
        thread.setDaemon(true);
        thread.start();
    }

    private static void ping() {
        try {
            // ponytail: no SRV lookup, mc.arashyt.ca has no SRV record; add one if the network ever moves off 25565.
            players = query(LadsNetwork.ADDRESS, PORT, TIMEOUT_MS);
            online = true;
        } catch (IOException | RuntimeException failure) {
            online = false;
            players = -1;
        }
    }

    /** One server-list ping (protocol since 1.7): the player count it reports, -1 when it reports none; throws when it does not answer. */
    public static int query(String host, int port, int timeoutMs) throws IOException {
        try (Socket socket = new Socket()) {
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
            byte[] json = new byte[size];
            in.readFully(json);
            return playersIn(new String(json, StandardCharsets.UTF_8));
        }
    }

    private static int playersIn(String json) {
        try {
            JsonElement count = JsonParser.parseString(json).getAsJsonObject().getAsJsonObject("players").get("online");
            return count == null ? -1 : count.getAsInt();
        } catch (RuntimeException unreadable) {
            return -1;
        }
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
