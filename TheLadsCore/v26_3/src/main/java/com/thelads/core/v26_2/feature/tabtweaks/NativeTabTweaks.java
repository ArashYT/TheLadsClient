// TabTweaks 1.5.11 port integration, LGPL-3.0-only.
package com.thelads.core.v26_2.feature.tabtweaks;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.thelads.core.config.*;
import com.thelads.core.v26_2.feature.NativeQualityOfLife;
import com.thelads.core.v26_2.feature.tabtweaks.config.TabTweaksConfig;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.LoggerFactory;

public final class NativeTabTweaks {
    private static boolean active;
    public static boolean active() { return active; }
    public static void initialize() {
        if (active || FabricLoader.getInstance().isModLoaded("tabtweaks")) return;
        importPrevious();
        active = true;
        ModuleSupport.registerBuiltIn("TabList", "PingView");
        TabTweaksConfig.refresh();
        ClientTickEvents.END_CLIENT_TICK.register(mc -> { TabTweaksConfig.refresh(); NativeTabTweaksProbe.tick(); });
    }
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
            } finally { Files.deleteIfExists(temporary); }
        } catch (Exception failure) {
            ConfigManager.applyJson(before);
            LoggerFactory.getLogger("TheLadsCore").warn("Could not import previous tab layout; original preferences retained", failure);
        }
    }
    public static void applyLegacy(JsonObject json) {
        for (String[] entry : new String[][] {
            {"maxTabPlayers","Max Players"}, {"playersPerColumn","Players Per Column"},
            {"removeHeader","Hide Header"}, {"removeFooter","Hide Footer"}, {"removeHeads","Hide Heads"},
            {"removeNpcHeads","Hide NPC Heads"}, {"improvedHeads","Improved Hats"}, {"removeObjectives","Hide Objectives"},
            {"moveTabDown","Y Offset"}, {"moveTabHorizontal","X Offset"}, {"moveTabBelowBossBars","Below Boss Bars"},
            {"tabHeaderColor","Header Color"}, {"tabBodyColor","Body Color"}, {"tabFooterColor","Footer Color"},
            {"tabPlayerListColor","Player Row Color"}
        }) importOption(json, "TabList", entry[0], entry[1], false);
        for (String[] entry : new String[][] {
            {"removePing","Hide Ping"}, {"showPingInTab","Show Numbers"}, {"scalePingDisplay","Small Numbers"},
            {"hideFalsePing","Hide False Ping"}, {"pingColorOne","Ping 0-74"}, {"pingColorTwo","Ping 75-144"},
            {"pingColorThree","Ping 145-199"}, {"pingColorFour","Ping 200-299"}, {"pingColorFive","Ping 300-399"}, {"pingColorSix","Ping 400+"}
        }) importOption(json, "PingView", entry[0], entry[1], false);
        importOption(json,"TabList","removeHeaderShadow","Header Shadow",true);
        importOption(json,"TabList","removeBodyShadow","Body Shadow",true);
        importOption(json,"TabList","removeFooterShadow","Footer Shadow",true);
        importOption(json,"PingView","removePingShadow","Text Shadow",true);
        if (json.has("tabScale")) ((SliderOption) NativeQualityOfLife.module("TabList").getOption("Size")).setValue(json.get("tabScale").getAsDouble() * 100);
    }
    private static void importOption(JsonObject json, String module, String field, String option, boolean invert) {
        if (!json.has(field)) return;
        var value = NativeQualityOfLife.module(module).getOption(option);
        if (value instanceof ColorOption color) { color.setUseGlobal(false); color.setColor(json.get(field).getAsInt()); }
        else if (value instanceof BoolOption bool) bool.set(invert != json.get(field).getAsBoolean());
        else value.load(json.get(field));
    }
}
