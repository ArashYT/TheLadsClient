package com.thelads.core.client.bridge;

import java.util.List;

/**
 * Fallback GameBridge providing safe, realistic mock data for HUD preview/editing
 * and standalone test environments where no Minecraft client is active.
 */
public class DefaultGameBridge implements LadsGameBridge {
    @Override
    public boolean isIngame() {
        return false;
    }

    @Override
    public int getFps() {
        return 144;
    }

    @Override
    public boolean hasPlayer() {
        return true;
    }

    @Override
    public int getPlayerX() {
        return 100;
    }

    @Override
    public int getPlayerY() {
        return 64;
    }

    @Override
    public int getPlayerZ() {
        return -250;
    }

    @Override
    public String getBiomeName() {
        return "Plains";
    }

    @Override
    public String getPlayerDirection() {
        return "North (Facing -Z)";
    }

    @Override
    public long getDayCount() {
        return 42;
    }

    @Override
    public String getGameTime() {
        return "12:00";
    }

    @Override
    public float getHealth() {
        return 20.0f;
    }

    @Override
    public float getMaxHealth() {
        return 20.0f;
    }

    @Override
    public int getFoodLevel() {
        return 20;
    }

    @Override
    public int getXpLevel() {
        return 30;
    }

    @Override
    public float getXpProgress() {
        return 0.75f;
    }

    @Override
    public int getPing() {
        return 24;
    }

    @Override
    public double getSpeed() {
        return 4.32;
    }

    @Override
    public boolean isKeyDown(String keyName) {
        return false;
    }

    @Override
    public List<String> getActivePotionEffects() {
        return List.of("Speed II (1:30)", "Strength I (0:45)");
    }

    @Override
    public List<String> getActiveResourcePacks() {
        return List.of("The Lads Remastered", "Vanilla");
    }

    @Override
    public boolean isHudHidden() {
        return false;
    }
}
