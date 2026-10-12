// Behaviour adapted from the Lads 26.3 Tab Tweaks port (TabTweaks 1.5.11 by MicrocontrollersDev), LGPL-3.0-only.
package com.thelads.core.v1_20_1.hud.overlay;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.systems.RenderSystem;
import com.thelads.core.config.BoolOption;
import com.thelads.core.config.ColorOption;
import com.thelads.core.config.ConfigManager;
import com.thelads.core.config.HudSettings;
import com.thelads.core.config.ModuleSupport;
import com.thelads.core.config.SliderOption;
import com.thelads.core.v1_20_1.hud.Hud1201;
import com.thelads.core.v1_20_1.hud.Mods1201;
import com.thelads.core.v1_20_1.mixin.BossBarAccessor1201;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.resources.ResourceLocation;

/**
 * TabList (layout) and PingView (latency) on 1.20.1's PlayerTabOverlay, one engine as on 26.3. A frame snapshot of the Lads
 * preferences is read by TabOverlayMixin. Stands down while the Tab Tweaks mod is installed.
 */
public final class TabTweaks1201 {
    private static boolean active;
    public static boolean layout, ping;
    public static int maxTabPlayers = 80, playersPerColumn = 20;
    public static boolean removeHeader, removeFooter, removeHeads, removeNpcHeads, improvedHeads, removeObjectives, showPlayerCount;
    public static String playerCountFormat = "Players: {count}";
    public static boolean removeHeaderShadow, removeBodyShadow, removeFooterShadow;
    public static boolean removePing, showPingInTab, removePingShadow, scalePingDisplay, hideFalsePing;
    public static float tabScale = 1, moveTabDown, moveTabHorizontal;
    public static boolean moveTabBelowBossBars;
    public static int tabHeaderColor, tabBodyColor, tabFooterColor, tabPlayerListColor;
    private static int pingColorOne, pingColorTwo, pingColorThree, pingColorFour, pingColorFive, pingColorSix;

    private TabTweaks1201() {}

    static void register() {
        if (Mods1201.loaded("tabtweaks")) {
            ModuleSupport.registerExternal("TabList", "Tab Tweaks", "tabtweaks", true);
            ModuleSupport.registerExternal("PingView", "Tab Tweaks", "tabtweaks", true);
            return;
        }
        importPrevious();
        active = true;
        ModuleSupport.registerBuiltIn("TabList", "PingView");
        refresh();
    }

    /** Once per tab list frame (TabOverlayMixin's render wrapper). */
    public static void refresh() {
        layout = active && Hud1201.enabled("TabList");
        ping = active && Hud1201.enabled("PingView");
        maxTabPlayers = layout ? (int) number("TabList", "Max Players", 80) : 80;
        playersPerColumn = layout ? (int) number("TabList", "Players Per Column", 20) : 20;
        removeHeader = layout && bool("TabList", "Hide Header", false);
        removeFooter = layout && bool("TabList", "Hide Footer", false);
        removeHeads = layout && (!bool("TabList", "Player Skins", true) || bool("TabList", "Hide Heads", false));
        showPlayerCount = layout && bool("TabList", "Show Player Count", false);
        playerCountFormat = Hud1201.string("TabList", "Player Count Format", "Players: {count}");
        removeNpcHeads = layout && bool("TabList", "Hide NPC Heads", false);
        improvedHeads = layout && bool("TabList", "Improved Hats", true);
        removeObjectives = layout && bool("TabList", "Hide Objectives", false);
        boolean shadow = bool("TabList", "Text Shadow", true);
        removeHeaderShadow = layout && !(shadow && bool("TabList", "Header Shadow", true));
        removeBodyShadow = layout && !(shadow && bool("TabList", "Body Shadow", true));
        removeFooterShadow = layout && !(shadow && bool("TabList", "Footer Shadow", true));
        tabScale = layout ? (float) number("TabList", "Size", 100) / 100F : 1F;
        moveTabDown = layout ? (float) number("TabList", "Y Offset", 10) : 0F;
        moveTabHorizontal = layout ? (float) number("TabList", "X Offset", 0) : 0F;
        moveTabBelowBossBars = layout && bool("TabList", "Below Boss Bars", true);
        tabHeaderColor = panelColor("Header Color", 0x80000000);
        tabBodyColor = panelColor("Body Color", 0x80000000);
        tabFooterColor = panelColor("Footer Color", 0x80000000);
        tabPlayerListColor = panelColor("Player Row Color", 0x20ffffff);
        removePing = ping && bool("PingView", "Hide Ping", false);
        showPingInTab = ping && bool("PingView", "Show Numbers", true);
        removePingShadow = ping && !bool("PingView", "Text Shadow", true);
        scalePingDisplay = ping && bool("PingView", "Small Numbers", false);
        hideFalsePing = ping && bool("PingView", "Hide False Ping", false);
        boolean fixed = Hud1201.choice("PingView", "Color Mode", 0) == 1;
        int fixedColor = color("PingView", "Static Color", -1);
        pingColorOne = fixed ? fixedColor : color("PingView", "Ping 0-74", -15466667);
        pingColorTwo = fixed ? fixedColor : color("PingView", "Ping 75-144", -14773218);
        pingColorThree = fixed ? fixedColor : color("PingView", "Ping 145-199", -4733653);
        pingColorFour = fixed ? fixedColor : color("PingView", "Ping 200-299", -13779);
        pingColorFive = fixed ? fixedColor : color("PingView", "Ping 300-399", -6458098);
        pingColorSix = fixed ? fixedColor : color("PingView", "Ping 400+", -4318437);
    }

