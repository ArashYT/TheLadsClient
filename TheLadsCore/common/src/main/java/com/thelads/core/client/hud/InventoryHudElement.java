package com.thelads.core.client.hud;

import com.thelads.core.client.bridge.LadsGraphics;

/**
 * Renders the player's main inventory (3x9 grid) on screen with items, durability bars, and stack counts (parity with Lunar / Feather / Badlion).
 */
public class InventoryHudElement extends HudElement {
    public static final String ID = "InventoryHUD";
    private static final int COLS = 9;
    private static final int ROWS = 3;
    private static final int SLOT_SIZE = 18;
    private static final int PADDING = 3;

    public InventoryHudElement() {
        this.width = COLS * SLOT_SIZE + PADDING * 2;
        this.height = ROWS * SLOT_SIZE + PADDING * 2;
    }

    public static int getElementWidth() {
        return COLS * SLOT_SIZE + PADDING * 2;
    }

    public static int getElementHeight() {
        return ROWS * SLOT_SIZE + PADDING * 2;
    }

    @Override
    public void render(LadsGraphics g) {
        renderInventory(g, false);
    }

    @Override
    public void renderEditor(LadsGraphics g) {
        renderInventory(g, true);
    }

    private void renderInventory(LadsGraphics g, boolean preview) {
        // An empty inventory draws nothing in game; the HUD editor shows sample items (drawInventorySlot's preview) instead.
        if (!preview && g.getGame() != null && g.getGame().isMainInventoryEmpty()) return;
        g.fill(x, y, x + width, y + height, 0x80000000);

        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                int slot = row * COLS + col;
                int slotX = x + PADDING + col * SLOT_SIZE + 1;
                int slotY = y + PADDING + row * SLOT_SIZE + 1;

                g.fill(slotX - 1, slotY - 1, slotX + 17, slotY + 17, 0x25FFFFFF);
                g.drawInventorySlot(slot, slotX, slotY, preview);
            }
        }
    }
}
