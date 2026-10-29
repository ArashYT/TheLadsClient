package com.thelads.ladsapi.session;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.thelads.ladsapi.LadsCore;
import com.thelads.ladsapi.api.LadsClientAPI;
import com.thelads.ladsapi.api.LadsPlayer;
import com.thelads.ladsapi.config.LadsConfig;
import com.thelads.ladsapi.platform.LadsPlatform;
import com.thelads.ladsapi.platform.LadsPlayerHandle;
import com.thelads.ladsapi.rules.LadsRules;
import com.thelads.ladsapi.util.VersionUtil;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Manages player sessions, protocol messages, presence, and implements LadsClientAPI.
 */
public final class SessionManager implements LadsClientAPI {
    /** Biggest client -> server payload (PROTOCOL.md section 1); the platforms cap their inbound packets the same way. */
    public static final int MAX_IN = 32_000;

    private final LadsPlatform platform;
    private final LadsConfig config;
    private final LadsRules rules;
    private final Map<UUID, LadsSession> sessions = new ConcurrentHashMap<>();
    private final List<Consumer<LadsPlayer>> readyListeners = new ArrayList<>();
    private final Object lock = new Object();

    public SessionManager(LadsPlatform platform, LadsConfig config, LadsRules rules) {
        this.platform = platform;
        this.config = config;
        this.rules = rules;
    }

    public LadsPlatform getPlatform() { return platform; }
    public LadsConfig getConfig() { return config; }
    public LadsRules getRules() { return rules; }
    public Map<UUID, LadsSession> getSessions() { return sessions; }

    public void addReadyListener(Consumer<LadsPlayer> listener) {
        synchronized (lock) {
            readyListeners.add(listener);
        }
    }

    @Override
    public Optional<LadsPlayer> player(UUID uuid) {
        if (uuid == null) return Optional.empty();
        LadsSession s = sessions.get(uuid);
        if (s != null && s.isLads()) {
            return Optional.of(s.getLadsPlayer());
        }
        return Optional.empty();
    }

    public void join(LadsPlayerHandle handle) {
        if (handle == null) return;

        // A proxy runs sessions only with proxy-rules: true; otherwise the backends own the policy (the proxy just sends reset).
        if (platform.isProxy() && !config.isProxyRules()) {
            return;
        }

        if (!config.isEnabled()) {
            return;
        }

        LadsSession session;
        UUID uuid = handle.uuid();
        synchronized (lock) {
            session = new LadsSession(handle, this);
            sessions.put(uuid, session);
        }

        // The tasks belong to this session object: a quick reconnect must not inherit the old tasks.
        // Schedule hello at +1 tick
        platform.runLater(() -> {
            synchronized (lock) {
                if (sessions.get(uuid) == session && !session.isHelloAcked() && !session.isHelloSent()) {
                    session.sendHello();
                }
            }
        }, 1);

        // Resend hello at +60 ticks if no reply, never more
        platform.runLater(() -> {
            synchronized (lock) {
                if (sessions.get(uuid) == session && !session.isHelloAcked() && !session.isHelloResent()) {
                    session.setHelloResent(true);
                    session.sendHello();
                }
            }
        }, 60);

        // require-lads-client: kick 5 s (100 ticks) after hello if no reply (kick executed outside lock)
        platform.runLater(() -> {
            boolean shouldKick = false;
            String kickMsg = null;
            synchronized (lock) {
                if (sessions.get(uuid) == session && config.isRequireLadsClient() && !session.isLads()) {
                    shouldKick = true;
                    kickMsg = config.getRequireLadsClientMessage();
                }
            }
            if (shouldKick) {
                session.getHandle().kick(kickMsg);
            }
        }, 100);
    }

