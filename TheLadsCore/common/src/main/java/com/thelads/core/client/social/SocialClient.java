package com.thelads.core.client.social;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.thelads.core.client.bridge.LadsGameBridge;
import com.thelads.core.client.cosmetics.net.WebSocket;
import com.thelads.core.config.ShareCodeClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.ProtocolException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadLocalRandom;

/**
 * WebSocket client for friends, presence, invites and ICE signaling (/social/ws).
 * Follows the CosmeticsLink auth/reconnect/backoff pattern and shares cosmetics/net/WebSocket.
 */
public class SocialClient {
    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore/SocialClient");
    private static final String MOJANG_JOIN = "https://sessionserver.mojang.com/session/minecraft/join";
    private static final int MAX_OUTBOUND = 512;

    private static volatile SocialClient instance;

    public static SocialClient get() {
        if (instance == null) {
            synchronized (SocialClient.class) {
                if (instance == null) {
                    instance = new SocialClient(System.getProperty("lads.social.url", System.getenv("LADS_SOCIAL_URL")));
                }
            }
        }
        return instance;
    }

    public static void setInstanceForTest(SocialClient client) {
        instance = client;
    }

    private final URI endpoint;
    private final boolean local;
    private final Object lock = new Object();
    private final Object outboundLock = new Object();
    private final BlockingQueue<JsonObject> outbound = new LinkedBlockingQueue<>();

    private volatile WebSocket currentWs;
    private volatile boolean running;
    private volatile boolean fastBackoff;
    private volatile Presence currentPresence;
    private volatile String status = "Disconnected";
    private Thread clientThread;

    public SocialClient(String urlOverride) {
        this.endpoint = resolveEndpoint(urlOverride);
        this.local = endpoint != null && "ws".equals(endpoint.getScheme());
    }

    public static URI resolveEndpoint(String override) {
        String url = override == null || override.trim().isEmpty() ? SocialProtocol.URL_DEFAULT : override.trim();
        try {
            URI uri = new URI(url);
            String host = uri.getHost();
            if (host != null && ("wss".equals(uri.getScheme())
                    || "ws".equals(uri.getScheme()) && (host.equals("localhost") || host.equals("127.0.0.1") || host.equals("[::1]")))) {
                return uri;
            }
        } catch (URISyntaxException ignored) {}
        LOGGER.warn("SocialClient: invalid social URL {} (wss://, or ws:// to localhost only); social client disabled", url);
        return null;
    }

    public synchronized void start() {
        if (running || endpoint == null) return;
        running = true;
        clientThread = new Thread(this::runLoop, "Lads Social Client");
        clientThread.setDaemon(true);
        clientThread.setPriority(Thread.MIN_PRIORITY + 1);
        clientThread.start();
    }

    public synchronized void close() {
        running = false;
        WebSocket ws = currentWs;
        currentWs = null;
        if (ws != null) {
            try { ws.close(); } catch (Exception ignored) {}
        }
        if (clientThread != null) {
            clientThread.interrupt();
            clientThread = null;
        }
        synchronized (lock) {
            lock.notifyAll();
        }
        status = "Disconnected";
    }

    public synchronized void stop() {
        close();
    }

    public void setFastBackoff(boolean fast) {
        this.fastBackoff = fast;
    }

    public int outboundQueueSize() {
        return outbound.size();
    }

    public List<JsonObject> outboundSnapshot() {
        return new ArrayList<>(outbound);
    }

    public String connectionStatus() {
        return status;
    }

    public void setPresence(Presence presence) {
        this.currentPresence = presence;
        if (isConnected()) {
            publishPresence(presence);
        }
    }

    public boolean isConnected() {
        WebSocket ws = currentWs;
        return ws != null && ws.isOpen();
    }

