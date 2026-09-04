package com.thelads.core.client.bridge;

import java.util.Collections;
import java.util.List;

/**
 * Universal game state bridge allowing HUD elements in :common to query game state
 * without direct compile-time coupling to version-specific Minecraft classes.
 */
public interface LadsGameBridge {
    class Holder {
        private static volatile LadsGameBridge instance = new DefaultGameBridge();
    }

    static LadsGameBridge get() {
        return Holder.instance;
    }

    static void set(LadsGameBridge bridge) {
        if (bridge != null) {
            Holder.instance = bridge;
        }
    }

    boolean isIngame();

    int getFps();

    boolean hasPlayer();

    int getPlayerX();

    int getPlayerY();

    int getPlayerZ();

    String getBiomeName();

    String getPlayerDirection();

    long getDayCount();

    String getGameTime();

    float getHealth();

    float getMaxHealth();

    int getFoodLevel();

    int getXpLevel();

    float getXpProgress();

    int getPing();

    double getSpeed();

    default long getUsedMemoryMb() {
        Runtime runtime = Runtime.getRuntime();
        return (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
    }

    default long getMaxMemoryMb() {
        return Runtime.getRuntime().maxMemory() / (1024 * 1024);
    }

    boolean isKeyDown(String keyName);

    default List<String> getActivePotionEffects() {
        return Collections.emptyList();
    }

    default List<String> getActiveResourcePacks() {
        return Collections.emptyList();
    }

    boolean isHudHidden();
}
