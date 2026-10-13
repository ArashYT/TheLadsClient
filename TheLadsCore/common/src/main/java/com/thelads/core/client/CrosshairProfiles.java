package com.thelads.core.client;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.thelads.core.client.util.ClientPaths;
import com.thelads.core.config.ConfigManager;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.Option;
import com.thelads.core.modules.CrosshairModule;
import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.LoggerFactory;

/**
 * Saved crosshair presets. A profile encapsulates all CrosshairModule options and
 * the custom pixel drawing (crosshair-drawing.json). Stored in thelads_crosshair_profiles.json
 * next to the config.
 */
public final class CrosshairProfiles {
    private static final Map<String, JsonObject> profiles = new LinkedHashMap<>();
    private static boolean loaded = false;
    private static CrosshairModule customModule = null;

    private CrosshairProfiles() {}

    public static void setModuleForTest(CrosshairModule module) {
        customModule = module;
    }

    public static CrosshairModule getModule() {
        if (customModule != null) {
            return customModule;
        }
        var m = ModuleManager.getInstance().getModule("Crosshair Tweaks");
        return m instanceof CrosshairModule cm ? cm : null;
    }

    public static String validateName(String name) {
        if (name == null) return null;
        String trimmed = name.trim();
        if (trimmed.isEmpty() || trimmed.length() > 32) return null;
        return trimmed;
    }

