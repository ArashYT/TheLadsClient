package com.thelads.core.client.hud;

import com.thelads.core.client.HypixelSafetyManager;
import com.thelads.core.client.bridge.LadsGraphics;
import com.thelads.core.config.HudSettings;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;

/**
 * Hypixel SkyBlock Dungeon map overlay rendered from the client's filled map item.
 * When in HUD editor / preview mode, renders a procedural dungeon floor layout.
 * Gated under HypixelSafetyManager.
 */
public class DungeonMapHudElement extends HudElement {
    public static final String ID = "DungeonMap";

    public DungeonMapHudElement() {
        this.width = 80;
        this.height = 88;
        this.moduleName = ID;
        useOrganizedDefaults();
    }

    @Override
    public void render(LadsGraphics g) {
        if (!shouldRender(false)) return;
        drawMapFrame(g, false);
    }

    @Override
    public void renderEditor(LadsGraphics g) {
        drawMapFrame(g, true);
    }

    private boolean shouldRender(boolean editor) {
        if (editor) return true;
        Module m = ModuleManager.getInstance().getModule(ID);
        if (m == null || !m.isEnabled()) return false;
        if (HypixelSafetyManager.isDisallowed(ID) || HypixelSafetyManager.isDisallowed("SkyBlock Suite")) return false;
        return true;
    }

    private void drawMapFrame(LadsGraphics g, boolean preview) {
        int dx = getDisplayX(g);
        int dy = getDisplayY(g);
        int w = width;
        int h = height;

        int bg = resolveBackground();
        if (bg == 0) bg = 0xB0101016;
        g.fill(dx, dy, dx + w, dy + h, bg);

        // Header bar
        g.fill(dx, dy, dx + w, dy + 10, 0xDD2A1824);
        boolean shadow = HudSettings.getInstance().isTextShadow();
        g.drawCenteredText("Dungeon Map", dx + w / 2, dy + 1, 0xFFFFFFFF, shadow);

        // Inner map area
        int mapSize = w - 4;
        int mapX = dx + 2;
        int mapY = dy + 11;
        g.drawMap(mapX, mapY, mapSize, preview);
    }

    public static void drawDungeonPreview(LadsGraphics g, int x, int y, int size) {
        g.fill(x, y, x + size, y + size, 0xFF18151E);
        int roomSize = size / 5;
        int pad = 2;
        // Draw sample dungeon rooms (5x5 grid)
        // Spawn room (green) at (2, 4)
        g.fill(x + 2 * roomSize + pad, y + 4 * roomSize + pad, x + 3 * roomSize - pad, y + 5 * roomSize - pad, 0xFF2ECC71);
        // Boss room (red) at (2, 0)
        g.fill(x + 2 * roomSize + pad, y + pad, x + 3 * roomSize - pad, y + roomSize - pad, 0xFFE74C3C);
        // Puzzle room (orange) at (1, 2)
        g.fill(x + roomSize + pad, y + 2 * roomSize + pad, x + 2 * roomSize - pad, y + 3 * roomSize - pad, 0xFFE67E22);
        // Mini-boss room (yellow) at (3, 2)
        g.fill(x + 3 * roomSize + pad, y + 2 * roomSize + pad, x + 4 * roomSize - pad, y + 3 * roomSize - pad, 0xFFF1C40F);
        // Normal combat rooms (grey/brown)
        g.fill(x + 2 * roomSize + pad, y + 3 * roomSize + pad, x + 3 * roomSize - pad, y + 4 * roomSize - pad, 0xFF7F8C8D);
        g.fill(x + 2 * roomSize + pad, y + 2 * roomSize + pad, x + 3 * roomSize - pad, y + 3 * roomSize - pad, 0xFF7F8C8D);
        g.fill(x + 2 * roomSize + pad, y + roomSize + pad, x + 3 * roomSize - pad, y + 2 * roomSize - pad, 0xFF7F8C8D);
        // Corridors
        g.fill(x + 2 * roomSize + roomSize / 2 - 1, y + roomSize, x + 2 * roomSize + roomSize / 2 + 1, y + 4 * roomSize, 0xFFBDC3C7);
        g.fill(x + roomSize, y + 2 * roomSize + roomSize / 2 - 1, x + 4 * roomSize, y + 2 * roomSize + roomSize / 2 + 1, 0xFFBDC3C7);
        // Player marker (white dot) in spawn room
        int px = x + 2 * roomSize + roomSize / 2;
        int py = y + 4 * roomSize + roomSize / 2;
        g.fill(px - 2, py - 2, px + 2, py + 2, 0xFFFFFFFF);
    }
}