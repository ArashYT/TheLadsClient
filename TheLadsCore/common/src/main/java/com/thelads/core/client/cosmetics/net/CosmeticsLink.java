package com.thelads.core.client.cosmetics.net;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.thelads.core.client.bridge.LadsGameBridge;
import com.thelads.core.client.cosmetics.Emotes;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.ShareCodeClient;
import com.thelads.core.modules.CosmeticsModule;
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
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Relays emotes between Lads players on the same Minecraft server, out of band: one WebSocket to our bot
 * (wss://bot.arashyt.ca/cosmetics/ws, protocol in cosmetics-server/cosmetics.py), never a Minecraft packet or plugin message, so it
 * works on (and is invisible to) every server, Hypixel included. Connected only in a multiplayer world with the Cosmetics module on.
 * Threads: the game thread samples state once a second (tick), a daemon link thread connects and writes (asleep when not
 * connected), a reader thread per connection applies what arrives to Emotes. Nothing here ever blocks the game thread.
 */
public final class CosmeticsLink {
    static final String DEFAULT_URL = "wss://bot.arashyt.ca/cosmetics/ws";
    private static final String MOJANG_JOIN = "https://sessionserver.mojang.com/session/minecraft/join";
    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore/Cosmetics");
    private static final CosmeticsLink LINK = new CosmeticsLink(System.getProperty("lads.cosmetics.url", System.getenv("LADS_COSMETICS_URL")));

    /** Where the link should be: who we are and which server room; null = disconnected. */
    record Want(UUID self, String room) {}

    private final URI endpoint; // null: a bad override turned the link off
    private final boolean local; // ws:// to this PC = QA: no Mojang join (offline accounts), QA env hooks honored
    private final String qaServer, qaEmote;
    private final Object lock = new Object();
    private volatile Want want;
    private volatile String localEmote;
    private volatile boolean emoteDirty, showOthers = true, shareMine = true;
    private Thread thread;
    private int ticks; // game thread only, as the two below
    private String lastAddress, lastRoom;

    CosmeticsLink(String url) {
        endpoint = endpoint(url);
        local = endpoint != null && "ws".equals(endpoint.getScheme());
        qaServer = local ? System.getenv("LADS_COSMETICS_QA_SERVER") : null;
        qaEmote = local ? System.getenv("LADS_COSMETICS_QA_EMOTE") : null;
        Emotes.setOutbound((id, start) -> {
            localEmote = id;
            emoteDirty = true;
            wake();
        });
    }

    /** Game thread, every client tick; samples once a second. */
    public static void tick() { LINK.sample(); }

    private void sample() {
        if (++ticks % 20 != 0 || endpoint == null) return;
        Module module = ModuleManager.getInstance().getModule("Cosmetics");
        CosmeticsModule cosmetics = module instanceof CosmeticsModule && module.isEnabled() ? (CosmeticsModule) module : null;
        boolean show = cosmetics != null && cosmetics.showOthers.get(), share = cosmetics != null && cosmetics.shareMine.get();
        LadsGameBridge game = LadsGameBridge.get();
        String address = qaServer != null ? qaServer : game.getServerAddress();
        if (!Objects.equals(address, lastAddress)) {
            lastAddress = address;
            lastRoom = room(address);
        }
        LadsGameBridge.Session session = (show || share) && lastRoom != null && game.isIngame() ? game.session() : null;
        Want next = session == null || session.uuid() == null ? null : new Want(session.uuid(), lastRoom);
        Want current = want;
        if (showOthers && !show && current != null) Emotes.clearRemote(current.self());
        showOthers = show;
        shareMine = share;
        if (Objects.equals(next, current)) return;
        want = next;
        if (next != null) start();
        wake();
    }

    private synchronized void start() {
        if (thread != null) return;
        thread = new Thread(this::run, "Lads Cosmetics");
        thread.setDaemon(true);
        thread.setPriority(Thread.MIN_PRIORITY);
        thread.start();
    }

    private void wake() {
        synchronized (lock) {
            lock.notifyAll();
        }
    }

    private void run() {
        int failures = 0;
        while (true) {
            Want w = awaitWant();
            long started = System.currentTimeMillis();
            boolean failed = false;
            try {
                serve(w);
            } catch (Exception e) { // never logs the token: our messages carry status codes only
                failed = true;
                LOGGER.info("Lads cosmetics: link down ({})", e.toString());
            } finally {
                Emotes.clearRemote(w.self());
            }
            if (System.currentTimeMillis() - started > 60_000) failures = 0; // a link that lived a minute resets the backoff
            if (failed || w.equals(want)) pause(backoffMs(++failures, ThreadLocalRandom.current().nextDouble()));
        }
    }

    private void serve(Want w) throws IOException {
        LadsGameBridge.Session session = LadsGameBridge.get().session();
        if (session == null || !w.self().equals(session.uuid())) return;
        try (WebSocket ws = WebSocket.connect(endpoint, 5000, ShareCodeClient.USER_AGENT)) {
            ws.setReadTimeout(15_000);
            JsonObject challenge = parse(ws.readText());
            String nonce = challenge == null ? null : str(challenge, "nonce");
            if (nonce == null || !"challenge".equals(str(challenge, "t"))) throw new ProtocolException("no challenge");
            String serverId = serverId(nonce);
            if (local) LOGGER.info("Lads cosmetics: local endpoint {}, Mojang join skipped (QA)", endpoint);
            else mojangJoin(session.accessToken(), w.self(), serverId);
            ws.sendText(json("t", "auth", "name", session.name(), "uuid", plain(w.self()), "serverId", serverId));
            ws.sendText(json("t", "room", "room", w.room()));
            ws.setReadTimeout(90_000); // the server pings every 30 s
            Thread reader = new Thread(() -> read(ws, w.self()), "Lads Cosmetics Reader");
            reader.setDaemon(true);
            reader.setPriority(Thread.MIN_PRIORITY);
            reader.start();
            emoteDirty = localEmote != null; // still emoting from before a reconnect: tell the new room
            long nextQa = 0;
            while (ws.isOpen() && w.equals(want)) {
                if (qaEmote != null && System.currentTimeMillis() >= nextQa) { // QA: replay every 15 s so a later client sees a live state
                    nextQa = System.currentTimeMillis() + 15_000;
                    Emotes.playLocal(w.self(), qaEmote);
                }
                if (emoteDirty) {
                    emoteDirty = false;
                    if (shareMine) ws.sendText(json("t", "emote", "id", localEmote));
                }
                synchronized (lock) {
                    if (!emoteDirty && ws.isOpen() && w.equals(want)) lock.wait(qaEmote != null ? 15_000 : 60_000);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void read(WebSocket ws, UUID self) {
        try {
            for (String text; (text = ws.readText()) != null; ) handle(text, self);
            LOGGER.info("Lads cosmetics: server closed the link");
        } catch (Exception e) {
            if (ws.isOpen()) LOGGER.info("Lads cosmetics: read failed ({})", e.toString());
        } finally {
            ws.close();
            wake();
        }
    }

    /** One server message: snapshot (a room's players on join), state (someone's emote changed) or leave. */
    void handle(String text, UUID self) {
        JsonObject message = parse(text);
        String type = message == null ? null : str(message, "t");
        if ("snapshot".equals(type) && message.get("players") != null && message.get("players").isJsonArray()) {
            LOGGER.info("Lads cosmetics: in room {} with {} other Lads players", lastRoomShort(), message.getAsJsonArray("players").size());
            for (JsonElement player : message.getAsJsonArray("players")) if (player.isJsonObject()) apply(player.getAsJsonObject(), self);
        } else if ("state".equals(type)) {
            apply(message, self);
        } else if ("leave".equals(type)) {
            UUID player = uuid(str(message, "uuid"));
            if (player != null && !player.equals(self)) Emotes.remove(player);
        }
    }

    private void apply(JsonObject player, UUID self) {
        UUID uuid = uuid(str(player, "uuid"));
        if (uuid == null || uuid.equals(self) || !showOthers) return;
        String id = str(player, "emote");
        JsonElement age = player.get("age");
        long ageMs = age != null && age.isJsonPrimitive() && age.getAsJsonPrimitive().isNumber() ? age.getAsLong() : 0;
        Emotes.onRemote(uuid, id, ageMs);
        if (local) LOGGER.info("Lads cosmetics: remote {} emote {} age {} ms", uuid, id, ageMs);
        else LOGGER.debug("Lads cosmetics: remote {} emote {} age {} ms", uuid, id, ageMs);
    }

    private String lastRoomShort() {
        Want w = want;
        return w == null ? "-" : w.room().substring(0, 8);
    }

    private Want awaitWant() {
        synchronized (lock) {
            while (want == null) {
                try {
                    lock.wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            return want;
        }
    }

    private void pause(long ms) {
        long until = System.currentTimeMillis() + ms;
        synchronized (lock) {
            for (long left; (left = until - System.currentTimeMillis()) > 0; ) {
                try {
                    lock.wait(left);
                } catch (InterruptedException e) {
                    return;
                }
            }
        }
    }

    /**
     * Proves to our server who we are without giving it the token: Mojang records that this account "joined" serverId, and our
     * server asks Mojang (hasJoined), exactly as a Minecraft server does. Only called once in a world, so it never races the
     * game's own join for the server it is connecting to.
     */
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

    /** The default endpoint, or an override (-Dlads.cosmetics.url / LADS_COSMETICS_URL): wss:// anywhere, ws:// only to this PC. */
    static URI endpoint(String override) {
        String url = override == null || override.trim().isEmpty() ? DEFAULT_URL : override.trim();
        try {
            URI uri = new URI(url);
            String host = uri.getHost();
            if (host != null && ("wss".equals(uri.getScheme())
                || "ws".equals(uri.getScheme()) && (host.equals("localhost") || host.equals("127.0.0.1") || host.equals("[::1]")))) return uri;
        } catch (URISyntaxException ignored) {
        }
        LOGGER.warn("Lads cosmetics: ignoring cosmetics URL {} (wss://, or ws:// to localhost only); cosmetics link off", url);
        return null;
    }

    /** SHA-256 hex of the lowercased "host:port" (port 25565 when none is given); null outside a multiplayer server. */
    static String room(String address) {
        if (address == null) return null;
        String a = address.trim().toLowerCase(Locale.ROOT);
        if (a.isEmpty() || a.equals("singleplayer") || a.equals("unknown")) return null;
        boolean port = a.startsWith("[") ? a.contains("]:") : a.indexOf(':') > 0 && a.indexOf(':') == a.lastIndexOf(':');
        return hex(digest("SHA-256", port ? a : a + ":25565"));
    }

    /** Minecraft-style signed hex SHA-1 of the server's nonce (cosmetics.py server_id). */
    static String serverId(String nonce) {
        return new BigInteger(digest("SHA-1", "lads-cosmetics:" + nonce)).toString(16);
    }

    /** 5 s, doubling per failure up to 5 min, minus up to 20 % jitter so a bot restart does not bring every client back at once. */
    static long backoffMs(int failures, double random) {
        long base = Math.min(300_000L, 5_000L << Math.min(Math.max(failures, 1) - 1, 6));
        return base - (long) (base * 0.2 * random);
    }

    static String plain(UUID uuid) { return uuid.toString().replace("-", ""); }

    static UUID uuid(String plain) {
        if (plain == null || plain.length() != 32) return null;
        try {
            return new UUID(Long.parseUnsignedLong(plain.substring(0, 16), 16), Long.parseUnsignedLong(plain.substring(16), 16));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static String json(String... keyValues) {
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

    private static byte[] digest(String algorithm, String text) {
        try {
            return MessageDigest.getInstance(algorithm).digest(text.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder out = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) out.append(Character.forDigit((b >> 4) & 15, 16)).append(Character.forDigit(b & 15, 16));
        return out.toString();
    }
}
