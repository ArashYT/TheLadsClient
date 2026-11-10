package com.thelads.ladsapi.session;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.thelads.ladsapi.LadsCore;
import com.thelads.ladsapi.api.LadsPlayerImpl;
import com.thelads.ladsapi.platform.LadsPlayerHandle;
import com.thelads.ladsapi.policy.PolicyBuilder;
import com.thelads.ladsapi.rules.ModuleRule;
import com.thelads.ladsapi.rules.RuleOption;
import com.thelads.ladsapi.util.RateLimiter;
import com.thelads.ladsapi.util.VersionUtil;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Manages protocol state and policy for one connected player handle.
 */
public final class LadsSession {
    static final int MAX_ATTEST_TRIES = 8;
    private static final java.security.SecureRandom RANDOM = new java.security.SecureRandom();
    /** Fabric/Forge create a new ServerPlayer on respawn, so the handle is replaced when the platform hands over a fresh one. */
    private volatile LadsPlayerHandle handle;
    private final SessionManager manager;
    private final RateLimiter rateLimiter = new RateLimiter(8);
    private final Map<String, ModuleRule> apiOverrides = new LinkedHashMap<>();
    /** Active scopes, oldest first: name (lower case) -> rules set through the API for it. Guarded by apiOverrides. */
    private final Map<String, Map<String, ModuleRule>> scopes = new LinkedHashMap<>();
    /** Scope name -> its newest timer: an older timer that fires finds another number and does nothing. */
    private final Map<String, Integer> scopeTimers = new java.util.HashMap<>();
    private final LadsPlayerImpl ladsPlayer;

    private volatile int rev = 0;
    private volatile boolean lads = false;
    private volatile String ladsVersion = "";
    private volatile String mcVersion = "";
    private volatile boolean helloSent = false;
    private volatile boolean helloResent = false;
    private volatile boolean helloAcked = false;
    /** In every hello of this session; the relay's token must be made for it (PROTOCOL.md section 6). */
    private final String nonce = randomId();
    private volatile boolean verified = false;
    private int attestTries = 0;
    /** World/backend the last policy was built for; a respawn into another world needs a new policy. */
    private volatile String syncedWorld = "";

    public LadsSession(LadsPlayerHandle handle, SessionManager manager) {
        this.handle = handle;
        this.manager = manager;
        this.ladsPlayer = new LadsPlayerImpl(this);
    }

    public LadsPlayerHandle getHandle() { return handle; }
    public void setHandle(LadsPlayerHandle handle) { if (handle != null) this.handle = handle; }
    public String getSyncedWorld() { return syncedWorld; }
    public SessionManager getManager() { return manager; }
    public LadsPlayerImpl getLadsPlayer() { return ladsPlayer; }
    public int getRev() { return rev; }
    public boolean isLads() { return lads; }
    public String getLadsVersion() { return ladsVersion; }
    public String getMcVersion() { return mcVersion; }
    public boolean isHelloSent() { return helloSent; }
    public boolean isHelloResent() { return helloResent; }
    public boolean isHelloAcked() { return helloAcked; }
    public Map<String, ModuleRule> getApiOverrides() { return apiOverrides; }
    public String getNonce() { return nonce; }
    /** The client showed a relay token for this session's nonce: the only proof that it is a Lads Client. */
    public boolean isVerified() { return verified; }
    public void setVerified(boolean verified) { this.verified = verified; }

    /** A hostile client may send hellos all day: only the first few tokens of a session are checked. Caller holds the manager's lock. */
    boolean takeAttestTry() {
        return attestTries++ < MAX_ATTEST_TRIES;
    }

