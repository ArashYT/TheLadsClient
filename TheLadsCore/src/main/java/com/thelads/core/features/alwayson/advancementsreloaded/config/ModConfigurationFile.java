package com.thelads.core.features.alwayson.advancementsreloaded.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.thelads.core.features.alwayson.advancementsreloaded.utils.Utils;
import net.fabricmc.loader.api.FabricLoader;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class ModConfigurationFile {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static FileType storedFileType = FileType.JSON;
    public static final Runnable saveRunnable = ModConfigurationFile::save;

    private ModConfigurationFile() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static void load(FileType filetype) {
        storedFileType = filetype;
        File file = getConfigFile();
        if (!file.exists()) {
            Utils.LOGGER.info("Configuration file not found, creating new one.");
            save();
            return;
        }

        try (InputStreamReader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            
            if (root.has("appearance")) {
                JsonObject appearance = root.getAsJsonObject("appearance");
                Configuration.displaySidebar = getBoolean(appearance, "display_sideabar", true);
                Configuration.displayDescription = getBoolean(appearance, "display_description", true);
                Configuration.criteriasAlphabeticOrder = getBoolean(appearance, "criterias_alphabetic_order", true);
                
                try {
                    Configuration.advancementsOrder = Configuration.AdvancementOrder.valueOf(
                        getString(appearance, "advancements_order", "ALPHABETIC").toUpperCase()
                    );
                } catch (Exception e) {
                    Configuration.advancementsOrder = Configuration.AdvancementOrder.ALPHABETIC;
                }
                try {
                    Configuration.tabsOrder = Configuration.TabOrder.valueOf(
                        getString(appearance, "tabs_order", "ALPHABETIC").toUpperCase()
                    );
                } catch (Exception e) {
                    Configuration.tabsOrder = Configuration.TabOrder.ALPHABETIC;
                }
                try {
                    Configuration.backgroundStyle = Configuration.BackgroundStyle.valueOf(
                        getString(appearance, "background_style", "TRANSPARENT").toUpperCase()
                    );
                } catch (Exception e) {
                    Configuration.backgroundStyle = Configuration.BackgroundStyle.TRANSPARENT;
                }
                try {
                    Configuration.criteriasTranslationMode = Configuration.TranslationMode.valueOf(
                        getString(appearance, "criterias_translation_mode", "ONLY_COMPATIBLE").toUpperCase()
                    );
                } catch (Exception e) {
                    Configuration.criteriasTranslationMode = Configuration.TranslationMode.ONLY_COMPATIBLE;
                }
            }

            if (root.has("advanced_customization")) {
                JsonObject advanced = root.getAsJsonObject("advanced_customization");
                Configuration.headerHeight = getInt(advanced, "header_height", 48);
                Configuration.footerHeight = getInt(advanced, "footer_height", 48);
                Configuration.criteriasWidth = getInt(advanced, "criterias_width", 142);
                Configuration.aboveWidgetLimit = getInt(advanced, "above_widget_limit", 14);
                Configuration.belowWidgetLimit = getInt(advanced, "below_widget_limit", 14);
                
                Configuration.customTabsOrder = getStringList(advanced, "custom_tabs_order");
                Configuration.customAdvancementsOrder = getStringList(advanced, "custom_advancements_order");
            }
        } catch (Exception e) {
            Utils.LOGGER.error("Failed to load configuration file", e);
        }
    }

    public static void save() {
        File file = getConfigFile();
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
            JsonObject root = new JsonObject();

            JsonObject appearance = new JsonObject();
            appearance.addProperty("display_sideabar", Configuration.displaySidebar);
            appearance.addProperty("display_description", Configuration.displayDescription);
            appearance.addProperty("criterias_alphabetic_order", Configuration.criteriasAlphabeticOrder);
            appearance.addProperty("advancements_order", Configuration.advancementsOrder.name());
            appearance.addProperty("tabs_order", Configuration.tabsOrder.name());
            appearance.addProperty("background_style", Configuration.backgroundStyle.name());
            appearance.addProperty("criterias_translation_mode", Configuration.criteriasTranslationMode.name());
            root.add("appearance", appearance);

            JsonObject advanced = new JsonObject();
            advanced.addProperty("header_height", Configuration.headerHeight);
            advanced.addProperty("footer_height", Configuration.footerHeight);
            advanced.addProperty("criterias_width", Configuration.criteriasWidth);
            advanced.addProperty("above_widget_limit", Configuration.aboveWidgetLimit);
            advanced.addProperty("below_widget_limit", Configuration.belowWidgetLimit);
            
            advanced.add("custom_tabs_order", GSON.toJsonTree(Configuration.customTabsOrder));
            advanced.add("custom_advancements_order", GSON.toJsonTree(Configuration.customAdvancementsOrder));
            root.add("advanced_customization", advanced);

            GSON.toJson(root, writer);
        } catch (IOException e) {
            Utils.LOGGER.error("Failed to save configuration file", e);
        }
    }

    private static File getConfigFile() {
        String filename = storedFileType == FileType.JSON ? "advancements_reloaded.json" : "advancements_reloaded.toml";
        return FabricLoader.getInstance().getConfigDir().resolve(filename).toFile();
    }

    private static boolean getBoolean(JsonObject obj, String memberName, boolean defaultValue) {
        return obj.has(memberName) ? obj.get(memberName).getAsBoolean() : defaultValue;
    }

    private static String getString(JsonObject obj, String memberName, String defaultValue) {
        return obj.has(memberName) ? obj.get(memberName).getAsString() : defaultValue;
    }

    private static int getInt(JsonObject obj, String memberName, int defaultValue) {
        return obj.has(memberName) ? obj.get(memberName).getAsInt() : defaultValue;
    }

    private static List<String> getStringList(JsonObject obj, String memberName) {
        List<String> list = new ArrayList<>();
        if (obj.has(memberName) && obj.get(memberName).isJsonArray()) {
            for (var elem : obj.getAsJsonArray(memberName)) {
                list.add(elem.getAsString());
            }
        }
        return list;
    }

    public enum FileType {
        JSON,
        TOML
    }
}
