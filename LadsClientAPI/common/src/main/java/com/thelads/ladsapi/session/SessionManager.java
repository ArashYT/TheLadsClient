package com.thelads.ladsapi.session;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.thelads.ladsapi.LadsCore;
import com.thelads.ladsapi.api.LadsClientAPI;
import com.thelads.ladsapi.api.Dialog;
import com.thelads.ladsapi.api.LadsInput;
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
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

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
    private final List<Consumer<LadsPlayer>> verifiedListeners = new ArrayList<>();
    private final List<BiConsumer<LadsPlayer, String>> spectateListeners = new ArrayList<>();
    private final Object lock = new Object();
    /** Names this server in every hello and in the relay's token; new on every start, it says nothing about the server. */
    private final String serverId = LadsSession.randomId();
    private final KeptSettings kept;
    private AttestVerifier verifier;
    private List<String> verifierKeys;
    private LongSupplier clock = () -> System.currentTimeMillis() / 1000;
    private LongSupplier millis = System::currentTimeMillis;
    private final List<LadsInput.ActionListener> actionListeners = new ArrayList<>();
    private final List<LadsInput.DialogListener> dialogListeners = new ArrayList<>();
    private final List<LadsInput.Listener> inputListeners = new ArrayList<>();

    public SessionManager(LadsPlatform platform, LadsConfig config, LadsRules rules) {
        this.platform = platform;
        this.config = config;
        this.rules = rules;
        this.kept = new KeptSettings(platform.dataFolder(), rules, platform.logger());
    }

    /** What admins saved per player (players.yml). */
    public KeptSettings kept() { return kept; }

    public LadsPlatform getPlatform() { return platform; }
    public LadsConfig getConfig() { return config; }
    public LadsRules getRules() { return rules; }
    public Map<UUID, LadsSession> getSessions() { return sessions; }

    public void addReadyListener(Consumer<LadsPlayer> listener) {
        synchronized (lock) {
            readyListeners.add(listener);
        }
    }

    /** Called once per session, when the client's relay token checked out (PROTOCOL.md section 6). */
    public void addVerifiedListener(Consumer<LadsPlayer> listener) {
        synchronized (lock) {
            verifiedListeners.add(listener);
        }
    }

    /** Called with "next" or "prev" when a spectating client asks for another target (PROTOCOL.md section 7). */
    public void addSpectateListener(BiConsumer<LadsPlayer, String> listener) {
        synchronized (lock) {
            spectateListeners.add(listener);
        }
    }

    /** Called when a player presses or releases a server action (PROTOCOL.md section 20). */
    public void addActionListener(LadsInput.ActionListener listener) {
        synchronized (lock) {
            actionListeners.add(listener);
        }
    }

    /** Called with every dialog answer: a button id, "dismiss" or "timeout" (section 21). */
    public void addDialogListener(LadsInput.DialogListener listener) {
        synchronized (lock) {
            dialogListeners.add(listener);
        }
    }

    /** Called when a client reports its input device (section 9). */
    public void addInputListener(LadsInput.Listener listener) {
        synchronized (lock) {
            inputListeners.add(listener);
        }
    }

    public String getServerId() { return serverId; }

    /** Unix milliseconds for the minigame timers and rate limits; tests set it. */
    public long nowMs() { return millis.getAsLong(); }

    public void setMillisClock(LongSupplier millis) { this.millis = millis; }

    /** Unix seconds; tests set it. */
    public void setClock(LongSupplier clock) { this.clock = clock; }

    /** The token check, or null when verify-client is off or no key loads (then no hello asks for proof and nobody is verified). */
    public AttestVerifier attest() {
        synchronized (lock) {
            if (config == null || !config.isVerifyClient()) return null;
            List<String> keys = config.getRelayPublicKeys().isEmpty() ? AttestVerifier.RELAY_KEYS : config.getRelayPublicKeys();
            if (verifier == null || !keys.equals(verifierKeys)) { // first use, or config.yml was reloaded with other keys
                verifier = new AttestVerifier(keys);
                verifierKeys = List.copyOf(keys);
            }
            return verifier.ready() ? verifier : null;
        }
    }

    /** Whether this player's client proved itself through the Lads relay. Old clients and players without the client: false. */
    @Override
    public boolean verified(UUID uuid) {
        LadsSession s = uuid == null ? null : sessions.get(uuid);
        return s != null && s.isLads() && s.isVerified();
    }

    @Override
    public boolean perks(UUID uuid) {
        boolean strict = config != null && config.isPerksNeedVerified() && attest() != null;
        return strict ? verified(uuid) : player(uuid).isPresent();
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
        if (platform.isProxy() && !config.proxyRulesFor(handle.world())) {
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
        kept.touchName(uuid, handle.name());
        for (String scope : kept.scopes(uuid)) session.enterScope(scope); // kept scopes come back with every session

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
        platform.runLater(() -> kickUnless(uuid, session, false), 100);
        // ... and after verify-timeout when it did reply, but never showed the relay's token: anyone can send that reply
        platform.runLater(() -> kickUnless(uuid, session, true), Math.max(100, config.getVerifyTimeout() * 20));
    }

    private void kickUnless(UUID uuid, LadsSession session, boolean verified) {
        String kickMsg = null;
        synchronized (lock) {
            if (sessions.get(uuid) != session || !config.isRequireLadsClient()) return;
            if (!verified && !session.isLads()) {
                kickMsg = config.getRequireLadsClientMessage();
            } else if (verified && session.isLads() && !session.isVerified() && attest() != null) {
                kickMsg = config.getRequireVerifiedMessage();
            }
        }
        if (kickMsg != null) {
            session.getHandle().kick(kickMsg);
        }
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
        if (!refresh(handle, platform.isProxy()) || platform.isProxy()) return;
        // Clients up to 1.14.3 read our messages on the network thread but the Respawn on the game thread, so the Respawn's reset
        // can land after this policy and wipe it for the whole stay in the world. Send it again once the world has loaded.
        java.util.UUID uuid = handle.uuid();
        String world = handle.world();
        for (int ticks : WORLD_RESEND_TICKS) platform.runLater(() -> resendAfterWorldChange(uuid, world), ticks);
    }

    private static final int[] WORLD_RESEND_TICKS = {10, 60};

    /** No hello (the client's reply budget is small): the session survives the client's reset, so the policy alone applies. */
    private void resendAfterWorldChange(java.util.UUID uuid, String world) {
        LadsSession s;
        synchronized (lock) {
            s = sessions.get(uuid);
            if (s == null || !s.isLads() || !Objects.equals(world, s.getSyncedWorld())) return;
        }
        resync(s, false);
    }

    /**
     * Velocity: the player is now on the backend {@code handle.world()}. A backend the proxy rules for gets a fresh session (hello,
     * policy), or a resync when the player came from another ruled backend; any other backend gets none, so the proxy's policy is
     * dropped and that backend's own plugin takes over (the client already reset on the switch).
     */
    public void proxyServerChanged(LadsPlayerHandle handle) {
        if (handle == null || !platform.isProxy()) return;
        if (config.proxyRulesFor(handle.world())) {
            boolean has;
            synchronized (lock) {
                has = sessions.containsKey(handle.uuid());
            }
            if (has) worldChanged(handle); else join(handle);
        } else {
            quit(handle.uuid());
        }
    }

    /** Game mode changed: the client kept its session, so a new policy is enough, and only when rules.yml has per-game-mode rules. */
    public void gameModeChanged(LadsPlayerHandle handle) {
        if (handle == null || rules.getGameModes().isEmpty()) return;
        LadsSession s;
        synchronized (lock) {
            s = sessions.get(handle.uuid());
            if (s == null || !s.isLads()) return;
            s.setHandle(handle);
        }
        s.sendPolicy();
    }

    /** Fabric/Forge build a new player object on respawn: keep the handle current and resync only if the world differs. */
    public void respawned(LadsPlayerHandle handle) {
        refresh(handle, false);
    }

    /** @return whether hello and policy went out again */
    private boolean refresh(LadsPlayerHandle handle, boolean force) {
        if (handle == null) return false;
        LadsSession s;
        boolean doResync = false;
        boolean doHello = false;
        synchronized (lock) {
            s = sessions.get(handle.uuid());
            if (s == null) return false;
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
            resync(s, true);
        } else if (doHello) {
            s.sendHello();
        }
        return doResync;
    }

    private void resync(LadsSession s, boolean hello) {
        if (hello) s.sendHello(); // the client clears policy and presence on hello; the reply is ignored because the session is already acked
        s.sendPolicy();
        if (config.isBadgePresence()) {
            s.sendPresence(true, getAllLadsPlayerUuids(), Collections.emptyList());
        }
        s.resendSpectate();
        s.resendFeatures();
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

        long now = nowMs();
        if (!session.checkFlood(now)) {
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

        // The feature messages have limits of their own (sections 9, 20, 21); everything else shares the 8 per second.
        switch (type.toLowerCase(java.util.Locale.ROOT)) {
            case "input" -> handleInput(session, root, now);
            case "action" -> handleAction(session, root, now);
            case "dialog" -> handleDialog(session, root, now);
            default -> {
                if (!session.checkRateLimit(now)) {
                    if (config.isDebug() && platform.logger() != null) {
                        platform.logger().warning("[LadsClientAPI] Rate limit exceeded for " + handle.name());
                    }
                    return;
                }
                if ("hello".equalsIgnoreCase(type)) {
                    handleHello(session, handle, root);
                } else if ("ack".equalsIgnoreCase(type)) {
                    handleAck(session, handle, root);
                } else if ("spectate".equalsIgnoreCase(type)) {
                    handleSpectate(session, root);
                }
            }
        }
    }

    private void demoLog(LadsSession session, String what) {
        if (session.isDemo() && platform.logger() != null) {
            platform.logger().info("[LadsClientAPI] " + session.getHandle().name() + " sent " + what);
        }
    }

    /** Section 9: the device class, only when the last hello asked for it. */
    private void handleInput(LadsSession session, JsonObject root, long now) {
        if (!session.isLads() || !session.takeInput(now)) return;
        String d = text(root, "device");
        LadsInput.Device device = "kbm".equals(d) ? LadsInput.Device.KBM : "gamepad".equals(d) ? LadsInput.Device.GAMEPAD : null;
        if (device == null) return;
        LadsInput.Pad pad = LadsInput.Pad.UNKNOWN;
        if (device == LadsInput.Device.GAMEPAD) {
            String p = text(root, "pad");
            pad = "xbox".equals(p) ? LadsInput.Pad.XBOX : "playstation".equals(p) ? LadsInput.Pad.PLAYSTATION
                    : "switch".equals(p) ? LadsInput.Pad.SWITCH : LadsInput.Pad.GENERIC;
        }
        session.setDevice(device, pad);
        demoLog(session, "input device=" + device + " pad=" + pad);
        List<LadsInput.Listener> listeners;
        synchronized (lock) {
            listeners = new ArrayList<>(inputListeners);
        }
        for (LadsInput.Listener l : listeners) {
            try {
                l.onInput(session.getLadsPlayer(), device, pad);
            } catch (Throwable t) {
                warn("input", t);
            }
        }
    }

    /** Section 20: a press or release of an action the server offered, at most 20 per second. */
    private void handleAction(LadsSession session, JsonObject root, long now) {
        if (!session.isLads() || !session.hasCap("action") || !session.checkActionRate(now)) return;
        String id = text(root, "id");
        JsonElement down = root.get("down");
        if (id == null || down == null || !down.isJsonPrimitive() || !down.getAsJsonPrimitive().isBoolean()) return;
        if (!session.features().hasAction(id)) return;
        boolean isDown = down.getAsBoolean();
        demoLog(session, "action " + id + " down=" + isDown);
        List<LadsInput.ActionListener> listeners;
        synchronized (lock) {
            listeners = new ArrayList<>(actionListeners);
        }
        for (LadsInput.ActionListener l : listeners) {
            try {
                l.onAction(session.getLadsPlayer(), id, isDown);
            } catch (Throwable t) {
                warn("action", t);
            }
        }
    }

    /** Section 21: the answer to a dialog this session has open, at most 4 per second. */
    private void handleDialog(LadsSession session, JsonObject root, long now) {
        if (!session.isLads() || !session.hasCap("dialog") || !session.checkDialogRate(now)) return;
        String id = text(root, "id");
        String choice = text(root, "choice");
        if (id == null || choice == null) return;
        Dialog.Callback cb = session.features().takeAnswer(id, choice);
        if (cb == null) return;
        demoLog(session, "dialog " + id + " choice=" + choice);
        dispatchDialog(session, id, choice, cb);
    }

    /** Runs a dialog's callback and the dialog listeners (also for the plugin's own "timeout"). */
    public void dispatchDialog(LadsSession session, String id, String choice, Dialog.Callback cb) {
        List<LadsInput.DialogListener> listeners;
        synchronized (lock) {
            listeners = new ArrayList<>(dialogListeners);
        }
        try {
            if (cb != null) cb.onChoice(session.getLadsPlayer(), choice);
        } catch (Throwable t) {
            warn("dialog", t);
        }
        for (LadsInput.DialogListener l : listeners) {
            try {
                l.onDialog(session.getLadsPlayer(), id, choice);
            } catch (Throwable t) {
                warn("dialog", t);
            }
        }
    }

    private void warn(String what, Throwable t) {
        if (platform.logger() != null) platform.logger().warning("[LadsClientAPI] Error in " + what + " listener: " + t.getMessage());
    }

    /**
     * PROTOCOL.md section 7: the player pressed the key for the next or previous target. Only from a client this server told it
     * may ask; the game plugin that hears it picks the target, so the client names nobody.
     */
    private void handleSpectate(LadsSession session, JsonObject root) {
        String act = text(root, "act");
        if (!session.isLads() || !session.isSpectateSwitch() || (!"next".equals(act) && !"prev".equals(act))) return;
        List<BiConsumer<LadsPlayer, String>> listeners;
        synchronized (lock) {
            listeners = new ArrayList<>(spectateListeners);
        }
        for (BiConsumer<LadsPlayer, String> listener : listeners) {
            try {
                listener.accept(session.getLadsPlayer(), act);
            } catch (Throwable t) {
                if (platform.logger() != null) {
                    platform.logger().warning("[LadsClientAPI] Error in spectate listener: " + t.getMessage());
                }
            }
        }
    }

    private void handleHello(LadsSession session, LadsPlayerHandle handle, JsonObject root) {
        String kickMsg = null;
        boolean sendFull = false;
        List<Consumer<LadsPlayer>> listeners = null;
        List<UUID> allLads = null;
        List<LadsSession> otherSessions = null;
        List<Consumer<LadsPlayer>> verifiedNow = null;

        synchronized (lock) {
            // PROTOCOL.md section 5: a server receiving a `v` it does not know treats the player as non-Lads.
            int v = whole(root, "v", 0);
            if (v < 1 || v > LadsCore.PROTOCOL) return;

            if (!session.isHelloAcked()) {
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
                    session.setClientCaps(clientCaps(root));
                    sendFull = true;
                    listeners = new ArrayList<>(readyListeners);
                    if (config.isBadgePresence()) {
                        allLads = getAllLadsPlayerUuids();
                        otherSessions = new ArrayList<>(sessions.values());
                    }
                }
            }

            // PROTOCOL.md section 6: the relay's token comes in a second hello (the first one cannot wait for the relay).
            if (session.isLads() && checkToken(session, handle, text(root, "token"))) {
                verifiedNow = new ArrayList<>(verifiedListeners);
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

        if (verifiedNow != null) {
            for (Consumer<LadsPlayer> listener : verifiedNow) {
                try {
                    listener.accept(session.getLadsPlayer());
                } catch (Throwable t) {
                    if (platform.logger() != null) {
                        platform.logger().warning("[LadsClientAPI] Error in verified listener: " + t.getMessage());
                    }
                }
            }
        }
    }

    /** Caller holds the lock. True when this token just made the session verified. */
    private boolean checkToken(LadsSession session, LadsPlayerHandle handle, String token) {
        AttestVerifier attest = attest();
        if (token == null || attest == null || session.isVerified() || !session.takeAttestTry()) return false;
        AttestVerifier.Result result = attest.check(token, handle.uuid(), session.getNonce(), serverId, clock.getAsLong());
        if (result == AttestVerifier.Result.OK) {
            session.setVerified(true);
            return true;
        }
        if (platform.logger() != null) {
            if (result == AttestVerifier.Result.EXPIRED) {
                platform.logger().warning("[LadsClientAPI] " + handle.name() + ": the relay's token is real but out of date. Check this server's clock.");
            } else if (config.isDebug()) {
                platform.logger().info("[LadsClientAPI] " + handle.name() + ": token refused (" + result + ")");
            }
        }
        return false;
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

    /** The hello's {@code caps} (PROTOCOL.md section 3): at most 32 short lower-case names, everything else in it is skipped. */
    private static java.util.Set<String> clientCaps(JsonObject root) {
        JsonElement e = root.get("caps");
        if (e == null || !e.isJsonArray()) return java.util.Set.of();
        java.util.Set<String> out = new java.util.HashSet<>();
        int seen = 0;
        for (JsonElement cap : e.getAsJsonArray()) {
            if (++seen > 64 || out.size() >= 32) break;
            if (!cap.isJsonPrimitive() || !cap.getAsJsonPrimitive().isString()) continue;
            String name = cap.getAsString();
            if (name.matches("[a-z0-9_-]{1,24}")) out.add(name);
        }
        return java.util.Set.copyOf(out);
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

    /** After players.yml was read again: sessions enter the scopes kept for their player (nothing is ended). */
    public void reapplyKeptScopes() {
        for (LadsSession s : sessions.values()) {
            for (String scope : kept.scopes(s.getHandle().uuid())) s.enterScope(scope);
        }
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
            try {
                s.sendReset();
            } catch (RuntimeException e) {
                // Paper refuses plugin messages once the plugin is disabled: one player must not stop the rest, or the plugin's disable
            }
        }
    }
}
