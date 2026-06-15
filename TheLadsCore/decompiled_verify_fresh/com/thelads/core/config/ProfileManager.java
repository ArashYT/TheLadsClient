/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.google.gson.Gson
 *  com.google.gson.GsonBuilder
 *  com.google.gson.JsonElement
 *  com.google.gson.JsonObject
 *  net.fabricmc.loader.api.FabricLoader
 */
package com.thelads.core.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.thelads.core.config.ConfigManager;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;

public class ProfileManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final ProfileManager INSTANCE = new ProfileManager();
    private final LinkedHashMap<String, JsonObject> profiles = new LinkedHashMap();
    private final LinkedHashMap<String, String> bindings = new LinkedHashMap();
    private String lastContext = null;

    public static ProfileManager get() {
        return INSTANCE;
    }

    private File file() {
        return new File(FabricLoader.getInstance().getConfigDir().toFile(), "thelads_profiles.json");
    }

    public List<String> getProfileNames() {
        return new ArrayList<String>(this.profiles.keySet());
    }

    public Map<String, String> getBindings() {
        return this.bindings;
    }

    public boolean hasProfile(String name) {
        return this.profiles.containsKey(name);
    }

    public void saveAs(String name) {
        this.profiles.put(name, ConfigManager.toJson());
        this.persist();
    }

    public void apply(String name) {
        JsonObject snap = this.profiles.get(name);
        if (snap != null) {
            ConfigManager.applyJson(snap);
            ConfigManager.save();
        }
    }

    public void delete(String name) {
        this.profiles.remove(name);
        this.bindings.values().removeIf(name::equals);
        this.persist();
    }

    public String nextDefaultName() {
        int n = 1;
        while (this.profiles.containsKey("Profile " + n)) {
            ++n;
        }
        return "Profile " + n;
    }

    public void bind(String contextKey, String profileName) {
        if (profileName == null) {
            this.bindings.remove(contextKey);
        } else {
            this.bindings.put(contextKey, profileName);
        }
        this.persist();
    }

    public String getBinding(String contextKey) {
        return this.bindings.get(contextKey);
    }

    public void onContext(String contextKey) {
        if (contextKey == null || contextKey.equals(this.lastContext)) {
            return;
        }
        this.lastContext = contextKey;
        String prof = this.bindings.get(contextKey);
        if (prof != null && this.profiles.containsKey(prof)) {
            ConfigManager.applyJson(this.profiles.get(prof));
            ConfigManager.save();
        }
    }

    public void load() {
        File f = this.file();
        if (!f.exists()) {
            return;
        }
        try (FileReader r = new FileReader(f);){
            JsonObject json = (JsonObject)GSON.fromJson((Reader)r, JsonObject.class);
            if (json == null) {
                return;
            }
            this.profiles.clear();
            this.bindings.clear();
            if (json.has("profiles")) {
                JsonObject p = json.getAsJsonObject("profiles");
                for (String k : p.keySet()) {
                    this.profiles.put(k, p.getAsJsonObject(k));
                }
            }
            if (json.has("bindings")) {
                JsonObject b = json.getAsJsonObject("bindings");
                for (String k : b.keySet()) {
                    this.bindings.put(k, b.get(k).getAsString());
                }
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void persist() {
        File f = this.file();
        f.getParentFile().mkdirs();
        JsonObject json = new JsonObject();
        JsonObject p = new JsonObject();
        for (Map.Entry<String, JsonObject> entry : this.profiles.entrySet()) {
            p.add(entry.getKey(), (JsonElement)entry.getValue());
        }
        json.add("profiles", (JsonElement)p);
        JsonObject b = new JsonObject();
        for (Map.Entry<String, String> entry : this.bindings.entrySet()) {
            b.addProperty(entry.getKey(), entry.getValue());
        }
        json.add("bindings", (JsonElement)b);
        try (FileWriter fileWriter = new FileWriter(f);){
            GSON.toJson((JsonElement)json, (Appendable)fileWriter);
        }
        catch (IOException iOException) {
            iOException.printStackTrace();
        }
    }
}

