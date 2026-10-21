package com.thelads.core.client.hypixel.quickplay;

import com.thelads.core.client.HypixelSafetyManager;
import com.thelads.core.client.bridge.LadsGraphics;
import com.thelads.core.client.gui.LadsPalette;
import com.thelads.core.client.gui.MenuGraphics;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Common GUI for Hypixel Quickplay: categories, vanilla item icons, modes grid, search, and favorites bar.
 * Rendered through LadsGraphics / MenuGraphics across 1.8.9, 1.20.1, and 26.3.
 */
public final class QuickplayScreen {
    private Runnable onClose;
    private QuickplayCategory selectedCategory = QuickplayCategory.BEDWARS;
    private String searchQuery = "";
    private int scrollOffset = 0;
    private int maxScroll = 0;

    public void setOnClose(Runnable onClose) {
        this.onClose = onClose;
    }

    public void close() {
        if (onClose != null) onClose.run();
    }

    public void render(LadsGraphics g, int mouseX, int mouseY) {
        int sw = g.getScaledWidth();
        int sh = g.getScaledHeight();

        // Dim full background
        g.fill(0, 0, sw, sh, 0x88000000);

        // Main modal dialog bounds
        int dialogW = Math.min(520, sw - 40);
        int dialogH = Math.min(340, sh - 40);
        int dialogX = (sw - dialogW) / 2;
        int dialogY = (sh - dialogH) / 2;

        // Base dialog card
        MenuGraphics.round(g, dialogX, dialogY, dialogW, dialogH, LadsPalette.PANEL);
        // Border outline
        g.fill(dialogX, dialogY, dialogX + dialogW, dialogY + 1, LadsPalette.BORDER);
        g.fill(dialogX, dialogY + dialogH - 1, dialogX + dialogW, dialogY + dialogH, LadsPalette.BORDER);
        g.fill(dialogX, dialogY, dialogX + 1, dialogY + dialogH, LadsPalette.BORDER);
        g.fill(dialogX + dialogW - 1, dialogY, dialogX + dialogW, dialogY + dialogH, LadsPalette.BORDER);

        // Header bar
        int headerH = 28;
        MenuGraphics.round(g, dialogX, dialogY, dialogW, headerH, LadsPalette.CARD);
        g.drawText("§lHYPIXEL QUICKPLAY", dialogX + 12, dialogY + 9, LadsPalette.TEXT);

        // Hypixel status badge
        boolean onHypixel = HypixelSafetyManager.isHypixel();
        if (onHypixel) {
            int badgeW = g.textWidth("Hypixel Connected") + 12;
            int badgeX = dialogX + dialogW - badgeW - 32;
            MenuGraphics.round(g, badgeX, dialogY + 6, badgeW, 16, 0x44228822);
            g.drawText("§aHypixel Connected", badgeX + 6, dialogY + 10, -1);
        } else {
            int badgeW = g.textWidth("Hypixel Only") + 12;
            int badgeX = dialogX + dialogW - badgeW - 32;
            MenuGraphics.round(g, badgeX, dialogY + 6, badgeW, 16, 0x55882222);
            g.drawText("§cHypixel Only", badgeX + 6, dialogY + 10, -1);
        }

        // Close 'X' button
        int closeBtnX = dialogX + dialogW - 24;
        int closeBtnY = dialogY + 6;
        boolean closeHover = mouseX >= closeBtnX && mouseX <= closeBtnX + 16 && mouseY >= closeBtnY && mouseY <= closeBtnY + 16;
        MenuGraphics.round(g, closeBtnX, closeBtnY, 16, 16, closeHover ? LadsPalette.HOVER : LadsPalette.CARD);
        g.drawCenteredText("§7✕", closeBtnX + 8, closeBtnY + 4, closeHover ? LadsPalette.ACCENT : LadsPalette.MUTED);

        int currentY = dialogY + headerH + 6;

        // Favourites Row
        int favH = 32;
        int favX = dialogX + 10;
        int favW = dialogW - 20;
        MenuGraphics.round(g, favX, currentY, favW, favH, LadsPalette.CARD);

        List<String> favIds = QuickplayModule.getFavorites();
        int slotW = (favW - 8) / 9;
        for (int i = 0; i < 9; i++) {
            int slotX = favX + 4 + i * slotW;
            int slotY = currentY + 3;
            int boxW = slotW - 4;
            int boxH = 26;

            boolean hasFav = i < favIds.size();
            QuickplayMode favMode = hasFav ? QuickplayCatalog.findById(favIds.get(i)) : null;

            boolean hover = mouseX >= slotX && mouseX <= slotX + boxW && mouseY >= slotY && mouseY <= slotY + boxH;
            int boxColor = hover ? LadsPalette.HOVER : (hasFav ? 0xFF23151D : 0xFF140C11);
            MenuGraphics.round(g, slotX, slotY, boxW, boxH, boxColor);

            // Number indicator [1] - [9]
            g.drawText("§8" + (i + 1), slotX + 3, slotY + 2, LadsPalette.MUTED);

            if (favMode != null) {
                // Item icon
                g.drawItemIcon(favMode.iconKey(), slotX + boxW - 19, slotY + 5);
                // Name
                String trimmedName = MenuGraphics.fit(g, favMode.name(), boxW - 24);
                g.drawText(trimmedName, slotX + 12, slotY + 9, hover ? LadsPalette.ACCENT : LadsPalette.TEXT);
            } else {
                g.drawCenteredText("§8+", slotX + boxW / 2, slotY + 9, LadsPalette.DISABLED);
            }
        }

        currentY += favH + 6;

        // Search Bar & Requeue Bar
        int searchBarH = 22;
        int searchW = 160;
        int searchX = dialogX + 10;
        MenuGraphics.round(g, searchX, currentY, searchW, searchBarH, LadsPalette.CARD);
        String searchDisplay = searchQuery.isEmpty() ? "§8Search modes..." : searchQuery;
        g.drawText(searchDisplay, searchX + 6, currentY + 7, searchQuery.isEmpty() ? LadsPalette.MUTED : LadsPalette.TEXT);

        // Last command / Play Again shortcut button
        String lastCmd = QuickplayModule.getLastPlayCommand();
        if (lastCmd != null && !lastCmd.isEmpty()) {
            int reqW = 160;
            int reqX = dialogX + dialogW - reqW - 10;
            boolean reqHover = mouseX >= reqX && mouseX <= reqX + reqW && mouseY >= currentY && mouseY <= currentY + searchBarH;
            MenuGraphics.round(g, reqX, currentY, reqW, searchBarH, reqHover ? LadsPalette.PRIMARY_HOVER : LadsPalette.PRIMARY);
            String reqText = "§fRequeue: " + lastCmd;
            g.drawCenteredText(MenuGraphics.fit(g, reqText, reqW - 8), reqX + reqW / 2, currentY + 7, -1);
        }

        currentY += searchBarH + 6;

        // Left Sidebar: Categories
        int sideW = 125;
        int sideX = dialogX + 10;
        int contentH = dialogY + dialogH - currentY - 28;
        MenuGraphics.round(g, sideX, currentY, sideW, contentH, LadsPalette.CARD);

        QuickplayCategory[] categories = QuickplayCategory.values();
        int catBtnH = contentH / categories.length;
        for (int i = 0; i < categories.length; i++) {
            QuickplayCategory cat = categories[i];
            int catY = currentY + i * catBtnH;
            boolean selected = cat == selectedCategory && searchQuery.isEmpty();
            boolean hover = mouseX >= sideX && mouseX <= sideX + sideW && mouseY >= catY && mouseY <= catY + catBtnH;

            int catColor = selected ? LadsPalette.PRIMARY : (hover ? LadsPalette.HOVER : LadsPalette.CARD);
            MenuGraphics.round(g, sideX + 2, catY + 1, sideW - 4, catBtnH - 2, catColor);

            // Category item icon
            g.drawItemIcon(cat.getIconKey(), sideX + 6, catY + (catBtnH - 16) / 2);
            // Category label
            int textCol = selected ? 0xFFFFFFFF : (hover ? LadsPalette.ACCENT : LadsPalette.TEXT);
            g.drawText(MenuGraphics.fit(g, cat.getDisplayName(), sideW - 28), sideX + 26, catY + (catBtnH - g.fontHeight()) / 2 + 1, textCol);
        }

        // Center / Main Area: Modes Grid
        int mainX = sideX + sideW + 6;
        int mainW = dialogX + dialogW - mainX - 10;
        MenuGraphics.round(g, mainX, currentY, mainW, contentH, LadsPalette.CARD);

        // Gather modes to display
        List<QuickplayMode> modes = searchQuery.isEmpty()
            ? QuickplayCatalog.getModes(selectedCategory)
            : QuickplayCatalog.search(searchQuery);

        // Not on Hypixel warning banner if outside Hypixel
        if (!onHypixel) {
            MenuGraphics.round(g, mainX + 4, currentY + 4, mainW - 8, 20, 0x66992222);
            g.drawCenteredText("§cQuickplay commands only work on the Hypixel Network (mc.hypixel.net)", mainX + mainW / 2, currentY + 10, -1);
        }

        int modesStartY = currentY + (onHypixel ? 4 : 26);
        int modesVisibleH = contentH - (onHypixel ? 8 : 30);

        // Scissor for modes area
        g.enableScissor(mainX, modesStartY, mainX + mainW, modesStartY + modesVisibleH);

        int cols = 2;
        int cardMargin = 4;
        int cardW = (mainW - (cols + 1) * cardMargin) / cols;
        int cardH = 34;

        int totalRows = (modes.size() + cols - 1) / cols;
        int totalContentHeight = totalRows * (cardH + cardMargin);
        maxScroll = Math.max(0, totalContentHeight - modesVisibleH);
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));

        for (int i = 0; i < modes.size(); i++) {
            QuickplayMode mode = modes.get(i);
            int row = i / cols;
            int col = i % cols;

            int mX = mainX + cardMargin + col * (cardW + cardMargin);
            int mY = modesStartY + cardMargin + row * (cardH + cardMargin) - scrollOffset;

            if (mY + cardH < modesStartY || mY > modesStartY + modesVisibleH) continue;

            boolean hover = mouseX >= mX && mouseX <= mX + cardW && mouseY >= mY && mouseY <= mY + cardH
                && mouseY >= modesStartY && mouseY <= modesStartY + modesVisibleH;

            boolean isFav = QuickplayModule.isFavorite(mode.id());
            int cardColor = hover ? LadsPalette.HOVER : 0xFF140B10;
            MenuGraphics.round(g, mX, mY, cardW, cardH, cardColor);

            // Icon
            g.drawItemIcon(mode.iconKey(), mX + 6, mY + 9);

            // Title & Command
            g.drawText(MenuGraphics.fit(g, mode.name(), cardW - 48), mX + 26, mY + 6, hover ? LadsPalette.ACCENT : LadsPalette.TEXT);
            g.drawText("§8" + MenuGraphics.fit(g, mode.command(), cardW - 48), mX + 26, mY + 18, LadsPalette.MUTED);

            // Star indicator for favorites
            if (isFav) {
                g.drawText("§e★", mX + cardW - 14, mY + 11, -1);
            } else if (hover) {
                g.drawText("§8☆", mX + cardW - 14, mY + 11, LadsPalette.DISABLED);
            }
        }

        g.disableScissor();

        // Footer instructions
        int footerY = dialogY + dialogH - 22;
        String hint = "§8[Left Click] Join Game   [Right Click] Toggle Favorite   [1-9] Quick Favorites   [Esc] Exit";
        g.drawCenteredText(hint, dialogX + dialogW / 2, footerY + 6, LadsPalette.MUTED);
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int sw = (int) mouseX; // temporary, recomputed via dialog
        // Will be called with exact coordinates
        return handleClick((int) mouseX, (int) mouseY, button);
    }

    public boolean handleClick(int mouseX, int mouseY, int button) {
        // Find dialog coordinates
        int dialogW = Math.min(520, 800 - 40); // bounds reference
        // Handle favorites bar clicks
        List<String> favIds = QuickplayModule.getFavorites();
        for (int i = 0; i < favIds.size(); i++) {
            QuickplayMode fav = QuickplayCatalog.findById(favIds.get(i));
            if (fav != null) {
                // If clicked, execute
            }
        }
        return false;
    }

    public void handleMouseClick(int mouseX, int mouseY, int button, int screenWidth, int screenHeight) {
        int dialogW = Math.min(520, screenWidth - 40);
        int dialogH = Math.min(340, screenHeight - 40);
        int dialogX = (screenWidth - dialogW) / 2;
        int dialogY = (screenHeight - dialogH) / 2;

        // Close button
        int closeBtnX = dialogX + dialogW - 24;
        int closeBtnY = dialogY + 6;
        if (mouseX >= closeBtnX && mouseX <= closeBtnX + 16 && mouseY >= closeBtnY && mouseY <= closeBtnY + 16) {
            close();
            return;
        }

        int currentY = dialogY + 28 + 6;

        // Favourites Row
        int favH = 32;
        int favX = dialogX + 10;
        int favW = dialogW - 20;
        int slotW = (favW - 8) / 9;
        if (mouseY >= currentY && mouseY <= currentY + favH) {
            List<String> favIds = QuickplayModule.getFavorites();
            for (int i = 0; i < 9; i++) {
                int slotX = favX + 4 + i * slotW;
                int boxW = slotW - 4;
                if (mouseX >= slotX && mouseX <= slotX + boxW) {
                    if (i < favIds.size()) {
                        String modeId = favIds.get(i);
                        if (button == 1) { // Right click: remove favorite
                            QuickplayModule.toggleFavorite(modeId);
                        } else if (button == 0) { // Left click: execute
                            QuickplayMode mode = QuickplayCatalog.findById(modeId);
                            if (mode != null) {
                                QuickplayModule.executeCommand(mode.command(), this::close);
                            }
                        }
                    }
                    return;
                }
            }
        }

        currentY += favH + 6;

        // Requeue button
        int searchBarH = 22;
        String lastCmd = QuickplayModule.getLastPlayCommand();
        if (lastCmd != null && !lastCmd.isEmpty()) {
            int reqW = 160;
            int reqX = dialogX + dialogW - reqW - 10;
            if (mouseX >= reqX && mouseX <= reqX + reqW && mouseY >= currentY && mouseY <= currentY + searchBarH) {
                QuickplayModule.triggerPlayAgain();
                close();
                return;
            }
        }

        currentY += searchBarH + 6;

        // Left Sidebar: Categories
        int sideW = 125;
        int sideX = dialogX + 10;
        int contentH = dialogY + dialogH - currentY - 28;
        if (mouseX >= sideX && mouseX <= sideX + sideW && mouseY >= currentY && mouseY <= currentY + contentH) {
            QuickplayCategory[] categories = QuickplayCategory.values();
            int catBtnH = contentH / categories.length;
            int clickedIdx = (mouseY - currentY) / catBtnH;
            if (clickedIdx >= 0 && clickedIdx < categories.length) {
                selectedCategory = categories[clickedIdx];
                searchQuery = "";
                scrollOffset = 0;
            }
            return;
        }

        // Center / Main Area: Modes
        int mainX = sideX + sideW + 6;
        int mainW = dialogX + dialogW - mainX - 10;
        boolean onHypixel = HypixelSafetyManager.isHypixel();
        int modesStartY = currentY + (onHypixel ? 4 : 26);
        int modesVisibleH = contentH - (onHypixel ? 8 : 30);

        if (mouseX >= mainX && mouseX <= mainX + mainW && mouseY >= modesStartY && mouseY <= modesStartY + modesVisibleH) {
            List<QuickplayMode> modes = searchQuery.isEmpty()
                ? QuickplayCatalog.getModes(selectedCategory)
                : QuickplayCatalog.search(searchQuery);

            int cols = 2;
            int cardMargin = 4;
            int cardW = (mainW - (cols + 1) * cardMargin) / cols;
            int cardH = 34;

            for (int i = 0; i < modes.size(); i++) {
                int row = i / cols;
                int col = i % cols;
                int mX = mainX + cardMargin + col * (cardW + cardMargin);
                int mY = modesStartY + cardMargin + row * (cardH + cardMargin) - scrollOffset;

                if (mouseX >= mX && mouseX <= mX + cardW && mouseY >= mY && mouseY <= mY + cardH) {
                    QuickplayMode mode = modes.get(i);
                    if (button == 1) { // Right click: toggle favorite
                        QuickplayModule.toggleFavorite(mode.id());
                    } else if (button == 0) { // Left click: execute
                        QuickplayModule.executeCommand(mode.command(), this::close);
                    }
                    return;
                }
            }
        }
    }

    public void handleMouseScroll(double amount) {
        if (amount != 0) {
            scrollOffset -= (int) (amount * 24);
            scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));
        }
    }

    public boolean keyPressed(int keyCode) {
        // Escape or Backspace on empty search closes
        if (keyCode == 256) { // GLFW_KEY_ESCAPE
            close();
            return true;
        }
        if (keyCode == 259 && !searchQuery.isEmpty()) { // GLFW_KEY_BACKSPACE
            searchQuery = searchQuery.substring(0, searchQuery.length() - 1);
            scrollOffset = 0;
            return true;
        }
        // Number keys 1-9 trigger favorites
        if (searchQuery.isEmpty() && keyCode >= 49 && keyCode <= 57) { // GLFW_KEY_1 to 9
            int favIdx = keyCode - 49;
            List<String> favIds = QuickplayModule.getFavorites();
            if (favIdx < favIds.size()) {
                QuickplayMode mode = QuickplayCatalog.findById(favIds.get(favIdx));
                if (mode != null) {
                    QuickplayModule.executeCommand(mode.command(), this::close);
                    return true;
                }
            }
        }
        return false;
    }

    public boolean charTyped(char codePoint) {
        if (codePoint >= 32 && codePoint != 127) {
            searchQuery += codePoint;
            scrollOffset = 0;
            return true;
        }
        return false;
    }
}
