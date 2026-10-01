// Adapted from TabTweaks 1.5.11 by MicrocontrollersDev, LGPL-3.0-only.
package com.thelads.core.v26_2.feature.tabtweaks.config;

import com.thelads.core.config.*;
import com.thelads.core.v26_2.feature.NativeQualityOfLife;
import com.thelads.core.v26_2.feature.tabtweaks.NativeTabTweaks;

/** Frame snapshot backed by Lads module preferences, without a second config engine. */
public final class TabTweaksConfig {
    private static final TabTweaksConfig CURRENT = new TabTweaksConfig();
    public boolean layout, ping;
    public int maxTabPlayers = 80, playersPerColumn = 20;
    public boolean removeHeader, removeFooter, removeHeads, removeNpcHeads, improvedHeads, removeObjectives;
    public boolean showPlayerCount;
    public String playerCountFormat = "Players: {count}";
    public boolean removeHeaderShadow, removeBodyShadow, removeFooterShadow;
    public boolean removePing, showPingInTab, removePingShadow, scalePingDisplay, hideFalsePing;
    public float tabScale = 1, moveTabDown, moveTabHorizontal;
    public boolean moveTabBelowBossBars;
    public int tabHeaderColor, tabBodyColor, tabFooterColor, tabPlayerListColor;
    public int pingColorOne, pingColorTwo, pingColorThree, pingColorFour, pingColorFive, pingColorSix;
    public static TabTweaksConfig current() { return CURRENT; }
    public static void refresh() {
        var c = CURRENT;
        c.layout = NativeTabTweaks.active() && NativeQualityOfLife.enabled("TabList");
        c.ping = NativeTabTweaks.active() && NativeQualityOfLife.enabled("PingView");
        c.maxTabPlayers = c.layout ? (int) number("TabList", "Max Players", 80) : 80;
        c.playersPerColumn = c.layout ? (int) number("TabList", "Players Per Column", 20) : 20;
        c.removeHeader = c.layout && bool("TabList", "Hide Header", false);
        c.removeFooter = c.layout && bool("TabList", "Hide Footer", false);
        c.removeHeads = c.layout && (!bool("TabList", "Player Skins", true) || bool("TabList", "Hide Heads", false));
        c.showPlayerCount = c.layout && bool("TabList", "Show Player Count", false);
        c.playerCountFormat = NativeQualityOfLife.string("TabList", "Player Count Format", "Players: {count}");
        c.removeNpcHeads = c.layout && bool("TabList", "Hide NPC Heads", false);
        c.improvedHeads = c.layout && bool("TabList", "Improved Hats", true);
        c.removeObjectives = c.layout && bool("TabList", "Hide Objectives", false);
        boolean shadow = bool("TabList", "Text Shadow", true);
        c.removeHeaderShadow = c.layout && !(shadow && bool("TabList", "Header Shadow", true));
        c.removeBodyShadow = c.layout && !(shadow && bool("TabList", "Body Shadow", true));
        c.removeFooterShadow = c.layout && !(shadow && bool("TabList", "Footer Shadow", true));
        c.tabScale = c.layout ? (float) number("TabList", "Size", 100) / 100F : 1F;
        c.moveTabDown = c.layout ? (float) number("TabList", "Y Offset", 10) : 0F;
        c.moveTabHorizontal = c.layout ? (float) number("TabList", "X Offset", 0) : 0F;
        c.moveTabBelowBossBars = c.layout && bool("TabList", "Below Boss Bars", true);
        c.tabHeaderColor = panelColor("Header Color", 0x80000000);
        c.tabBodyColor = panelColor("Body Color", 0x80000000);
        c.tabFooterColor = panelColor("Footer Color", 0x80000000);
        c.tabPlayerListColor = panelColor("Player Row Color", 0x20ffffff);
        c.removePing = c.ping && bool("PingView", "Hide Ping", false);
        c.showPingInTab = c.ping && bool("PingView", "Show Numbers", true);
        c.removePingShadow = c.ping && !bool("PingView", "Text Shadow", true);
        c.scalePingDisplay = c.ping && bool("PingView", "Small Numbers", false);
        c.hideFalsePing = c.ping && bool("PingView", "Hide False Ping", false);
        boolean fixed = NativeQualityOfLife.choice("PingView", "Color Mode", 0) == 1;
        int fixedColor = color("PingView", "Static Color", -1);
        c.pingColorOne = fixed ? fixedColor : color("PingView", "Ping 0-74", -15466667);
        c.pingColorTwo = fixed ? fixedColor : color("PingView", "Ping 75-144", -14773218);
        c.pingColorThree = fixed ? fixedColor : color("PingView", "Ping 145-199", -4733653);
        c.pingColorFour = fixed ? fixedColor : color("PingView", "Ping 200-299", -13779);
        c.pingColorFive = fixed ? fixedColor : color("PingView", "Ping 300-399", -6458098);
        c.pingColorSix = fixed ? fixedColor : color("PingView", "Ping 400+", -4318437);
    }
    private static int panelColor(String name, int fallback) {
        return switch (NativeQualityOfLife.choice("TabList", "Background", 0)) {
            case 1 -> 0xc0000000; case 2 -> 0x60ffffff; case 3 -> 0;
            default -> {
                int c = color("TabList", name, fallback);
                if (c == fallback && "Body Color".equals(name)) {
                    yield color("TabList", "Global Tab Background", fallback);
                }
                yield c;
            }
        };
    }
    private static int color(String module, String name, int fallback) {
        var option = NativeQualityOfLife.module(module).getOption(name);
        return option instanceof ColorOption value ? value.isUseGlobal() ? HudSettings.getInstance().getGlobalColor() : value.getColor() : fallback;
    }
    private static boolean bool(String module, String name, boolean fallback) { return NativeQualityOfLife.bool(module, name, fallback); }
    private static double number(String module, String name, double fallback) { return NativeQualityOfLife.number(module, name, fallback); }
}
