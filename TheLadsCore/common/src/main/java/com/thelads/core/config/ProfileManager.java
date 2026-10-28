package com.thelads.core.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.thelads.core.client.util.ClientPaths;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
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
    private File testFile = null;
    private boolean loaded = false;

    public static ProfileManager get() {
        return INSTANCE;
    }

    public void setTestFile(File f) {
        this.testFile = f;
        this.loaded = false;
        this.profiles.clear();
        this.bindings.clear();
    }

    public void ensureLoaded() {
        if (!loaded) {
            loaded = true;
            load();
        }
    }

    private File file() {
        if (testFile != null) {
            return testFile;
        }
        return ClientPaths.getProfilesFile();
    }

    public List<String> getProfileNames() {
        ensureLoaded();
        return new ArrayList<>(profiles.keySet());
    }

    public Map<String, String> getBindings() {
        ensureLoaded();
        return bindings;
    }

    public boolean hasProfile(String name) {
        ensureLoaded();
        return profiles.containsKey(name);
    }

    public JsonObject getProfile(String name) {
        ensureLoaded();
        return profiles.get(name);
    }

    /** Snapshot the current config under a name. */
    public void saveAs(String name) {
        ensureLoaded();
        profiles.put(name, ConfigManager.toJson());
        persist();
    }

    /** Apply a stored profile to the live config (all modules and settings). */
    public void apply(String name) {
        apply(name, ShareCode.ImportMode.BOTH);
    }

    /** Apply a stored profile with the specified ImportMode (Mods, Settings, or Both). */
    public void apply(String name, ShareCode.ImportMode mode) {
        ensureLoaded();
        JsonObject snap = profiles.get(name);
        if (snap != null) {
            saveAs("Before import");
            if (mode == null || mode == ShareCode.ImportMode.BOTH) {
                ConfigManager.applyJson(snap);
            } else {
                JsonObject prepared = ShareCode.prepareImportJson(snap, mode, false);
                ConfigManager.applyJson(prepared);
            }
            ConfigManager.saveLater();
        }
    }

    public boolean rename(String oldName, String newName) {
        ensureLoaded();
        if (oldName == null || newName == null || newName.isBlank() || !profiles.containsKey(oldName)
                || (profiles.containsKey(newName) && !oldName.equals(newName))) {
            return false;
        }
        if (oldName.equals(newName)) {
            return true;
        }
        LinkedHashMap<String, JsonObject> updated = new LinkedHashMap<>();
        for (Map.Entry<String, JsonObject> e : profiles.entrySet()) {
            if (e.getKey().equals(oldName)) {
                updated.put(newName, e.getValue());
            } else {
                updated.put(e.getKey(), e.getValue());
            }
        }
        profiles.clear();
        profiles.putAll(updated);
        for (Map.Entry<String, String> e : bindings.entrySet()) {
            if (oldName.equals(e.getValue())) {
                e.setValue(newName);
            }
        }
        persist();
        return true;
    }

    public void delete(String name) {
        ensureLoaded();
        profiles.remove(name);
        bindings.values().removeIf(name::equals);
        persist();
    }

    public String nextDefaultName() {
        ensureLoaded();
        int n = 1;
        while (profiles.containsKey("Profile " + n)) {
            n++;
        }
        return "Profile " + n;
    }

    public void bind(String contextKey, String profileName) {
        ensureLoaded();
        if (profileName == null) {
            bindings.remove(contextKey);
        } else {
            bindings.put(contextKey, profileName);
        }
        persist();
    }

    public String getBinding(String contextKey) {
        ensureLoaded();
        return bindings.get(contextKey);
    }

    /** Apply the bound profile when the player's context changes (called from client tick). */
    public void onContext(String contextKey) {
        if (contextKey == null || contextKey.equals(lastContext)) {
            return;
        }
        lastContext = contextKey;
        ensureLoaded();
        String prof = bindings.get(contextKey);
        if (prof != null && profiles.containsKey(prof)) {
            ConfigManager.applyJson(profiles.get(prof));
            ConfigManager.saveLater();
        }
    }

    public void save() {
        persist();
    }

    public void saveLater() {
        Thread writer = new Thread(this::persist, "Lads profile writer");
        writer.setDaemon(true);
        writer.start();
    }

    public void load() {
        loaded = true;
        profiles.clear();
        bindings.clear();
        File f = file();
        if (!f.exists()) {
            return;
        }
        try (BufferedReader r = Files.newBufferedReader(f.toPath(), StandardCharsets.UTF_8)) {
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

    private synchronized void persist() {
        File f = file();
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
        String content = GSON.toJson(json);
        writeFileAtomically(f, content);
        if (testFile == null) {
            try {
                java.nio.file.Path primaryPath = f.toPath().toAbsolutePath().normalize();
                for (File target : ClientPaths.getMirrorTargetsForSharedFile("thelads_profiles.json")) {
                    java.nio.file.Path targetPath = target.toPath().toAbsolutePath().normalize();
                    if (!targetPath.equals(primaryPath)) {
                        writeFileAtomically(target, content);
                    }
                }
            } catch (Exception ignored) {}
        }
    }

    private static void writeFileAtomically(File f, String content) {
        if (f == null) return;
        if (f.getParentFile() != null) {
            f.getParentFile().mkdirs();
        }
        File temp = new File(f.getParentFile(), f.getName() + ".tmp");
        try (BufferedWriter w = Files.newBufferedWriter(temp.toPath(), StandardCharsets.UTF_8)) {
            w.write(content);
            w.flush();
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }
        try {
            java.nio.file.Files.move(temp.toPath(), f.toPath(),
                java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            try {
                java.nio.file.Files.move(temp.toPath(), f.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e2) {
                e2.printStackTrace();
            }
        }
    }
}