    /** 32 hex characters from a secure random source: a hello nonce or a server id. */
    static String randomId() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        StringBuilder out = new StringBuilder(32);
        for (byte b : bytes) out.append(Character.forDigit((b >> 4) & 15, 16)).append(Character.forDigit(b & 15, 16));
        return out.toString();
    }

    public void setHelloSent(boolean helloSent) { this.helloSent = helloSent; }
    public void setHelloResent(boolean helloResent) { this.helloResent = helloResent; }
    public void setHelloAcked(boolean helloAcked) { this.helloAcked = helloAcked; }

    public void setLads(boolean lads, String ladsVersion, String mcVersion) {
        this.lads = lads;
        this.ladsVersion = VersionUtil.clean(ladsVersion);
        this.mcVersion = VersionUtil.clean(mcVersion);
    }

    public boolean checkRateLimit() {
        return rateLimiter.tryAcquire();
    }

    public boolean checkRateLimit(long nowMs) {
        return rateLimiter.tryAcquire(nowMs);
    }

    public void sendHello() {
        JsonObject obj = new JsonObject();
        obj.addProperty("t", "hello");
        obj.addProperty("v", LadsCore.PROTOCOL);
        String serverName = manager.getConfig() != null ? manager.getConfig().getServerName() : "Lads Server";
        if (serverName != null && serverName.length() > 32) {
            serverName = serverName.substring(0, 32);
        }
        obj.addProperty("server", serverName);
        if (manager.getConfig() != null) { // optional additions: left out when unset, as an older plugin sends
            String game = manager.getConfig().getGameName().replace("%world%", handle.world() == null ? "" : handle.world()).trim();
            if (!game.isEmpty()) obj.addProperty("game", game.length() > 32 ? game.substring(0, 32) : game);
            String hub = manager.getConfig().getHubCommand().trim();
            if (!hub.isEmpty()) obj.addProperty("hub", hub);
        }
        if (manager.attest() != null) { // optional additions: an older client ignores them and stays unverified
            obj.addProperty("nonce", nonce);
            obj.addProperty("sid", manager.getServerId());
        }
        JsonArray caps = new JsonArray();
        caps.add("policy");
        caps.add("presence");
        caps.add("reset");
        obj.add("caps", caps);

        handle.send(obj.toString().getBytes(StandardCharsets.UTF_8));
        this.helloSent = true;
    }

    /** A toast on this player's client (PROTOCOL.md "toast"). Nothing to a player without the Lads Client. */
    public void sendToast(String title, String message) {
        if (!lads) return;
        JsonObject obj = new JsonObject();
        obj.addProperty("t", "toast");
        obj.addProperty("title", title);
        obj.addProperty("message", message);
        handle.send(obj.toString().getBytes(StandardCharsets.UTF_8));
    }

    public void sendPolicy() {
        rev++;
        syncedWorld = handle.world();
        Map<String, ModuleRule> overridesCopy;
        Map<String, Map<String, ModuleRule>> scopesCopy = new LinkedHashMap<>();
        synchronized (apiOverrides) {
            overridesCopy = new LinkedHashMap<>(apiOverrides);
            for (Map.Entry<String, Map<String, ModuleRule>> e : scopes.entrySet()) {
                scopesCopy.put(e.getKey(), new LinkedHashMap<>(e.getValue()));
            }
        }
        String json = PolicyBuilder.buildPolicyJson(
                manager.getRules(),
                manager.getConfig(),
                handle,
                scopesCopy,
                overridesCopy,
                rev
        );
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > 524_288) {
            if (manager.getPlatform().logger() != null) {
                manager.getPlatform().logger().severe("[LadsClientAPI] Policy json exceeds max size of 524288 bytes (" + bytes.length + " bytes), dropping outbound policy!");
            }
            return;
        }
        handle.send(bytes);
    }

    public void sendPresence(boolean full, Collection<UUID> add, Collection<UUID> remove) {
        JsonObject obj = new JsonObject();
        obj.addProperty("t", "presence");
        obj.addProperty("full", full);

        JsonArray addArr = new JsonArray();
        if (add != null) {
            for (UUID u : add) addArr.add(u.toString());
        }
        obj.add("add", addArr);

        JsonArray remArr = new JsonArray();
        if (remove != null) {
            for (UUID u : remove) remArr.add(u.toString());
        }
        obj.add("remove", remArr);

        handle.send(obj.toString().getBytes(StandardCharsets.UTF_8));
    }

    public void sendReset() {
        handle.send("{\"t\":\"reset\"}".getBytes(StandardCharsets.UTF_8));
    }

    public void setApiOverrideModuleState(String module, String state) {
        setScopeModuleState(null, module, state);
    }

    public void setApiOverrideOptionLimit(String module, String option, double min, double max) {
        setScopeOptionLimit(null, module, option, min, max);
    }

    public void setApiOverrideOptionForce(String module, String option, Object value) {
        setScopeOptionForce(null, module, option, value);
    }

    // ---- scopes: named rule layers under the API overrides. A null scope name means the API overrides themselves. ----

    public static String scopeKey(String scope) {
        return scope == null ? "" : scope.trim().toLowerCase(java.util.Locale.ROOT);
    }

    /** Caller holds apiOverrides. Entering a scope that is already active keeps its place and its rules. */
    private Map<String, ModuleRule> layer(String scope) {
        return scope == null ? apiOverrides : scopes.computeIfAbsent(scopeKey(scope), k -> new LinkedHashMap<>());
    }

    /** Switches the scope on (rules.yml scopes.<name> plus whatever the API adds to it). False when it was on already. */
    public boolean enterScope(String scope) {
        synchronized (apiOverrides) {
            boolean fresh = !scopes.containsKey(scopeKey(scope));
            layer(scope);
            return fresh;
        }
    }

    /** Switches the scope off, with its API rules and its timer. False when it was not on. */
    public boolean endScope(String scope) {
        synchronized (apiOverrides) {
            scopeTimers.remove(scopeKey(scope));
            return scopes.remove(scopeKey(scope)) != null;
        }
    }

    public java.util.List<String> scopeNames() {
        synchronized (apiOverrides) {
            return new java.util.ArrayList<>(scopes.keySet());
        }
    }

    /** Ends the scope by itself after this many seconds and sends the policy without it; 0 or less cancels a running timer. */
    public void expireScope(String scope, long seconds) {
        String key = scopeKey(scope);
        int timer;
        synchronized (apiOverrides) {
            layer(scope);
            timer = scopeTimers.merge(key, 1, Integer::sum);
        }
        if (seconds <= 0) return;
        manager.getPlatform().runLater(() -> {
            synchronized (apiOverrides) {
                Integer newest = scopeTimers.get(key);
                if (newest == null || newest != timer) return;
                scopeTimers.remove(key);
                if (scopes.remove(key) == null) return;
            }
            if (manager.getSessions().get(handle.uuid()) == this) sendPolicy(); // not after the player left
        }, (int) Math.min(Integer.MAX_VALUE, seconds * 20));
    }

    public void setScopeModuleState(String scope, String module, String state) {
        synchronized (apiOverrides) {
            layer(scope).computeIfAbsent(module, k -> new ModuleRule()).setState(state);
        }
    }

    public void setScopeOptionLimit(String scope, String module, String option, double min, double max) {
        synchronized (apiOverrides) {
            ModuleRule mr = layer(scope).computeIfAbsent(module, k -> new ModuleRule());
            RuleOption opt = mr.getOptions().computeIfAbsent(option, k -> new RuleOption());
            if (!Double.isNaN(min)) opt.setMin(min);
            if (!Double.isNaN(max)) opt.setMax(max);
        }
    }

    public void setScopeOptionForce(String scope, String module, String option, Object value) {
        synchronized (apiOverrides) {
            ModuleRule mr = layer(scope).computeIfAbsent(module, k -> new ModuleRule());
            RuleOption opt = mr.getOptions().computeIfAbsent(option, k -> new RuleOption());
            opt.setValue(value);
            opt.setLock(true);
        }
    }

    /** Drops the scope's API rules for one module (null = all of them); the scope itself stays on. */
    public void clearScopeRules(String scope, String module) {
        synchronized (apiOverrides) {
            Map<String, ModuleRule> rules = scopes.get(scopeKey(scope));
            if (rules == null) return;
            if (module == null) rules.clear();
            else rules.remove(module);
        }
    }

    public void clearApiOverrides(String module) {
        synchronized (apiOverrides) {
            if (module == null) {
                apiOverrides.clear();
            } else {
                apiOverrides.remove(module);
            }
        }
    }
}