    /** Distance the list moves down: its Y Offset, or below vanilla's boss bars when they reach further. */
    public static float distanceY() {
        if (!moveTabBelowBossBars) return moveTabDown;
        return Math.max(bossBarShift(), moveTabDown);
    }

    /** Where vanilla BossHealthOverlay draws its last bar (y 12, 19 apart, stopping at a third of the screen); 0 without bars. */
    private static int bossBarShift() {
        Minecraft mc = Minecraft.getInstance();
        if (Hud1201.enabled("BossBar")) return 0; // Lads' own boss bars are a movable HUD element, not vanilla's stack
        int bars = ((BossBarAccessor1201) mc.gui.getBossOverlay()).ladsEvents().size();
        int y = 12, last = 0, limit = mc.getWindow().getGuiScaledHeight() / 3;
        for (int i = 0; i < bars; i++) {
            last = y;
            y += 19;
            if (y >= limit) break;
        }
        return last;
    }

    /** PingView numbers in place of the bars (vanilla renderPingIcon's slot). */
    public static void drawLatency(GuiGraphics graphics, int slotWidth, int x, int y, PlayerInfo info) {
        Minecraft mc = Minecraft.getInstance();
        // 1.9.1: Hypixel lists everyone at 1 ms: your own row shows the measured ping, the others no number (RealPing).
        int latency = com.thelads.core.client.hypixel.RealPing.row(info.getLatency(), mc.player != null && mc.player.getUUID().equals(info.getProfile().getId()));
        int color = latency == com.thelads.core.client.hypixel.RealPing.NONE ? 0xFF808080 : -5636096;
        if (latency >= 0 && latency < 75) color = pingColorOne;
        else if (latency >= 75 && latency < 145) color = pingColorTwo;
        else if (latency >= 145 && latency < 200) color = pingColorThree;
        else if (latency >= 200 && latency < 300) color = pingColorFour;
        else if (latency >= 300 && latency < 400) color = pingColorFive;
        else if (latency >= 400) color = pingColorSix;
        String text = hideFalsePing && (latency <= 1 || latency >= 999) ? "" : latency == com.thelads.core.client.hypixel.RealPing.NONE ? "-" : String.valueOf(latency);
        int width = mc.font.width(text);
        boolean shadow = !removePingShadow;
        if (scalePingDisplay) {
            graphics.pose().pushPose();
            try {
                graphics.pose().scale(0.5F, 0.5F, 1F);
                graphics.drawString(mc.font, text, 2 * (x + slotWidth) - width - 4, 2 * y + 4, color, shadow);
            } finally {
                graphics.pose().popPose();
            }
        } else graphics.drawString(mc.font, text, x + slotWidth - width, y, color, shadow);
    }

    /** Improved Hats: the hat layer one pixel larger than the face and centred on it, so it reads as a layer over the head. */
    public static void drawHead(GuiGraphics graphics, ResourceLocation skin, int x, int y, int size, boolean hat, boolean upsideDown) {
        int v = 8 + (upsideDown ? 8 : 0), height = 8 * (upsideDown ? -1 : 1);
        graphics.blit(skin, x, y, size, size, 8.0F, v, 8, height, 64, 64);
        if (!hat) return;
        graphics.pose().pushPose();
        try {
            graphics.pose().translate(-0.5F, -0.5F, 0F);
            RenderSystem.enableBlend();
            graphics.blit(skin, x, y, size + 1, size + 1, 40.0F, v, 8, height, 64, 64);
            RenderSystem.disableBlend();
        } finally {
            graphics.pose().popPose();
        }
    }