    public void quit(UUID uuid) {
        if (uuid == null) return;
        LadsSession s;
        List<LadsSession> others = new ArrayList<>();
        synchronized (lock) {
            s = sessions.remove(uuid);
            if (s != null && s.isLads() && config.isBadgePresence()) {
                for (LadsSession other : sessions.values()) {
                    if (other.isLads()) {
                        others.add(other);
                    }
                }
            }
        }
        for (LadsSession other : others) {
            other.sendPresence(false, Collections.emptyList(), List.of(uuid));
        }
    }

    /**
     * World changed (Paper, Fabric, Forge) or proxy server switch (Velocity). The client resets itself on a JoinGame or on a Respawn into
     * a world with another seed, and then drops every policy until a new hello arrives (PROTOCOL.md section 4), so a bare policy is
     * not enough: hello, policy and presence go out again.
     */
    public void worldChanged(LadsPlayerHandle handle) {
        refresh(handle, platform.isProxy());
    }

    /** Fabric/Forge build a new player object on respawn: keep the handle current and resync only if the world differs. */
    public void respawned(LadsPlayerHandle handle) {
        refresh(handle, false);
    }

    private void refresh(LadsPlayerHandle handle, boolean force) {
        if (handle == null) return;
        LadsSession s;
        boolean doResync = false;
        boolean doHello = false;
        synchronized (lock) {
            s = sessions.get(handle.uuid());
            if (s == null) return;
            s.setHandle(handle);
            if (s.isLads()) {
                if (force || !Objects.equals(handle.world(), s.getSyncedWorld())) {
                    doResync = true;
                }
            } else if (s.isHelloSent() && !s.isHelloAcked()) {
                doHello = true;
            }
        }
        if (doResync) {
            resync(s);
        } else if (doHello) {
            s.sendHello();
        }
    }

    private void resync(LadsSession s) {
        s.sendHello(); // the client clears policy and presence on hello; the reply is ignored because the session is already acked
        s.sendPolicy();
        if (config.isBadgePresence()) {
            s.sendPresence(true, getAllLadsPlayerUuids(), Collections.emptyList());
        }
    }

    public void receive(LadsPlayerHandle handle, byte[] payload) {
        if (handle == null || payload == null || payload.length == 0 || payload.length > MAX_IN) {
            return; // Dropped
        }

        LadsSession session;
        synchronized (lock) {
            session = sessions.get(handle.uuid());
            if (session == null) {
                return;
            }
            session.setHandle(handle);
        }

        if (!session.checkRateLimit()) {
            if (config.isDebug() && platform.logger() != null) {
                platform.logger().warning("[LadsClientAPI] Rate limit exceeded for " + handle.name());
            }
            return;
        }

        JsonObject root;
        try {
            String jsonStr = new String(payload, StandardCharsets.UTF_8);
            JsonElement el = JsonParser.parseString(jsonStr);
            if (!el.isJsonObject()) return;
            root = el.getAsJsonObject();
        } catch (Exception | StackOverflowError e) {
            return; // Malformed dropped, never crash/kick
        }

        // Valid JSON can still carry any type in any field: read them type-checked (PROTOCOL.md: malformed = dropped, never a crash/kick).
        String type = text(root, "t");
        if (type == null) return;

        if ("hello".equalsIgnoreCase(type)) {
            handleHello(session, handle, root);
        } else if ("ack".equalsIgnoreCase(type)) {
            handleAck(session, handle, root);
        }
    }

