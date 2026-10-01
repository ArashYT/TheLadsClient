// The Lads: independent implementation (clean room), not derived from Tooltips TXF. It reads and writes the same
// config/tooltipstxf.json5 keys so a player's existing settings keep applying.
package com.thelads.core.v1_21_1.embedded.tooltips;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.LoggerFactory;

final class TooltipsConfig {
    private static final String GRAY = "#555555";
    // Key, default value, and (for colours) nothing else: the order is the file's order.
    private static final Map<String, Boolean> SWITCHES = new LinkedHashMap<>();
    private static final Map<String, String> COLORS = new LinkedHashMap<>();
    static {
        SWITCHES.put("enableMod", true);
        SWITCHES.put("timeInSeconds", false);
        SWITCHES.put("showDurability", true);
        SWITCHES.put("showFoodValues", true);
        SWITCHES.put("showCompostable", true);
        SWITCHES.put("showBurnTime", true);
        SWITCHES.put("showUseCooldown", false);
        SWITCHES.put("showSongDuration", true);
        SWITCHES.put("showEnchantability", false);
        SWITCHES.put("showRepairCost", false);
        SWITCHES.put("showStrength", false);
        SWITCHES.put("showEnchantmentPower", true);
        SWITCHES.put("showMiningLevel", false);
        SWITCHES.put("showMiningSpeed", false);
        SWITCHES.put("showModName", false);
        SWITCHES.put("showComponents", false);
        for (String key : new String[] {"durability", "foodValues", "compostable", "burnTime", "useCooldown", "songDuration",
            "enchantability", "repairCost", "strength", "enchantmentPower", "miningLevel", "miningSpeed"}) COLORS.put(key + "Color", GRAY);
        COLORS.put("modNameColor", "#5555ff");
    }
    private static final String[] COMPONENT_COLORS = {GRAY, "#55FFFF", "#AAAAAA", GRAY};

    private final Map<String, Boolean> switches = new LinkedHashMap<>(SWITCHES);
    private final Map<String, Integer> colors = new LinkedHashMap<>();
    private final int[] componentColors = new int[COMPONENT_COLORS.length];

    private TooltipsConfig() {
        COLORS.forEach((key, value) -> colors.put(key, rgb(value, 0x555555)));
        for (int i = 0; i < COMPONENT_COLORS.length; i++) componentColors[i] = rgb(COMPONENT_COLORS[i], 0x555555);
    }

    boolean on(String key) { return switches.getOrDefault(key, false); }
    int color(String key) { return colors.getOrDefault(key + "Color", 0x555555); }
    int componentColor(int index) { return componentColors[index]; }

    static TooltipsConfig load() {
        TooltipsConfig config = new TooltipsConfig();
        Path path = FabricLoader.getInstance().getConfigDir().resolve("tooltipstxf.json5");
        try {
            if (!Files.exists(path)) {
                Files.writeString(path, defaults());
                return config;
            }
            JsonObject json;
            try (Reader reader = Files.newBufferedReader(path); JsonReader lenient = new JsonReader(reader)) {
                lenient.setLenient(true); // JSON5: comments, unquoted keys, trailing commas
                json = JsonParser.parseReader(lenient).getAsJsonObject();
            }
            for (String key : SWITCHES.keySet())
                if (json.get(key) instanceof JsonElement value && value.isJsonPrimitive()) config.switches.put(key, value.getAsBoolean());
            for (String key : COLORS.keySet())
                if (json.get(key) instanceof JsonElement value && value.isJsonPrimitive()) config.colors.put(key, rgb(value.getAsString(), config.colors.get(key)));
            if (json.get("componentsColors") instanceof JsonArray array)
                for (int i = 0; i < Math.min(array.size(), COMPONENT_COLORS.length); i++)
                    config.componentColors[i] = rgb(array.get(i).getAsString(), config.componentColors[i]);
        } catch (IOException | RuntimeException failure) {
            LoggerFactory.getLogger("TheLadsCore").warn("Tooltips: could not read {}, using defaults", path, failure);
        }
        return config;
    }

    private static int rgb(String hex, int fallback) {
        try {
            return Integer.parseInt(hex.trim().replace("#", ""), 16) & 0xFFFFFF;
        } catch (RuntimeException invalid) {
            return fallback;
        }
    }

    private static String defaults() {
        var text = new StringBuilder("{\n");
        SWITCHES.forEach((key, value) -> text.append("  \"").append(key).append("\": ").append(value).append(",\n"));
        text.append('\n');
        COLORS.forEach((key, value) -> text.append("  \"").append(key).append("\": \"").append(value).append("\",\n"));
        text.append("  // Title, Ctrl, Key, Value\n  \"componentsColors\": [\n");
        for (int i = 0; i < COMPONENT_COLORS.length; i++)
            text.append("    \"").append(COMPONENT_COLORS[i]).append(i + 1 < COMPONENT_COLORS.length ? "\",\n" : "\"\n");
        return text.append("  ]\n}\n").toString();
    }
}
