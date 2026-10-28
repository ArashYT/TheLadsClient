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
    /** Fabric/Forge create a new ServerPlayer on respawn, so the handle is replaced when the platform hands over a fresh one. */
    private volatile LadsPlayerHandle handle;
    private final SessionManager manager;
    private final RateLimiter rateLimiter = new RateLimiter(8);
    private final Map<String, ModuleRule> apiOverrides = new LinkedHashMap<>();
    private final LadsPlayerImpl ladsPlayer;

    private int rev = 0;
    private boolean lads = false;
    private String ladsVersion = "";
    private String mcVersion = "";
    private boolean helloSent = false;
    private boolean helloResent = false;
    private boolean helloAcked = false;
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
        JsonArray caps = new JsonArray();
        caps.add("policy");
        caps.add("presence");
        caps.add("reset");
        obj.add("caps", caps);

        handle.send(obj.toString().getBytes(StandardCharsets.UTF_8));
        this.helloSent = true;
    }

    public void sendPolicy() {
        rev++;
        syncedWorld = handle.world();
        String json = PolicyBuilder.buildPolicyJson(
                manager.getRules(),
                manager.getConfig(),
                handle,
                apiOverrides,
                rev
        );
        handle.send(json.getBytes(StandardCharsets.UTF_8));
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
        apiOverrides.computeIfAbsent(module, k -> new ModuleRule()).setState(state);
    }

    public void setApiOverrideOptionLimit(String module, String option, double min, double max) {
        ModuleRule mr = apiOverrides.computeIfAbsent(module, k -> new ModuleRule());
        RuleOption opt = mr.getOptions().computeIfAbsent(option, k -> new RuleOption());
        if (!Double.isNaN(min)) opt.setMin(min);
        if (!Double.isNaN(max)) opt.setMax(max);
    }

    public void setApiOverrideOptionForce(String module, String option, Object value) {
        ModuleRule mr = apiOverrides.computeIfAbsent(module, k -> new ModuleRule());
        RuleOption opt = mr.getOptions().computeIfAbsent(option, k -> new RuleOption());
        opt.setValue(value);
        opt.setLock(true);
    }

    public void clearApiOverrides(String module) {
        if (module == null) {
            apiOverrides.clear();
        } else {
            apiOverrides.remove(module);
        }
    }
}