    private void enqueueOutbound(JsonObject obj) {
        if (obj == null) return;
        synchronized (outboundLock) {
            while (outbound.size() >= MAX_OUTBOUND) {
                boolean removedPresence = false;
                for (JsonObject candidate : outbound) {
                    if (candidate != null && SocialProtocol.PRESENCE.equals(str(candidate, "t"))) {
                        outbound.remove(candidate);
                        removedPresence = true;
                        break;
                    }
                }
                if (!removedPresence) {
                    outbound.poll();
                }
            }
            outbound.offer(obj);
        }
    }

    public void send(JsonObject obj) {
        if (obj == null) return;
        WebSocket ws = currentWs;
        if (ws != null && ws.isOpen()) {
            try {
                ws.sendText(obj.toString());
            } catch (IOException e) {
                LOGGER.warn("SocialClient: failed to send message: {}", e.getMessage());
                enqueueOutbound(obj);
            }
        } else {
            enqueueOutbound(obj);
        }
    }

    public void send(String type, Object... kv) {
        send(SocialProtocol.msg(type, kv));
    }

    private void runLoop() {
        int failures = 0;
        while (running) {
            LadsGameBridge.Session session = LadsGameBridge.get().session();
            if (session == null || session.uuid() == null) {
                status = "Awaiting login";
                pause(1000);
                continue;
            }

            status = "Connecting...";
            long started = System.currentTimeMillis();
            boolean failed = false;

            try {
                connectAndServe(session);
            } catch (Exception e) {
                failed = true;
                LOGGER.info("SocialClient: link down ({})", e.toString());
            } finally {
                status = "Disconnected";
                currentWs = null;
            }

            if (!running) break;

            if (System.currentTimeMillis() - started > 60_000) {
                failures = 0;
            }

            long waitMs = fastBackoff ? 50L : backoffMs(++failures, ThreadLocalRandom.current().nextDouble());
            pause(waitMs);
        }
    }

