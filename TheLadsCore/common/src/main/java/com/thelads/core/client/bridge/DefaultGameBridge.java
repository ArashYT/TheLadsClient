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
    public int getRenderedChunks() {
        return 150;
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
    public float getPitch() {
        return 0.0f;
    }

    public int clientTicks = 0;

    @Override
    public int getClientTicks() {
        return clientTicks;
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

    @Override
    public DefenseEnchants getDefenseEnchants() {
        return new DefenseEnchants(16, 0, 0, 0, 4);
    }

    @Override
    public List<CooldownEntry> getActiveCooldowns() {
        return List.of(new CooldownEntry("Ender Pearl", 0.65f, 9.8f));
    }

    @Override
    public double getTps() {
        return 20.0;
    }

    @Override
    public double getCpuLoad() {
        return 24.0;
    }

    @Override
    public float getToughness() {
        return 8.0f;
    }

    @Override
    public int getPotionCount() {
        return 6;
    }

    @Override
    public int getSoupCount() {
        return 2;
    }

    @Override
    public String getItemCountText(int selection) {
        return switch (selection) {
            case 1 -> "Arrows: 64";
            case 2 -> "Totems: 2";
            case 3 -> "Rockets: 64";
            case 4 -> "Golden Apples: 12";
            case 5 -> "Ender Pearls: 16";
            case 6 -> "Potions: 6";
            case 7 -> "Soups: 4";
            default -> "Held item: 64";
        };
    }
}
