package com.thelads.core.client.bridge;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

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

    /** True only for a connected native HUD player renderer. */
    default boolean hasPaperDollRenderer() { return false; }

    int getPlayerX();

    int getPlayerY();

    int getPlayerZ();

    String getBiomeName();

    /** Full namespaced biome ID, or null when unavailable. */
    default String getBiomeId() { return null; }

    String getPlayerDirection();

    /** Minecraft yaw normalized to [0, 360), with 0 = south; -1 when unavailable. */
    default float getYaw() { return -1.0f; }

    long getDayCount();

    String getGameTime();

    float getHealth();

    float getMaxHealth();

    /** Absorption health points, or -1 when unavailable. */
    default float getAbsorption() { return -1.0f; }

    int getFoodLevel();

    /** Client-known food saturation, or -1 when unavailable. */
    default float getSaturation() { return -1.0f; }

    /** An equipped item's display name and remaining/max durability; maximum <= 0 means not damageable. */
    default boolean hasMinimap() { return false; }
    default int[] minimapSize() { return new int[]{100,100}; }
    default void positionMinimap(int x,int y) {}

    default int bossBarCount() { return 0; }

    record ArmorPiece(String name, int remaining, int maximum) {}

    /** Actual equipped armor only, in native slot order. Empty means no equipment data/items. */
    default List<ArmorPiece> getArmor() { return Collections.emptyList(); }

    /** Native sidebar display text, including team decorations / formatted score values; empty strings are valid. */
    record ScoreLine(String name, String value) {
        public ScoreLine {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(value, "value");
        }
    }

    /** The actual selected sidebar objective, in native display order, excluding hidden score entries. */
    record ScoreboardSnapshot(String title, List<ScoreLine> lines) {
        public ScoreboardSnapshot {
            Objects.requireNonNull(title, "title");
            lines = List.copyOf(lines);
        }
    }

    /** Null when no sidebar objective is available. Never substitute demo data here. */
    default ScoreboardSnapshot getScoreboard() { return null; }

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