    private void connectAndServe(LadsGameBridge.Session session) throws IOException {
        try (WebSocket ws = WebSocket.connect(endpoint, 5000, ShareCodeClient.USER_AGENT)) {
            ws.setReadTimeout(15_000);
            JsonObject challenge = parse(ws.readText());
            String nonce = challenge == null ? null : str(challenge, "nonce");
            if (nonce == null || !"challenge".equals(str(challenge, "t"))) {
                throw new ProtocolException("No challenge received from social service");
            }

            String serverId = serverId(nonce);
            if (local) {
                LOGGER.info("SocialClient: local endpoint {}, Mojang auth skipped", endpoint);
            } else {
                mojangJoin(session.accessToken(), session.uuid(), serverId);
            }

            JsonObject auth = SocialProtocol.msg(SocialProtocol.AUTH,
                    "name", session.name(),
                    "uuid", plain(session.uuid()),
                    "serverId", serverId);
            ws.sendText(auth.toString());

            JsonObject hello = parse(ws.readText());
            if (hello == null || !SocialProtocol.HELLO.equals(str(hello, "t"))) {
                throw new ProtocolException("No hello received after auth");
            }

            this.currentWs = ws;
            status = "Connected";
            LOGGER.info("SocialClient: connected to {}", endpoint);

            // Notify SocialState of hello payload
            SocialState state = SocialState.get();
            if (state != null) {
                state.onHello(hello);
            }

            onConnected();

            ws.setReadTimeout(90_000);
            Thread reader = new Thread(() -> readMessages(ws), "Lads Social Reader");
            reader.setDaemon(true);
            reader.setPriority(Thread.MIN_PRIORITY);
            reader.start();

            long nextPing = System.currentTimeMillis() + 30_000;
            long nextPresence = System.currentTimeMillis() + 60_000;

            while (ws.isOpen() && running) {
                long now = System.currentTimeMillis();

                // Send pending queued messages
                JsonObject queued;
                while ((queued = outbound.poll()) != null) {
                    ws.sendText(queued.toString());
                }

                if (now >= nextPing) {
                    nextPing = now + 30_000;
                    ws.send(WebSocket.PING, new byte[0]);
                }

                if (now >= nextPresence) {
                    nextPresence = now + 60_000;
                    publishPresence(currentPresence);
                }

                synchronized (lock) {
                    try {
                        lock.wait(Math.min(1000, Math.max(1, nextPing - now)));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
    }

    public void onConnected() {
        publishPresence(currentPresence);
    }

    private void publishPresence(Presence p) {
        if (p == null) {
            p = new Presence("menu", SocialBridge.get().gameInfo(), null, null);
        }
        JsonObject msg = new JsonObject();
        msg.addProperty("t", SocialProtocol.PRESENCE);
        msg.addProperty("state", p.state());
        if (p.server() != null) msg.addProperty("server", p.server());
        if (p.game() != null) {
            JsonObject g = new JsonObject();
            g.addProperty("mc", p.game().mc());
            g.addProperty("loader", p.game().loader());
            g.addProperty("profileKind", p.game().profileKind());
            g.addProperty("packId", p.game().packId());
            g.addProperty("packVersion", p.game().packVersion());
            g.addProperty("modsHash", p.game().modsHash());
            g.addProperty("lads", p.game().lads());
            msg.add("game", g);
        }
        if (p.world() != null) {
            JsonObject w = new JsonObject();
            w.addProperty("id", p.world().id());
            w.addProperty("name", p.world().name());
            w.addProperty("scope", p.world().scope());
            w.addProperty("slots", p.world().slots());
            w.addProperty("used", p.world().used());
            msg.add("world", w);
        }
        send(msg);
    }

    private void readMessages(WebSocket ws) {
        try {
            for (String text; (text = ws.readText()) != null; ) {
                JsonObject json = parse(text);
                if (json != null) {
                    SocialState state = SocialState.get();
                    if (state != null) {
                        state.onMessage(json);
                    }
                }
            }
        } catch (Exception e) {
            if (ws.isOpen()) LOGGER.info("SocialClient: read error ({})", e.toString());
        } finally {
            ws.close();
            synchronized (lock) { lock.notifyAll(); }
        }
    }

    public static long backoffMs(int failures, double random) {
        long base = Math.min(300_000L, 5_000L << Math.min(Math.max(failures, 1) - 1, 6));
        return base - (long) (base * 0.2 * random);
    }

    private static void mojangJoin(String token, UUID self, String serverId) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(MOJANG_JOIN).openConnection();
        try {
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("User-Agent", ShareCodeClient.USER_AGENT);
            try (OutputStream out = connection.getOutputStream()) {
                out.write(json("accessToken", token, "selectedProfile", plain(self), "serverId", serverId).getBytes(StandardCharsets.UTF_8));
            }
            int status = connection.getResponseCode();
            if (status != 204) throw new IOException("Mojang join refused (HTTP " + status + ")");
        } finally {
            connection.disconnect();
        }
    }

    private static String serverId(String nonce) {
        return new BigInteger(digest("SHA-1", "lads-cosmetics:" + nonce)).toString(16);
    }

    private static byte[] digest(String algorithm, String text) {
        try {
            return MessageDigest.getInstance(algorithm).digest(text.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String plain(UUID uuid) {
        return uuid.toString().replace("-", "");
    }

    public static UUID parseUuid(String plain) {
        if (plain == null) return null;
        if (plain.contains("-")) {
            try { return UUID.fromString(plain); } catch (Exception ignored) { return null; }
        }
        if (plain.length() != 32) return null;
        try {
            return new UUID(Long.parseUnsignedLong(plain.substring(0, 16), 16), Long.parseUnsignedLong(plain.substring(16), 16));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String json(String... keyValues) {
        JsonObject object = new JsonObject();
        for (int i = 0; i < keyValues.length; i += 2) object.addProperty(keyValues[i], keyValues[i + 1]);
        return object.toString();
    }

    private static JsonObject parse(String text) {
        try {
            JsonElement element = text == null ? null : JsonParser.parseString(text);
            return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String str(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() ? value.getAsString() : null;
    }

    private void pause(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
