package com.thelads.core.features.alwayson.betterstatisticscreen;

import com.thelads.core.features.alwayson.betterstatisticscreen.BetterStatsConfig;
import com.thelads.core.features.alwayson.betterstatisticscreen.client.BetterStatsClient;
import com.thelads.core.features.alwayson.betterstatisticscreen.server.BetterStatsServer;
import java.util.Objects;
import java.util.Properties;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BetterStats {
    public static final String MOD_ID = "betterstats";
    public static final Logger LOGGER = LoggerFactory.getLogger("betterstats");
    private static final Properties PROPERTIES = new Properties();
    private static final BetterStatsConfig CONFIG = new BetterStatsConfig();
    private static BetterStats INSTANCE;
    private final String modName;
    private final String modVersion;

    protected BetterStats() {
        if (!(this instanceof BetterStatsClient) && !(this instanceof BetterStatsServer)) {
            throw new IllegalStateException("Unexpected subclass " + String.valueOf(this.getClass()));
        }
        if (INSTANCE != null) {
            throw new IllegalStateException("Mod already initialized - betterstats");
        }
        INSTANCE = this;
        LOGGER.info("Initializing 'betterstats' as '" + this.getClass().getSimpleName() + "'.");
        try {
            PROPERTIES.load(BetterStats.class.getResourceAsStream("/betterstats.properties"));
        }
        catch (Exception e) {
            throw new RuntimeException("Failed to load 'betterstats.properties'", e);
        }
        this.modName = Objects.requireNonNull(PROPERTIES.getProperty("mod.name"));
        this.modVersion = Objects.requireNonNull(PROPERTIES.getProperty("mod.version"));
        CONFIG.loadFromFile();
    }

    public static final BetterStats getInstance() {
        return INSTANCE;
    }

    public static final BetterStatsConfig getConfig() {
        return CONFIG;
    }

    @Deprecated(forRemoval=true)
    public static final String getProperty(@NotNull String key) throws IllegalStateException {
        if (INSTANCE == null) {
            throw new IllegalStateException("betterstats is not initialized yet.");
        }
        return Objects.requireNonNull(PROPERTIES.getProperty(key), "Attempt to access missing property '" + key + "' for the mod 'betterstats'.");
    }

    public final String getModName() {
        return this.modName;
    }

    public final String getModVersion() {
        return this.modVersion;
    }
}
