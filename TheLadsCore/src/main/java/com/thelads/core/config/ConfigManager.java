package com.thelads.core.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.thelads.core.modules.HudModule;
import net.fabricmc.loader.api.FabricLoader;

import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

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

    /** Serialize the current module + HUD state to a JSON snapshot (also used by profiles). */
    public static JsonObject toJson() {
        JsonObject json = new JsonObject();
        JsonObject modulesJson = new JsonObject();
        for (Module module : ModuleManager.getInstance().getModules()) {
            JsonObject moduleJson = new JsonObject();
            moduleJson.addProperty("enabled", module.isEnabled());
            if (module instanceof HudModule) {
                HudModule hm = (HudModule) module;
                moduleJson.addProperty("useGlobalColor", hm.isUseGlobalColor());
                moduleJson.addProperty("customColor", hm.getCustomColor());
            }
            if (!module.getOptions().isEmpty()) {
                JsonObject opts = new JsonObject();
                for (Option o : module.getOptions()) {
                    opts.add(o.getName(), o.save());
                }
                moduleJson.add("options", opts);
            }
            moduleJson.addProperty("favorite", module.isFavorite());
            moduleJson.addProperty("lastModified", module.getLastModified());
            modulesJson.add(module.getName(), moduleJson);
        }
        json.add("modules", modulesJson);

        JsonObject hud = new JsonObject();
        hud.addProperty("globalColor", HudSettings.getInstance().getGlobalColor());
        hud.addProperty("globalBackground", HudSettings.getInstance().getGlobalBackground());
        hud.addProperty("textShadow", HudSettings.getInstance().isTextShadow());
        JsonObject positions = new JsonObject();
        for (Map.Entry<String, int[]> e : HudSettings.getInstance().getPositions().entrySet()) {
            JsonArray xy = new JsonArray();
            xy.add(e.getValue()[0]);
            xy.add(e.getValue()[1]);
            positions.add(e.getKey(), xy);
        }
        hud.add("positions", positions);
        JsonArray fade = new JsonArray();
        for (int c : HudSettings.getInstance().getFadePlaylist()) {
            fade.add(c);
        }
        hud.add("fadePlaylist", fade);

        JsonArray lockedArr = new JsonArray();
        for (String n : HudSettings.getInstance().getLocked()) lockedArr.add(n);
        hud.add("locked", lockedArr);

        JsonArray groupsArr = new JsonArray();
        for (Set<String> g : HudSettings.getInstance().getGroups()) {
            JsonArray ga = new JsonArray();
            for (String n : g) ga.add(n);
            groupsArr.add(ga);
        }
        hud.add("groups", groupsArr);

        json.add("hud", hud);
        return json;
    }

    /** Apply a JSON snapshot to the live module + HUD state. */
    public static void applyJson(JsonObject json) {
        if (json == null) {
            return;
        }
        if (json.has("modules")) {
            JsonObject modulesJson = json.getAsJsonObject("modules");
            for (Module module : ModuleManager.getInstance().getModules()) {
                try {
                    if (!modulesJson.has(module.getName())) {
                        continue;
                    }
                    JsonObject moduleJson = modulesJson.getAsJsonObject(module.getName());
                    if (moduleJson.has("enabled")) {
                        module.setEnabled(moduleJson.get("enabled").getAsBoolean());
                    }
                    if (module instanceof HudModule) {
                        HudModule hm = (HudModule) module;
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
                            if (opts.has(o.getName())) {
                                o.load(opts.get(o.getName()));
                            }
                        }
                    }
                    if (moduleJson.has("favorite")) {
                        module.setFavorite(moduleJson.get("favorite").getAsBoolean());
                    }
                    if (moduleJson.has("lastModified")) {
                        module.setLastModified(moduleJson.get("lastModified").getAsLong());
                    }
                } catch (Exception ex) {
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
                    } catch (Exception ignored) {
                    }
                }
            }
            if (hud.has("fadePlaylist")) {
                HudSettings.getInstance().getFadePlaylist().clear();
                JsonArray fade = hud.getAsJsonArray("fadePlaylist");
                for (int i = 0; i < fade.size(); i++) {
                    HudSettings.getInstance().getFadePlaylist().add(fade.get(i).getAsInt());
                }
            }
            if (hud.has("locked")) {
                HudSettings.getInstance().getLocked().clear();
                JsonArray la = hud.getAsJsonArray("locked");
                for (int i = 0; i < la.size(); i++) HudSettings.getInstance().getLocked().add(la.get(i).getAsString());
            }
            if (hud.has("groups")) {
                HudSettings.getInstance().getGroups().clear();
                JsonArray ga = hud.getAsJsonArray("groups");
                for (int i = 0; i < ga.size(); i++) {
                    JsonArray grp = ga.get(i).getAsJsonArray();
                    Set<String> s = new HashSet<>();
                    for (int j = 0; j < grp.size(); j++) s.add(grp.get(j).getAsString());
                    if (!s.isEmpty()) HudSettings.getInstance().getGroups().add(s);
                }
            }
        }
    }

    public static void load() {
        File configFile = getConfigFile();
        if (!configFile.exists()) {
            return;
        }
        try (InputStreamReader reader = new InputStreamReader(
                new FileInputStream(configFile), StandardCharsets.UTF_8)) {
            applyJson(GSON.fromJson(reader, JsonObject.class));
        } catch (Exception e) {
            System.err.println("Failed to load config");
            e.printStackTrace();
        }
    }

    public static void save() {
        File configFile = getConfigFile();
        configFile.getParentFile().mkdirs();
        try (OutputStreamWriter writer = new OutputStreamWriter(
                new FileOutputStream(configFile), StandardCharsets.UTF_8)) {
            GSON.toJson(toJson(), writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
