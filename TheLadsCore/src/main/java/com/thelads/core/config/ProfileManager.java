package com.thelads.core.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Named HUD/module profiles. A profile is a full config snapshot (via
 * ConfigManager). Profiles can be applied manually or auto-applied when the
 * player enters a bound context (a specific server or singleplayer world);
 * unbound profiles are just "general" presets. Stored in thelads_profiles.json.
 */
public class ProfileManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final ProfileManager INSTANCE = new ProfileManager();

    private final LinkedHashMap<String, JsonObject> profiles = new LinkedHashMap<>();
    private final LinkedHashMap<String, String> bindings = new LinkedHashMap<>(); // context key -> profile name
    private String lastContext = null;

    public static ProfileManager get() {
        return INSTANCE;
    }

    private File file() {
        return new File(FabricLoader.getInstance().getConfigDir().toFile(), "thelads_profiles.json");
    }

    public List<String> getProfileNames() {
        return new ArrayList<>(profiles.keySet());
    }

    public Map<String, String> getBindings() {
        return bindings;
    }

    public boolean hasProfile(String name) {
        return profiles.containsKey(name);
    }

    /** Snapshot the current config under a name. */
    public void saveAs(String name) {
        profiles.put(name, ConfigManager.toJson());
        persist();
    }

    /** Apply a stored profile to the live config. */
    public void apply(String name) {
        JsonObject snap = profiles.get(name);
        if (snap != null) {
            ConfigManager.applyJson(snap);
            ConfigManager.save();
        }
    }

    public void delete(String name) {
        profiles.remove(name);
        bindings.values().removeIf(name::equals);
        persist();
    }

    public String nextDefaultName() {
        int n = 1;
        while (profiles.containsKey("Profile " + n)) {
            n++;
        }
        return "Profile " + n;
    }

    public void bind(String contextKey, String profileName) {
        if (profileName == null) {
            bindings.remove(contextKey);
        } else {
            bindings.put(contextKey, profileName);
        }
        persist();
    }

    public String getBinding(String contextKey) {
        return bindings.get(contextKey);
    }

    /** Apply the bound profile when the player's context changes (called from client tick). */
    public void onContext(String contextKey) {
        if (contextKey == null || contextKey.equals(lastContext)) {
            return;
        }
        lastContext = contextKey;
        String prof = bindings.get(contextKey);
        if (prof != null && profiles.containsKey(prof)) {
            ConfigManager.applyJson(profiles.get(prof));
            ConfigManager.save();
        }
    }

    public void load() {
        File f = file();
        if (!f.exists()) {
            return;
        }
        try (FileReader r = new FileReader(f)) {
            JsonObject json = GSON.fromJson(r, JsonObject.class);
            if (json == null) {
                return;
            }
            profiles.clear();
            bindings.clear();
            if (json.has("profiles")) {
                JsonObject p = json.getAsJsonObject("profiles");
                for (String k : p.keySet()) {
                    profiles.put(k, p.getAsJsonObject(k));
                }
            }
            if (json.has("bindings")) {
                JsonObject b = json.getAsJsonObject("bindings");
                for (String k : b.keySet()) {
                    bindings.put(k, b.get(k).getAsString());
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void persist() {
        File f = file();
        f.getParentFile().mkdirs();
        JsonObject json = new JsonObject();
        JsonObject p = new JsonObject();
        for (Map.Entry<String, JsonObject> e : profiles.entrySet()) {
            p.add(e.getKey(), e.getValue());
        }
        json.add("profiles", p);
        JsonObject b = new JsonObject();
        for (Map.Entry<String, String> e : bindings.entrySet()) {
            b.addProperty(e.getKey(), e.getValue());
        }
        json.add("bindings", b);
        try (FileWriter w = new FileWriter(f)) {
            GSON.toJson(json, w);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
