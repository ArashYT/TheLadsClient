package com.thelads.core.client.gui;

import com.thelads.core.client.bridge.LadsGraphics;
import com.thelads.core.client.title.TitleScreenTheme;
import java.util.List;
import java.util.function.Consumer;

/**
 * The right-click menu of the server and world lists. Each version's screen mixin opens it, gives it its clicks and keys first and draws it
 * last; picking runs that screen's own (vanilla) action. Closes on a pick, an outside click or Esc, and stays inside the screen.
 */
public final class ListContextMenu {
    public record Item(String id, String label, String icon, boolean enabled) {}

    private static final int ROW = 16, PAD = 3, ICON = 10, ICON_GAP = 5, MIN_WIDTH = 96;
    private static final int DISABLED = 0xFF6E5E62;
    private static boolean anyOpen;
    private List<Item> items = List.of();
    private Consumer<String> pick = id -> {};
    private boolean open;
    private int anchorX, anchorY, x, y, width;

    public void open(int mouseX, int mouseY, List<Item> items, Consumer<String> pick) {
        this.items = items;
        this.pick = pick;
        anchorX = x = mouseX;
        anchorY = y = mouseY;
        width = MIN_WIDTH;
        open = anyOpen = true;
    }

    public void close() {
        open = anyOpen = false;
    }

    public boolean isOpen() {
        return open;
    }

    /** A press outside this class needs to know it is the menu's (only one screen is open at a time). */
    public static boolean anyOpen() {
        return anyOpen;
    }

    /** Where a menu this big opens at the mouse: down and right of it, moved back in where it would leave the screen. */
    public static int[] place(int anchorX, int anchorY, int width, int height, int screenWidth, int screenHeight) {
        return new int[] {Math.max(0, Math.min(anchorX, screenWidth - width)), Math.max(0, Math.min(anchorY, screenHeight - height))};
    }

    private int height() {
        return items.size() * ROW + 2 * PAD;
    }

    /** The row under a point, -1 off the rows. */
    private int rowAt(double px, double py) {
        if (px < x || px >= x + width || py < y + PAD || py >= y + PAD + items.size() * ROW) return -1;
        return (int) ((py - y - PAD) / ROW);
    }

    public void render(LadsGraphics g, int mouseX, int mouseY) {
        if (open) g.overlay(() -> draw(g, mouseX, mouseY));
    }

    private void draw(LadsGraphics g, int mouseX, int mouseY) {
        width = MIN_WIDTH;
        for (Item item : items) width = Math.max(width, g.textWidth(item.label()) + ICON + ICON_GAP + 2 * PAD + 4);
        int[] at = place(anchorX, anchorY, width, height(), g.getScaledWidth(), g.getScaledHeight());
        x = at[0];
        y = at[1];
        MenuGraphics.round(g, x - 1, y - 1, width + 2, height() + 2, LadsPalette.BORDER);
        MenuGraphics.round(g, x, y, width, height(), LadsPalette.PANEL);
        int hovered = rowAt(mouseX, mouseY);
        for (int i = 0; i < items.size(); i++) {
            Item item = items.get(i);
            int top = y + PAD + i * ROW, color = item.enabled() ? LadsPalette.TEXT : DISABLED;
            if (i == hovered && item.enabled()) MenuGraphics.round(g, x + 2, top, width - 4, ROW, LadsPalette.BORDER);
            TitleScreenTheme.icon(g, item.icon(), x + PAD + 2, top + (ROW - ICON) / 2, ICON, item.enabled() ? LadsPalette.ACCENT : DISABLED);
            g.drawText(item.label(), x + PAD + 2 + ICON + ICON_GAP, top + (ROW - g.fontHeight()) / 2 + 1, color, false);
        }
    }

    /** Every click while open is the menu's: a row picks it (a disabled one does nothing), anywhere else closes it. False when closed. */
    public boolean mouseClicked(double mouseX, double mouseY) {
        if (!open) return false;
        int row = rowAt(mouseX, mouseY);
        if (row < 0) {
            close();
            return true;
        }
        Item item = items.get(row);
        if (item.enabled()) {
            close();
            pick.accept(item.id());
        }
        return true;
    }
}
