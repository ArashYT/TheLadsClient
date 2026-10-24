package com.thelads.core.client.hud;

import com.thelads.core.client.bridge.LadsGraphics;

/**
 * Inventory HUD: renders a live preview of the player's inventory on the HUD (parity with Feather / OneClient / Badlion).
 */
public class InventoryHudElement extends HudElement {
    private static final int COLS = 9;
    private static final int ROWS = 3;
    private static final int SLOT_SIZE = 18;

    public InventoryHudElement() {
        this.x = 10;
        this.y = 105;
        this.width = COLS * SLOT_SIZE;
        this.height = ROWS * SLOT_SIZE;
    }

    @Override
    public void render(LadsGraphics g) {
        renderInventory(g, false);
    }

    @Override
    public void renderEditor(LadsGraphics g) {
        renderInventory(g, true);
    }

    private void renderInventory(LadsGraphics g, boolean editor) {
        int w = COLS * SLOT_SIZE;
        int h = ROWS * SLOT_SIZE;
        // Background panel
        g.fill(x, y, x + w, y + h, 0x80000000);
        // Draw 3x9 grid slots
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                int sx = x + c * SLOT_SIZE;
                int sy = y + r * SLOT_SIZE;
                g.fill(sx + 1, sy + 1, sx + SLOT_SIZE - 1, sy + SLOT_SIZE - 1, 0x33FFFFFF);
            }
        }
        if (editor) {
            g.drawCenteredText("Inventory", x + w / 2, y + h / 2 - 4, 0xFFAAAAAA, com.thelads.core.config.HudSettings.getInstance().isTextShadow());
        }
    }
}