    private void handleHello(LadsSession session, LadsPlayerHandle handle, JsonObject root) {
        String kickMsg = null;
        boolean sendFull = false;
        List<Consumer<LadsPlayer>> listeners = null;
        List<UUID> allLads = null;
        List<LadsSession> otherSessions = null;

        synchronized (lock) {
            if (session.isHelloAcked()) return;

            // PROTOCOL.md section 5: a server receiving a `v` it does not know treats the player as non-Lads.
            int v = whole(root, "v", 0);
            if (v < 1 || v > LadsCore.PROTOCOL) return;

            // Both strings reach commands, PlaceholderAPI and the kick text: only plain version text gets through.
            String ladsVersion = VersionUtil.clean(text(root, "lads"));
            String mcVersion = VersionUtil.clean(text(root, "mc"));

            // min-client-version check
            if (!config.getMinClientVersion().isBlank() && VersionUtil.isOlder(ladsVersion, config.getMinClientVersion())) {
                kickMsg = config.getMinClientVersionMessage()
                        .replace("%client%", ladsVersion)
                        .replace("%min%", config.getMinClientVersion());
            } else {
                session.setHelloAcked(true);
                session.setLads(true, ladsVersion, mcVersion);
                sendFull = true;
                listeners = new ArrayList<>(readyListeners);
                if (config.isBadgePresence()) {
                    allLads = getAllLadsPlayerUuids();
                    otherSessions = new ArrayList<>(sessions.values());
                }
            }
        }

        if (kickMsg != null) {
            handle.kick(kickMsg);
            return;
        }

        if (sendFull) {
            for (Consumer<LadsPlayer> listener : listeners) {
                try {
                    listener.accept(session.getLadsPlayer());
                } catch (Throwable t) {
                    if (platform.logger() != null) {
                        platform.logger().warning("[LadsClientAPI] Error in ready listener: " + t.getMessage());
                    }
                }
            }

            // Send policy
            session.sendPolicy();

            // Send presence full if badge.presence
            if (allLads != null) {
                session.sendPresence(true, allLads, Collections.emptyList());

                // Send presence add to other players
                for (LadsSession other : otherSessions) {
                    if (other != session && other.isLads()) {
                        other.sendPresence(false, List.of(handle.uuid()), Collections.emptyList());
                    }
                }
            }
        }
    }

    private void handleAck(LadsSession session, LadsPlayerHandle handle, JsonObject root) {
        if (!session.isLads()) return;

        if (config.isDebug() && platform.logger() != null) {
            platform.logger().info("[LadsClientAPI] Player " + handle.name() + " ack rev=" + whole(root, "rev", 0)
                    + ", ignored=" + shortJson(root.get("ignored")) + ", refused=" + shortJson(root.get("refused")));
        }
    }

    /** A string field, or null when it is missing or any other JSON type. */
    private static String text(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isString() ? e.getAsString() : null;
    }

    /** An int field, or {@code fallback} when it is missing, not a number or not a whole number in int range. */
    private static int whole(JsonObject o, String key, int fallback) {
        JsonElement e = o.get(key);
        if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber()) return fallback;
        try {
            return e.getAsBigDecimal().intValueExact();
        } catch (ArithmeticException | NumberFormatException ex) {
            return fallback;
        }
    }

    /** Client JSON goes into the log only in short form: a hostile client controls its length. */
    private static String shortJson(JsonElement e) {
        String s = String.valueOf(e);
        return s.length() > 200 ? s.substring(0, 200) + "..." : s;
    }

    public List<UUID> getAllLadsPlayerUuids() {
        List<UUID> list = new ArrayList<>();
        synchronized (lock) {
            for (LadsSession s : sessions.values()) {
                if (s.isLads()) {
                    list.add(s.getHandle().uuid());
                }
            }
        }
        return list;
    }

    public void resendAllPolicies() {
        List<LadsSession> ladsSessions = new ArrayList<>();
        synchronized (lock) {
            for (LadsSession s : sessions.values()) {
                if (s.isLads()) {
                    ladsSessions.add(s);
                }
            }
        }
        for (LadsSession s : ladsSessions) {
            s.sendPolicy();
        }
    }

    public void stop() {
        List<LadsSession> ladsSessions = new ArrayList<>();
        synchronized (lock) {
            for (LadsSession s : sessions.values()) {
                if (s.isLads()) {
                    ladsSessions.add(s);
                }
            }
            sessions.clear();
        }
        for (LadsSession s : ladsSessions) {
            s.sendReset();
        }
    }
}