    private static synchronized void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        profiles.clear();
        File file = ClientPaths.getCrosshairProfilesFile();
        if (!file.exists() || !file.isFile()) {
            return;
        }
        try {
            String content = Files.readString(file.toPath());
            if (content.isBlank()) return;
            JsonElement parsed = JsonParser.parseString(content);
            if (!parsed.isJsonObject()) return;
            JsonObject root = parsed.getAsJsonObject();
            JsonObject p = root.has("profiles") && root.get("profiles").isJsonObject()
                ? root.getAsJsonObject("profiles")
                : root;
            for (String key : p.keySet()) {
                JsonElement val = p.get(key);
                if (val != null && val.isJsonObject()) {
                    profiles.put(key, val.getAsJsonObject());
                }
            }
        } catch (Exception e) {
            LoggerFactory.getLogger("TheLadsCore").warn("Could not read crosshair profiles; using empty list", e);
            profiles.clear();
        }
    }

    public static synchronized void reload() {
        loaded = false;
        ensureLoaded();
    }

    private static synchronized void persist() {
        File file = ClientPaths.getCrosshairProfilesFile();
        if (file.getParentFile() != null) {
            file.getParentFile().mkdirs();
        }
        JsonObject root = new JsonObject();
        JsonObject p = new JsonObject();
        for (Map.Entry<String, JsonObject> entry : profiles.entrySet()) {
            p.add(entry.getKey(), entry.getValue());
        }
        root.add("profiles", p);
        Path path = file.toPath();
        Path parent = path.getParent() != null ? path.getParent() : Path.of(".");
        try {
            Path temp = Files.createTempFile(parent, "crosshair-profiles-", ".tmp");
            try {
                Files.writeString(temp, new GsonBuilder().setPrettyPrinting().create().toJson(root));
                try {
                    Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(temp);
            }
        } catch (IOException e) {
            LoggerFactory.getLogger("TheLadsCore").warn("Could not write crosshair profiles", e);
        }
    }

    public static synchronized List<String> list() {
        ensureLoaded();
        return new ArrayList<>(profiles.keySet());
    }

    public static synchronized boolean has(String name) {
        ensureLoaded();
        return findExistingKey(name) != null;
    }

    public static synchronized JsonObject get(String name) {
        ensureLoaded();
        String key = findExistingKey(name);
        return key != null ? profiles.get(key) : null;
    }

    private static String findExistingKey(String name) {
        if (name == null) return null;
        String trimmed = name.trim();
        for (String k : profiles.keySet()) {
            if (k.equalsIgnoreCase(trimmed)) return k;
        }
        return null;
    }

    public static synchronized boolean save(String name) {
        String valid = validateName(name);
        if (valid == null) return false;
        ensureLoaded();

        CrosshairModule module = getModule();
        JsonObject optionsObj = new JsonObject();
        if (module != null) {
            for (Option opt : module.getOptions()) {
                if (opt instanceof CrosshairModule.ProfilesOption) continue;
                optionsObj.add(opt.getName(), opt.save());
            }
        }

        CrosshairDrawing drawing = CrosshairDrawingStore.load();
        JsonObject drawingObj = new JsonObject();
        drawingObj.addProperty("width", drawing.width());
        drawingObj.addProperty("height", drawing.height());
        JsonArray rowsArray = new JsonArray();
        for (String row : drawing.rows()) {
            rowsArray.add(new JsonPrimitive(row));
        }
        drawingObj.add("rows", rowsArray);

        JsonObject profileObj = new JsonObject();
        profileObj.add("options", optionsObj);
        profileObj.add("drawing", drawingObj);

        String existingKey = findExistingKey(valid);
        if (existingKey != null) {
            profiles.remove(existingKey);
        }
        profiles.put(valid, profileObj);
        persist();
        return true;
    }

    public static synchronized boolean apply(String name) {
        ensureLoaded();
        String existingKey = findExistingKey(name);
        if (existingKey == null) return false;
        JsonObject profileObj = profiles.get(existingKey);
        if (profileObj == null) return false;

        // 1. Load options
        CrosshairModule module = getModule();
        if (module != null && profileObj.has("options") && profileObj.get("options").isJsonObject()) {
            JsonObject opts = profileObj.getAsJsonObject("options");
            for (Option opt : module.getOptions()) {
                if (opt instanceof CrosshairModule.ProfilesOption) continue;
                if (opts.has(opt.getName())) {
                    try {
                        opt.load(opts.get(opt.getName()));
                    } catch (Exception ignored) {
                        // Corrupt/unknown option keys are ignored, never crash
                    }
                }
            }
        }
        if (module != null) {
            module.profiles.setSelected(existingKey);
        }

        // 2. Load drawing
        if (profileObj.has("drawing") && profileObj.get("drawing").isJsonObject()) {
            try {
                JsonObject d = profileObj.getAsJsonObject("drawing");
                int width = d.has("width") ? d.get("width").getAsInt() : 17;
                int height = d.has("height") ? d.get("height").getAsInt() : 17;
                List<String> rows = new ArrayList<>();
                if (d.has("rows") && d.get("rows").isJsonArray()) {
                    for (JsonElement r : d.getAsJsonArray("rows")) {
                        rows.add(r.getAsString());
                    }
                }
                CrosshairDrawing restored = CrosshairDrawing.fromRows(width, height, rows);
                CrosshairDrawingStore.save(restored);
            } catch (Exception ignored) {}
        }

        // 3. Save config later
        ConfigManager.saveLater();

        // 4. Refresh renderer cached drawing
        CrosshairDrawingStore.notifyDrawingChanged();

        return true;
    }

    public static synchronized boolean rename(String oldName, String newName) {
        ensureLoaded();
        String oldValid = validateName(oldName);
        String newValid = validateName(newName);
        if (oldValid == null || newValid == null) return false;

        String existingOld = findExistingKey(oldValid);
        if (existingOld == null) return false;

        String existingNew = findExistingKey(newValid);
        if (existingNew != null && !existingNew.equalsIgnoreCase(existingOld)) {
            return false;
        }

        LinkedHashMap<String, JsonObject> updated = new LinkedHashMap<>();
        for (Map.Entry<String, JsonObject> entry : profiles.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(oldValid)) {
                updated.put(newValid, entry.getValue());
            } else {
                updated.put(entry.getKey(), entry.getValue());
            }
        }
        profiles.clear();
        profiles.putAll(updated);
        persist();
        return true;
    }

    public static synchronized boolean delete(String name) {
        ensureLoaded();
        String existingKey = findExistingKey(name);
        if (existingKey == null) return false;
        profiles.remove(existingKey);
        persist();
        return true;
    }

    public static synchronized String nextDefaultName() {
        ensureLoaded();
        int n = 1;
        while (has("Profile " + n)) {
            n++;
        }
        return "Profile " + n;
    }
}
