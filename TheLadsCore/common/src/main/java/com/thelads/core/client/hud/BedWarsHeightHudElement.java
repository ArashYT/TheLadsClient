package com.thelads.core.client.hud;

import com.thelads.core.client.HypixelSafetyManager;
import com.thelads.core.client.bridge.LadsGraphics;
import com.thelads.core.client.hypixel.BedWarsHeight;
import com.thelads.core.client.hypixel.HypixelMods;

/**
 * Bed Wars Height Limit Indicator HUD.
 * Renders "Height: <blocks left>" turning yellow and red as the player nears the map build limit.
 */
public class BedWarsHeightHudElement extends TextHudElement {
    public BedWarsHeightHudElement() {
        super(70);
        this.x = 200;
        this.y = 200;
        this.width = 80;
        this.height = 16;
    }

    @Override
    public boolean isAvailable() {
        if (editor) return true;
        if (!HypixelSafetyManager.isHypixel()) return false;
        String[] loc = HypixelMods.location();
        return loc != null && "BEDWARS".equalsIgnoreCase(loc[0]);
    }

    @Override
    protected String updateText(LadsGraphics graphics) {
        if (editor) {
            return "§aHeight: 25";
        }
        if (!HypixelSafetyManager.isHypixel()) return "";
        String[] loc = HypixelMods.location();
        if (loc == null || !"BEDWARS".equalsIgnoreCase(loc[0])) return "";

        String map = HypixelMods.mapName();
        int limit = BedWarsHeight.getLimit(map);
        int playerY = graphics.getGame() != null ? graphics.getGame().getPlayerY() : 0;
        int blocksLeft = Math.max(0, limit - playerY);

        String color = blocksLeft > 10 ? "§a" : (blocksLeft > 5 ? "§e" : "§c");
        return color + "Height: " + blocksLeft;
    }
}
