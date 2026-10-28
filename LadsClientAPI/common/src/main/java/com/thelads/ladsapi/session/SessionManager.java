package com.thelads.ladsapi.session;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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
    private final LadsPlatform platform;
    private final LadsConfig config;
    private final LadsRules rules;
    private final Map<UUID, LadsSession> sessions = new ConcurrentHashMap<>();
    private final List<Consumer<LadsPlayer>> readyListeners = new ArrayList<>();

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
        readyListeners.add(listener);
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

        LadsSession session = new LadsSession(handle, this);
        UUID uuid = handle.uuid();
        sessions.put(uuid, session);

        // The tasks belong to this session object: a quick reconnect must not inherit the old tasks.
        // Schedule hello at +1 tick
        platform.runLater(() -> {
            if (sessions.get(uuid) == session && !session.isHelloAcked() && !session.isHelloSent()) {
                session.sendHello();
            }
        }, 1);

        // Resend hello at +60 ticks if no reply, never more
        platform.runLater(() -> {
            if (sessions.get(uuid) == session && !session.isHelloAcked() && !session.isHelloResent()) {
                session.setHelloResent(true);
                session.sendHello();
            }
        }, 60);

        // require-lads-client: kick 5 s (100 ticks) after hello if no reply
        platform.runLater(() -> {
            if (sessions.get(uuid) == session && config.isRequireLadsClient() && !session.isLads()) {
                session.getHandle().kick(config.getRequireLadsClientMessage());
            }
        }, 100);
    }

    public void quit(UUID uuid) {
        if (uuid == null) return;
        LadsSession s = sessions.remove(uuid);
        if (s != null && s.isLads() && config.isBadgePresence()) {
            // Send presence remove to remaining Lads players
            for (LadsSession other : sessions.values()) {
                if (other.isLads()) {
                    other.sendPresence(false, Collections.emptyList(), List.of(uuid));
                }
            }
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
        LadsSession s = sessions.get(handle.uuid());
        if (s == null) return;
        s.setHandle(handle);
        if (s.isLads()) {
            if (force || !Objects.equals(handle.world(), s.getSyncedWorld())) {
                resync(s);
            }
        } else if (s.isHelloSent() && !s.isHelloAcked()) {
            // Our hello may have reached the client before it reset: ask again, the first reply completes the handshake as usual.
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
        if (handle == null || payload == null || payload.length == 0 || payload.length > 32000) {
            return; // Dropped
        }

        LadsSession session = sessions.get(handle.uuid());
        if (session == null) {
            return;
        }
        session.setHandle(handle);

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
        } catch (Exception e) {
            return; // Malformed dropped, never crash/kick
        }

        if (!root.has("t")) return;
        String type = root.get("t").getAsString();

        if ("hello".equalsIgnoreCase(type)) {
            handleHello(session, handle, root);
        } else if ("ack".equalsIgnoreCase(type)) {
            handleAck(session, handle, root);
        }
    }

    private void handleHello(LadsSession session, LadsPlayerHandle handle, JsonObject root) {
        if (session.isHelloAcked()) return;

        if (!root.has("v")) return;
        int v = root.get("v").getAsInt();
        if (v < 1) return; // Non-lads player

        String ladsVersion = root.has("lads") ? root.get("lads").getAsString() : "";
        String mcVersion = root.has("mc") ? root.get("mc").getAsString() : "";

        // min-client-version check
        if (!config.getMinClientVersion().isBlank() && VersionUtil.isOlder(ladsVersion, config.getMinClientVersion())) {
            String msg = config.getMinClientVersionMessage()
                    .replace("%client%", ladsVersion)
                    .replace("%min%", config.getMinClientVersion());
            handle.kick(msg);
            return;
        }

        session.setHelloAcked(true);
        session.setLads(true, ladsVersion, mcVersion);

        for (Consumer<LadsPlayer> listener : readyListeners) {
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
        if (config.isBadgePresence()) {
            List<UUID> allLads = getAllLadsPlayerUuids();
            session.sendPresence(true, allLads, Collections.emptyList());

            // Send presence add to other players
            for (LadsSession other : sessions.values()) {
                if (other != session && other.isLads()) {
                    other.sendPresence(false, List.of(handle.uuid()), Collections.emptyList());
                }
            }
        }
    }

    private void handleAck(LadsSession session, LadsPlayerHandle handle, JsonObject root) {
        if (!session.isLads()) return;

        if (config.isDebug() && platform.logger() != null) {
            int rev = root.has("rev") ? root.get("rev").getAsInt() : 0;
            JsonElement ignored = root.get("ignored");
            JsonElement refused = root.get("refused");
            platform.logger().info("[LadsClientAPI] Player " + handle.name() + " ack rev=" + rev
                    + ", ignored=" + ignored + ", refused=" + refused);
        }
    }

    public List<UUID> getAllLadsPlayerUuids() {
        List<UUID> list = new ArrayList<>();
        for (LadsSession s : sessions.values()) {
            if (s.isLads()) {
                list.add(s.getHandle().uuid());
            }
        }
        return list;
    }

    public void resendAllPolicies() {
        for (LadsSession s : sessions.values()) {
            if (s.isLads()) {
                s.sendPolicy();
            }
        }
    }

    public void stop() {
        for (LadsSession s : sessions.values()) {
            if (s.isLads()) {
                s.sendReset();
            }
        }
        sessions.clear();
    }
}
