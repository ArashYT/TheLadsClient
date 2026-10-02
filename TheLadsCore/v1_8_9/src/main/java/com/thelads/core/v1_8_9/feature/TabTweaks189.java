// Adapted from TabTweaks 1.5.11 by MicrocontrollersDev, LGPL-3.0-only; corresponding source in META-INF/lads-sources/tabtweaks.
package com.thelads.core.v1_8_9.feature;

import com.thelads.core.client.bridge.LadsGameBridge;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;

/**
 * PingView and TabList on 1.8.9 (GuiPlayerTabOverlayMixin): the 26.x TabTweaksConfig frame snapshot of the Lads module options,
 * refreshed every client tick, and the numeric ping.
 */
public final class TabTweaks189 {
    public static boolean layout, ping;
    public static int maxTabPlayers = 80, playersPerColumn = 20;
    public static boolean removeHeader, removeFooter, removeHeads, removeNpcHeads, improvedHeads, removeObjectives, showPlayerCount;
    public static String playerCountFormat = "Players: {count}";
    public static boolean removeHeaderShadow, removeBodyShadow, removeFooterShadow;
    public static boolean removePing, showPingInTab, removePingShadow, scalePingDisplay, hideFalsePing;
    public static float tabScale = 1, moveTabDown, moveTabHorizontal;
    public static boolean moveTabBelowBossBars;
    public static int tabHeaderColor, tabBodyColor, tabFooterColor, tabPlayerListColor;
    /** QA only (Probe150): ping numbers drawn. */
    public static long pings;
    private static final int[] PING_COLORS = new int[6];
    private static final String[] PING_BANDS = {"Ping 0-74", "Ping 75-144", "Ping 145-199", "Ping 200-299", "Ping 300-399", "Ping 400+"};
    private static final int[] PING_DEFAULTS = {-15466667, -14773218, -4733653, -13779, -6458098, -4318437};

    private TabTweaks189() {}

    public static void refresh() {
        layout = Options189.enabled("TabList");
        ping = Options189.enabled("PingView");
        maxTabPlayers = layout ? (int) Options189.number("TabList", "Max Players", 80) : 80;
        playersPerColumn = layout ? (int) Options189.number("TabList", "Players Per Column", 20) : 20;
        removeHeader = layout && Options189.bool("TabList", "Hide Header", false);
        removeFooter = layout && Options189.bool("TabList", "Hide Footer", false);
        removeHeads = layout && (!Options189.bool("TabList", "Player Skins", true) || Options189.bool("TabList", "Hide Heads", false));
        showPlayerCount = layout && Options189.bool("TabList", "Show Player Count", false);
        playerCountFormat = Options189.string("TabList", "Player Count Format", "Players: {count}");
        removeNpcHeads = layout && Options189.bool("TabList", "Hide NPC Heads", false);
        improvedHeads = layout && Options189.bool("TabList", "Improved Hats", true);
        removeObjectives = layout && Options189.bool("TabList", "Hide Objectives", false);
        boolean shadow = Options189.bool("TabList", "Text Shadow", true);
        removeHeaderShadow = layout && !(shadow && Options189.bool("TabList", "Header Shadow", true));
        removeBodyShadow = layout && !(shadow && Options189.bool("TabList", "Body Shadow", true));
        removeFooterShadow = layout && !(shadow && Options189.bool("TabList", "Footer Shadow", true));
        tabScale = layout ? (float) Options189.number("TabList", "Size", 100) / 100F : 1F;
        moveTabDown = layout ? (float) Options189.number("TabList", "Y Offset", 10) : 0F;
        moveTabHorizontal = layout ? (float) Options189.number("TabList", "X Offset", 0) : 0F;
        moveTabBelowBossBars = layout && Options189.bool("TabList", "Below Boss Bars", true);
        tabHeaderColor = panelColor("Header Color", 0x80000000);
        tabBodyColor = panelColor("Body Color", 0x80000000);
        tabFooterColor = panelColor("Footer Color", 0x80000000);
        tabPlayerListColor = panelColor("Player Row Color", 0x20ffffff);
        removePing = ping && Options189.bool("PingView", "Hide Ping", false);
        showPingInTab = ping && Options189.bool("PingView", "Show Numbers", true);
        removePingShadow = ping && !Options189.bool("PingView", "Text Shadow", true);
        scalePingDisplay = ping && Options189.bool("PingView", "Small Numbers", false);
        hideFalsePing = ping && Options189.bool("PingView", "Hide False Ping", false);
        boolean fixed = Options189.choice("PingView", "Color Mode", 0) == 1;
        for (int i = 0; i < PING_COLORS.length; i++)
            PING_COLORS[i] = fixed ? Options189.color("PingView", "Static Color", -1) : Options189.color("PingView", PING_BANDS[i], PING_DEFAULTS[i]);
    }

    private static int panelColor(String name, int fallback) {
        switch (Options189.choice("TabList", "Background", 0)) {
            case 1: return 0xc0000000;
            case 2: return 0x60ffffff;
            case 3: return 0;
            default:
                int color = Options189.color("TabList", name, fallback);
                return color == fallback && "Body Color".equals(name) ? Options189.color("TabList", "Global Tab Background", fallback) : color;
        }
    }

    /** The tab list's move down below the boss bar: 1.8.9 draws at most one, its bar ending 17 GUI px down (26.x: 12 for one bar). */
    public static float down() {
        return moveTabBelowBossBars && LadsGameBridge.get().bossBarCount() > 0 ? Math.max(12, moveTabDown) : moveTabDown;
    }

    /** PingView's number where the ping bars go (GuiPlayerTabOverlay.drawPing's slot width, x and y), coloured by latency band. */
    public static void drawPing(FontRenderer font, int slotWidth, int x, int y, int ping) {
        int color = ping < 0 ? -5636096 : PING_COLORS[ping < 75 ? 0 : ping < 145 ? 1 : ping < 200 ? 2 : ping < 300 ? 3 : ping < 400 ? 4 : 5];
        String text = hideFalsePing && (ping <= 1 || ping >= 999) ? "" : String.valueOf(ping);
        int width = font.getStringWidth(text);
        pings++;
        if (scalePingDisplay) {
            GlStateManager.pushMatrix();
            GlStateManager.scale(0.5F, 0.5F, 1.0F);
            font.drawString(text, 2 * (x + slotWidth) - width - 4, 2 * y + 4, color, !removePingShadow);
            GlStateManager.popMatrix();
        } else font.drawString(text, x + slotWidth - width, y, color, !removePingShadow);
    }
}
