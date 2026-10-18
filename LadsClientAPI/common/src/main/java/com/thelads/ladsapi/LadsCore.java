package com.thelads.ladsapi;

import com.thelads.ladsapi.platform.LadsPlatform;
import com.thelads.ladsapi.platform.LadsPlayerHandle;
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

    private LadsCore(LadsPlatform platform) {
        this.platform = platform;
    }

    /**
     * Starts the core once. A hybrid server (Arclight/Mohist) may call this from two entrypoints: the second call returns null and
     * that entrypoint must stay off.
     */
    public static synchronized LadsCore start(LadsPlatform platform) {
        if (instance != null) return null;
        instance = new LadsCore(platform);
        // lane SRV-A: load config.yml + rules.yml (generate from the embedded ladsapi-schema.json), start sessions
        return instance;
    }

    public static LadsCore get() {
        return instance;
    }

    public LadsPlatform platform() {
        return platform;
    }

    public void stop() {
        // lane SRV-A: send reset to every Lads player, drop sessions
        instance = null;
    }

    /** Player fully joined (Paper PlayerJoinEvent, Fabric JOIN, Forge PlayerLoggedIn, Velocity ServerPostConnectEvent). */
    public void join(LadsPlayerHandle player) {
        // lane SRV-A: schedule hello at +1 tick, resend at +60 ticks if no reply; proxy: send reset first
    }

    public void quit(UUID player) {
        // lane SRV-A
    }

    /** World changed: per-world rules may differ, so a new policy goes out. */
    public void worldChanged(LadsPlayerHandle player) {
        // lane SRV-A
    }

    /** One lads:api payload from a player. */
    public void receive(LadsPlayerHandle player, byte[] payload) {
        // lane SRV-A
    }

    /** /ladsapi <args>: returns the lines to show the sender. `admin` = has ladsapi.admin. */
    public List<String> command(String[] args, boolean admin) {
        return List.of(); // lane SRV-A
    }

    public void reload() {
        // lane SRV-A: reread files, resend policies
    }
}
