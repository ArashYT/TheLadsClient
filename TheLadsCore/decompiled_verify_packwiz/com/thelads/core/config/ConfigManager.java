/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.google.gson.Gson
 *  com.google.gson.GsonBuilder
 *  com.google.gson.JsonArray
 *  com.google.gson.JsonElement
 *  com.google.gson.JsonObject
 *  net.fabricmc.loader.api.FabricLoader
 */
package com.thelads.core.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.thelads.core.config.HudSettings;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.Option;
import com.thelads.core.modules.HudModule;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;

public class ConfigManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static File testConfigFile = null;

    protected static void setTestConfigFile(File file) {
        testConfigFile = file;
    }

    private static File getConfigFile() {
        if (testConfigFile != null) {
            return testConfigFile;
        }
        return new File(FabricLoader.getInstance().getConfigDir().toFile(), "thelads_config.json");
    }

    public static JsonObject toJson() {
        JsonObject json = new JsonObject();
        JsonObject modulesJson = new JsonObject();
        for (Module module : ModuleManager.getInstance().getModules()) {
            JsonObject moduleJson = new JsonObject();
            moduleJson.addProperty("enabled", Boolean.valueOf(module.isEnabled()));
            if (module instanceof HudModule) {
                HudModule hudModule = (HudModule)module;
                moduleJson.addProperty("useGlobalColor", Boolean.valueOf(hudModule.isUseGlobalColor()));
                moduleJson.addProperty("customColor", (Number)hudModule.getCustomColor());
            }
            if (!module.getOptions().isEmpty()) {
                JsonObject jsonObject = new JsonObject();
                for (Option o : module.getOptions()) {
                    jsonObject.add(o.getName(), o.save());
                }
                moduleJson.add("options", (JsonElement)jsonObject);
            }
            moduleJson.addProperty("favorite", Boolean.valueOf(module.isFavorite()));
            moduleJson.addProperty("lastModified", (Number)module.getLastModified());
            modulesJson.add(module.getName(), (JsonElement)moduleJson);
        }
        json.add("modules", (JsonElement)modulesJson);
        JsonObject hud = new JsonObject();
        hud.addProperty("globalColor", (Number)HudSettings.getInstance().getGlobalColor());
        hud.addProperty("globalBackground", (Number)HudSettings.getInstance().getGlobalBackground());
        hud.addProperty("textShadow", Boolean.valueOf(HudSettings.getInstance().isTextShadow()));
        JsonObject positions = new JsonObject();
        for (Map.Entry entry : HudSettings.getInstance().getPositions().entrySet()) {
            JsonArray xy = new JsonArray();
            xy.add((Number)((int[])entry.getValue())[0]);
            xy.add((Number)((int[])entry.getValue())[1]);
            positions.add((String)entry.getKey(), (JsonElement)xy);
        }
        hud.add("positions", (JsonElement)positions);
        JsonArray fade = new JsonArray();
        for (int c : HudSettings.getInstance().getFadePlaylist()) {
            fade.add((Number)c);
        }
        hud.add("fadePlaylist", (JsonElement)fade);
        JsonArray jsonArray = new JsonArray();
        for (String n : HudSettings.getInstance().getLocked()) {
            jsonArray.add(n);
        }
        hud.add("locked", (JsonElement)jsonArray);
        JsonArray groupsArr = new JsonArray();
        for (Set<String> g : HudSettings.getInstance().getGroups()) {
            JsonArray ga = new JsonArray();
            for (String n : g) {
                ga.add(n);
            }
            groupsArr.add((JsonElement)ga);
        }
        hud.add("groups", (JsonElement)groupsArr);
        json.add("hud", (JsonElement)hud);
        return json;
    }

    /*
     * WARNING - void declaration
     */
    public static void applyJson(JsonObject json) {
        if (json == null) {
            return;
        }
        if (json.has("modules")) {
            JsonObject modulesJson = json.getAsJsonObject("modules");
            for (Module module : ModuleManager.getInstance().getModules()) {
                try {
                    if (!modulesJson.has(module.getName())) continue;
                    JsonObject moduleJson = modulesJson.getAsJsonObject(module.getName());
                    if (moduleJson.has("enabled")) {
                        module.setEnabled(moduleJson.get("enabled").getAsBoolean());
                    }
                    if (module instanceof HudModule) {
                        HudModule hm = (HudModule)module;
                        if (moduleJson.has("useGlobalColor")) {
                            hm.setUseGlobalColor(moduleJson.get("useGlobalColor").getAsBoolean());
                        }
                        if (moduleJson.has("customColor")) {
                            hm.setCustomColor(moduleJson.get("customColor").getAsInt());
                        }
                    }
                    if (moduleJson.has("options")) {
                        JsonObject opts = moduleJson.getAsJsonObject("options");
                        for (Option o : module.getOptions()) {
                            if (!opts.has(o.getName())) continue;
                            o.load(opts.get(o.getName()));
                        }
                    }
                    if (moduleJson.has("favorite")) {
                        module.setFavorite(moduleJson.get("favorite").getAsBoolean());
                    }
                    if (!moduleJson.has("lastModified")) continue;
                    module.setLastModified(moduleJson.get("lastModified").getAsLong());
                }
                catch (Exception ex) {
                    System.err.println("Failed to apply config for module: " + module.getName());
                    ex.printStackTrace();
                }
            }
        }
        if (json.has("hud")) {
            JsonObject hud = json.getAsJsonObject("hud");
            if (hud.has("globalColor")) {
                HudSettings.getInstance().setGlobalColor(hud.get("globalColor").getAsInt());
            }
            if (hud.has("globalBackground")) {
                HudSettings.getInstance().setGlobalBackground(hud.get("globalBackground").getAsInt());
            }
            if (hud.has("textShadow")) {
                HudSettings.getInstance().setTextShadow(hud.get("textShadow").getAsBoolean());
            }
            if (hud.has("positions")) {
                JsonObject pos = hud.getAsJsonObject("positions");
                for (String key : pos.keySet()) {
                    try {
                        JsonArray xy = pos.getAsJsonArray(key);
                        HudSettings.getInstance().setPosition(key, xy.get(0).getAsInt(), xy.get(1).getAsInt());
                    }
                    catch (Exception xy) {}
                }
            }
            if (hud.has("fadePlaylist")) {
                void var3_7;
                HudSettings.getInstance().getFadePlaylist().clear();
                JsonArray fade = hud.getAsJsonArray("fadePlaylist");
                boolean bl = false;
                while (var3_7 < fade.size()) {
                    HudSettings.getInstance().getFadePlaylist().add(fade.get((int)var3_7).getAsInt());
                    ++var3_7;
                }
            }
            if (hud.has("locked")) {
                void var3_9;
                HudSettings.getInstance().getLocked().clear();
                JsonArray la = hud.getAsJsonArray("locked");
                boolean bl = false;
                while (var3_9 < la.size()) {
                    HudSettings.getInstance().getLocked().add(la.get((int)var3_9).getAsString());
                    ++var3_9;
                }
            }
            if (hud.has("groups")) {
                void var3_11;
                HudSettings.getInstance().getGroups().clear();
                JsonArray ga = hud.getAsJsonArray("groups");
                boolean bl = false;
                while (var3_11 < ga.size()) {
                    JsonArray grp = ga.get((int)var3_11).getAsJsonArray();
                    HashSet<String> s = new HashSet<String>();
                    for (int j = 0; j < grp.size(); ++j) {
                        s.add(grp.get(j).getAsString());
                    }
                    if (!s.isEmpty()) {
                        HudSettings.getInstance().getGroups().add(s);
                    }
                    ++var3_11;
                }
            }
        }
    }

    public static void load() {
        File configFile = ConfigManager.getConfigFile();
        if (!configFile.exists()) {
            return;
        }
        try (InputStreamReader reader = new InputStreamReader((InputStream)new FileInputStream(configFile), StandardCharsets.UTF_8);){
            ConfigManager.applyJson((JsonObject)GSON.fromJson((Reader)reader, JsonObject.class));
        }
        catch (Exception e) {
            System.err.println("Failed to load config");
            e.printStackTrace();
        }
    }

    public static void save() {
        File configFile = ConfigManager.getConfigFile();
        configFile.getParentFile().mkdirs();
        try (OutputStreamWriter writer = new OutputStreamWriter((OutputStream)new FileOutputStream(configFile), StandardCharsets.UTF_8);){
            GSON.toJson((JsonElement)ConfigManager.toJson(), (Appendable)writer);
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }
}

