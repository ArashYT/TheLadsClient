package com.thelads.ladsapi;

import com.thelads.ladsapi.api.LadsClientAPI;
import com.thelads.ladsapi.command.LadsCommandHandler;
import com.thelads.ladsapi.config.LadsConfig;
import com.thelads.ladsapi.platform.LadsPlatform;
import com.thelads.ladsapi.platform.LadsPlayerHandle;
import com.thelads.ladsapi.rules.LadsRules;
import com.thelads.ladsapi.schema.LadsSchema;
import com.thelads.ladsapi.session.SessionManager;
import java.util.List;
import java.util.UUID;

/**
 * The shared server core: config, rules, per-player sessions, the lads:api protocol (artifacts/ladsapi/PROTOCOL.md).
 * Platforms forward their events here and nothing else. FROZEN INTERFACE (phase 0); lane SRV-A implements the bodies.
 */
public final class LadsCore {
    public static final String CHANNEL = "lads:api";
    public static final int PROTOCOL = 1;

    private static volatile LadsCore instance;
    private final LadsPlatform platform;
    private final LadsConfig config;
    private final LadsSchema schema;
    private final LadsRules rules;
    private final SessionManager sessionManager;

    private LadsCore(LadsPlatform platform) {
        this.platform = platform;
        this.config = LadsConfig.load(platform.dataFolder(), platform.logger());
        this.schema = LadsSchema.loadDefault();
        this.rules = LadsRules.load(platform.dataFolder(), this.schema, platform.logger());
        this.sessionManager = new SessionManager(platform, this.config, this.rules);
        LadsClientAPI.Holder.set(this.sessionManager);
    }

    /**
     * Starts the core once. A hybrid server (Arclight/Mohist) may call this from two entrypoints: the second call returns null and
     * that entrypoint must stay off.
     */
    public static synchronized LadsCore start(LadsPlatform platform) {
        if (instance != null) return null;
        instance = new LadsCore(platform);
        return instance;
    }

    public static LadsCore get() {
        return instance;
    }

    public LadsPlatform platform() {
        return platform;
    }

    public LadsConfig config() {
        return config;
    }

    public LadsRules rules() {
        return rules;
    }

    public LadsSchema schema() {
        return schema;
    }

    public SessionManager sessionManager() {
        return sessionManager;
    }

    public void stop() {
        if (sessionManager != null) {
            sessionManager.stop();
        }
        LadsClientAPI.Holder.set(null);
        instance = null;
    }

    /** Player fully joined (Paper PlayerJoinEvent, Fabric JOIN, Forge PlayerLoggedIn, Velocity ServerPostConnectEvent). */
    public void join(LadsPlayerHandle player) {
        if (sessionManager != null) {
            sessionManager.join(player);
        }
    }

    public void quit(UUID player) {
        if (sessionManager != null) {
            sessionManager.quit(player);
        }
    }

    /** World changed: per-world rules may differ, so a new policy goes out. */
    public void worldChanged(LadsPlayerHandle player) {
        if (sessionManager != null) {
            sessionManager.worldChanged(player);
        }
    }

    /** One lads:api payload from a player. */
    public void receive(LadsPlayerHandle player, byte[] payload) {
        if (sessionManager != null) {
            sessionManager.receive(player, payload);
        }
    }

    /** /ladsapi <args>: returns the lines to show the sender. `admin` = has ladsapi.admin. */
    public List<String> command(String[] args, boolean admin) {
        return LadsCommandHandler.handleCommand(this, args, admin);
    }

    public void reload() {
        if (config != null) {
            config.readFrom(platform.dataFolder().resolve("config.yml"), platform.logger());
        }
        if (rules != null) {
            rules.readFrom(platform.dataFolder().resolve("rules.yml"), platform.logger());
        }
        if (sessionManager != null) {
            sessionManager.resendAllPolicies();
        }
    }
}
