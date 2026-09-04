package com.thelads.core.client.gui;

import com.thelads.core.client.bridge.LadsGraphics;
import com.thelads.core.config.ConfigManager;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class LadsSettingsScreen {
    private String currentTab = "MODS";
    private String currentCategory = "ALL";
    private String currentSort = "Alphabetical";
    private boolean sortAscending = true;
    private String searchQuery = "";
    private double scrollOffset = 0;
    private final long openTime;
    private Runnable onOpenHudEditor = () -> {};
    private Runnable onClose = () -> {};
    private int lastWidth = 800;
    private int lastHeight = 600;

    // Premium Red and Black color palette
    private static final int BG = 0xEE050505;
    private static final int SIDEBAR = 0xEE0A0A0A;
    private static final int CARD = 0xAA111111;
    private static final int CARD_HOVER = 0xAA2B1111;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int ACCENT = 0xFFD32F2F;
    private static final int TOGGLE_ON = 0xFF2E7D32;
    private static final int TOGGLE_OFF = 0xFFB71C1C;

    public LadsSettingsScreen() {
        this.openTime = System.currentTimeMillis();
    }

    public void setOnOpenHudEditor(Runnable onOpenHudEditor) {
        this.onOpenHudEditor = onOpenHudEditor;
    }

    public void setOnClose(Runnable onClose) {
        this.onClose = onClose;
    }

    public void setSearchQuery(String query) {
        this.searchQuery = (query != null) ? query : "";
    }

    public String getSearchQuery() {
        return searchQuery;
    }

    public void render(LadsGraphics g, int mouseX, int mouseY) {
        this.lastWidth = g.getScaledWidth();
        this.lastHeight = g.getScaledHeight();
        int width = this.lastWidth;
        int height = this.lastHeight;

        // Calculate smooth entry easing
        long elapsed = System.currentTimeMillis() - openTime;
        float progress = Math.min(1.0f, elapsed / 200.0f);
        float ease = 1.0f - (float) Math.pow(1.0f - progress, 3);

        int winW = Math.min(900, width - 40);
        int winH = Math.min(500, height - 40);
        int winX = (width - winW) / 2;
        int winY = (int) (((height - winH) / 2) * ease);

        // Dark backdrop
        g.fill(0, 0, width, height, (int) (0xDD * ease) << 24);

        // Main window container
        g.fill(winX, winY, winX + winW, winY + winH, BG);
        g.fill(winX, winY, winX + winW, winY + 2, ACCENT);

        // Left sidebar (Categories)
        int sidebarW = 160;
        g.fill(winX, winY, winX + sidebarW, winY + winH, SIDEBAR);
        g.drawText("THE LADS CLIENT", winX + 16, winY + 16, ACCENT, true);

        String[] categories = { "ALL", "NEW", "HUD", "SERVER", "MECHANIC" };
        int catY = winY + 48;
        for (String cat : categories) {
            boolean active = cat.equalsIgnoreCase(currentCategory);
            boolean hover = mouseX >= winX && mouseX <= winX + sidebarW && mouseY >= catY && mouseY <= catY + 24;
            int bgCol = active ? 0x44D32F2F : (hover ? 0x22FFFFFF : 0);
            if (bgCol != 0) g.fill(winX + 4, catY, winX + sidebarW - 4, catY + 24, bgCol);
            g.drawText(cat, winX + 16, catY + 7, active ? ACCENT : TEXT, false);
            catY += 28;
        }

        // Action button in sidebar: EDIT HUD LAYOUT
        int hudBtnY = winY + winH - 36;
        boolean hoverHud = mouseX >= winX + 8 && mouseX <= winX + sidebarW - 8 && mouseY >= hudBtnY && mouseY <= hudBtnY + 26;
        g.fill(winX + 8, hudBtnY, winX + sidebarW - 8, hudBtnY + 26, hoverHud ? 0xFFE53935 : ACCENT);
        g.drawCenteredText("EDIT HUD LAYOUT", winX + sidebarW / 2, hudBtnY + 8, 0xFFFFFFFF, true);

        // Top navigation tabs: MODS, PERFORMANCE, PACKS, HUD PRESETS
        String[] tabs = { "MODS", "PERFORMANCE", "PACKS", "HUD PRESETS" };
        int tabX = winX + sidebarW + 16;
        for (String tab : tabs) {
            boolean active = tab.equalsIgnoreCase(currentTab);
            boolean hover = mouseX >= tabX && mouseX <= tabX + 80 && mouseY >= winY + 12 && mouseY <= winY + 32;
            int col = active ? ACCENT : (hover ? 0xFFEEEEEE : 0xFFAAAAAA);
            g.drawText(tab, tabX, winY + 16, col, active);
            if (active) g.fill(tabX, winY + 30, tabX + g.textWidth(tab), winY + 32, ACCENT);
            tabX += 100;
        }

        // Search bar display
        int searchX = winX + winW - 170;
        int searchY = winY + 12;
        g.fill(searchX, searchY, searchX + 150, searchY + 20, 0x55000000);
        g.fill(searchX, searchY + 19, searchX + 150, searchY + 20, 0x88D32F2F);
        String searchDisplay = searchQuery.isEmpty() ? "Search..." : searchQuery;
        g.drawText(searchDisplay, searchX + 6, searchY + 5, searchQuery.isEmpty() ? 0xFF888888 : TEXT, false);

        // Content area with module cards
        int contentX = winX + sidebarW + 16;
        int contentY = winY + 45;
        int contentW = winW - sidebarW - 32;
        int contentH = winH - 60;

        List<Module> filtered = getFilteredModules();
        int cardW = (contentW - 16) / 2;
        int cardH = 64;
        int gap = 10;

        g.enableScissor(contentX, contentY, contentX + contentW, contentY + contentH);

        for (int i = 0; i < filtered.size(); i++) {
            Module m = filtered.get(i);
            int col = i % 2;
            int row = i / 2;
            int cx = contentX + col * (cardW + gap);
            int cy = (int) (contentY + row * (cardH + gap) - scrollOffset);

            if (cy + cardH < contentY || cy > contentY + contentH) continue;

            boolean hover = mouseX >= cx && mouseX <= cx + cardW && mouseY >= cy && mouseY <= cy + cardH;
            g.fill(cx, cy, cx + cardW, cy + cardH, hover ? CARD_HOVER : CARD);
            g.fill(cx, cy, cx + cardW, cy + 1, hover ? ACCENT : 0x22FFFFFF);

            // Module Title & Description
            g.drawText(m.getName(), cx + 10, cy + 10, TEXT, false);
            String desc = m.getDescription();
            if (g.textWidth(desc) > cardW - 80) {
                while (desc.length() > 3 && g.textWidth(desc + "...") > cardW - 80) {
                    desc = desc.substring(0, desc.length() - 1);
                }
                desc += "...";
            }
            g.drawText(desc, cx + 10, cy + 24, 0xFFAAAAAA, false);

            // Toggle Button
            int btnW = 55;
            int btnH = 20;
            int btnX = cx + cardW - btnW - 8;
            int btnY = cy + (cardH - btnH) / 2;
            boolean btnHover = mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= btnY && mouseY <= btnY + btnH;
            int btnCol = m.isEnabled() ? TOGGLE_ON : TOGGLE_OFF;
            if (btnHover) btnCol = (btnCol & 0x00FFFFFF) | 0xDD000000;
            g.fill(btnX, btnY, btnX + btnW, btnY + btnH, btnCol);
            g.drawCenteredText(m.isEnabled() ? "ON" : "OFF", btnX + btnW / 2, btnY + 5, TEXT, false);

            // Favorite star
            int starX = cx + cardW - 74;
            int starY = cy + 10;
            g.drawText(m.isFavorite() ? "★" : "☆", starX, starY, m.isFavorite() ? 0xFFFFD700 : 0xFF666666, false);
        }

        g.disableScissor();
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int width = (lastWidth > 0) ? lastWidth : 800;
        int height = (lastHeight > 0) ? lastHeight : 600;
        int winW = Math.min(900, width - 40);
        int winH = Math.min(500, height - 40);
        int winX = (width - winW) / 2;
        int winY = (height - winH) / 2;
        int sidebarW = 160;

        // Categories click
        String[] categories = { "ALL", "NEW", "HUD", "SERVER", "MECHANIC" };
        int catY = winY + 48;
        for (String cat : categories) {
            if (mouseX >= winX && mouseX <= winX + sidebarW && mouseY >= catY && mouseY <= catY + 24) {
                currentCategory = cat;
                scrollOffset = 0;
                return true;
            }
            catY += 28;
        }

        // EDIT HUD LAYOUT click
        int hudBtnY = winY + winH - 36;
        if (mouseX >= winX + 8 && mouseX <= winX + sidebarW - 8 && mouseY >= hudBtnY && mouseY <= hudBtnY + 26) {
            if (onOpenHudEditor != null) onOpenHudEditor.run();
            return true;
        }

        // Tab click
        String[] tabs = { "MODS", "PERFORMANCE", "PACKS", "HUD PRESETS" };
        int tabX = winX + sidebarW + 16;
        for (String tab : tabs) {
            if (mouseX >= tabX && mouseX <= tabX + 80 && mouseY >= winY + 12 && mouseY <= winY + 32) {
                currentTab = tab;
                scrollOffset = 0;
                return true;
            }
            tabX += 100;
        }

        // Module cards click
        int contentX = winX + sidebarW + 16;
        int contentY = winY + 45;
        int contentW = winW - sidebarW - 32;
        int contentH = winH - 60;
        int cardW = (contentW - 16) / 2;
        int cardH = 64;
        int gap = 10;

        List<Module> filtered = getFilteredModules();
        for (int i = 0; i < filtered.size(); i++) {
            Module m = filtered.get(i);
            int col = i % 2;
            int row = i / 2;
            int cx = contentX + col * (cardW + gap);
            int cy = (int) (contentY + row * (cardH + gap) - scrollOffset);

            if (cy + cardH < contentY || cy > contentY + contentH) continue;

            // Toggle button check
            int btnW = 55;
            int btnH = 20;
            int btnX = cx + cardW - btnW - 8;
            int btnY = cy + (cardH - btnH) / 2;
            if (mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= btnY && mouseY <= btnY + btnH) {
                m.toggle();
                ConfigManager.save();
                return true;
            }

            // Favorite star check
            int starX = cx + cardW - 74;
            int starY = cy + 10;
            if (mouseX >= starX - 4 && mouseX <= starX + 16 && mouseY >= starY - 4 && mouseY <= starY + 16) {
                m.setFavorite(!m.isFavorite());
                ConfigManager.save();
                return true;
            }
        }

        return false;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        scrollOffset = Math.max(0, scrollOffset - amount * 24);
        return true;
    }

    public List<Module> getFilteredModules() {
        List<Module> list = new ArrayList<>();
        for (Module m : ModuleManager.getInstance().getModules()) {
            if (!currentCategory.equalsIgnoreCase("ALL")) {
                if (!m.getCategory().name().equalsIgnoreCase(currentCategory)) {
                    continue;
                }
            }
            if (!searchQuery.isEmpty()) {
                if (!m.getName().toLowerCase().contains(searchQuery.toLowerCase())
                    && !m.getDescription().toLowerCase().contains(searchQuery.toLowerCase())) {
                    continue;
                }
            }
            list.add(m);
        }

        if ("Alphabetical".equalsIgnoreCase(currentSort)) {
            list.sort(Comparator.comparing(Module::getName));
        } else if ("Recently Opened".equalsIgnoreCase(currentSort)) {
            list.sort(Comparator.comparingLong(Module::getLastOpenedTime).reversed());
        }
        if (!sortAscending) {
            java.util.Collections.reverse(list);
        }
        return list;
    }

    public String getCurrentTab() { return currentTab; }
    public String getCurrentCategory() { return currentCategory; }
}