    private static int panelColor(String name, int fallback) {
        return switch (Hud1201.choice("TabList", "Background", 0)) {
            case 1 -> 0xc0000000;
            case 2 -> 0x60ffffff;
            case 3 -> 0;
            default -> {
                int c = color("TabList", name, fallback);
                yield c == fallback && "Body Color".equals(name) ? color("TabList", "Global Tab Background", fallback) : c;
            }
        };
    }

    private static int color(String module, String name, int fallback) {
        var value = Hud1201.module(module);
        return value != null && value.getOption(name) instanceof ColorOption option
            ? option.isUseGlobal() ? HudSettings.getInstance().getGlobalColor() : option.getColor() : fallback;
    }

    private static boolean bool(String module, String name, boolean fallback) { return Hud1201.bool(module, name, fallback); }
    private static double number(String module, String name, double fallback) { return Hud1201.number(module, name, fallback); }

    /** One-time, read-only import of a previous Tab Tweaks config (config/tabtweaks.json), as on 26.3. */
    private static void importPrevious() {
        Path directory = FabricLoader.getInstance().getConfigDir();
        Path source = directory.resolve("tabtweaks.json"), marker = directory.resolve("lads-tabtweaks-import.json");
        if (Files.exists(marker) || !Files.isRegularFile(source)) return;
        var before = ConfigManager.toJson();
        try {
            if (Files.size(source) > 1_048_576) throw new IllegalArgumentException("Tab layout file too large");
            applyLegacy(JsonParser.parseString(Files.readString(source)).getAsJsonObject());
            ConfigManager.save();
            Path temporary = Files.createTempFile(directory, "lads-tab-import-", ".tmp");
            try {
                Files.writeString(temporary, "{\"version\":1,\"source\":\"tabtweaks.json\"}\n");
                try { Files.move(temporary, marker, StandardCopyOption.ATOMIC_MOVE); }
                catch (java.nio.file.AtomicMoveNotSupportedException ignored) { Files.move(temporary, marker); }
            } finally {
                Files.deleteIfExists(temporary);
            }
        } catch (Exception failure) {
            ConfigManager.applyJson(before);
            Hud1201.LOG.warn("Could not import previous tab layout; original preferences retained", failure);
        }
    }

    private static void applyLegacy(JsonObject json) {
        for (String[] entry : new String[][] {
            {"maxTabPlayers", "Max Players"}, {"playersPerColumn", "Players Per Column"},
            {"removeHeader", "Hide Header"}, {"removeFooter", "Hide Footer"}, {"removeHeads", "Hide Heads"},
            {"removeNpcHeads", "Hide NPC Heads"}, {"improvedHeads", "Improved Hats"}, {"removeObjectives", "Hide Objectives"},
            {"moveTabDown", "Y Offset"}, {"moveTabHorizontal", "X Offset"}, {"moveTabBelowBossBars", "Below Boss Bars"},
            {"tabHeaderColor", "Header Color"}, {"tabBodyColor", "Body Color"}, {"tabFooterColor", "Footer Color"},
            {"tabPlayerListColor", "Player Row Color"}
        }) importOption(json, "TabList", entry[0], entry[1], false);
        for (String[] entry : new String[][] {
            {"removePing", "Hide Ping"}, {"showPingInTab", "Show Numbers"}, {"scalePingDisplay", "Small Numbers"},
            {"hideFalsePing", "Hide False Ping"}, {"pingColorOne", "Ping 0-74"}, {"pingColorTwo", "Ping 75-144"},
            {"pingColorThree", "Ping 145-199"}, {"pingColorFour", "Ping 200-299"}, {"pingColorFive", "Ping 300-399"}, {"pingColorSix", "Ping 400+"}
        }) importOption(json, "PingView", entry[0], entry[1], false);
        importOption(json, "TabList", "removeHeaderShadow", "Header Shadow", true);
        importOption(json, "TabList", "removeBodyShadow", "Body Shadow", true);
        importOption(json, "TabList", "removeFooterShadow", "Footer Shadow", true);
        importOption(json, "PingView", "removePingShadow", "Text Shadow", true);
        if (json.has("tabScale")) ((SliderOption) Hud1201.module("TabList").getOption("Size")).setValue(json.get("tabScale").getAsDouble() * 100);
    }

    private static void importOption(JsonObject json, String module, String field, String option, boolean invert) {
        if (!json.has(field)) return;
        var value = Hud1201.module(module).getOption(option);
        if (value instanceof ColorOption color) { color.setUseGlobal(false); color.setColor(json.get(field).getAsInt()); }
        else if (value instanceof BoolOption bool) bool.set(invert != json.get(field).getAsBoolean());
        else value.load(json.get(field));
    }
}
