package com.thelads.core.client.pack;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.thelads.core.client.util.ClientPaths;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.LoggerFactory;

/**
 * Pack Profiles (OneClient's packed_packs profiles): named sets of selected resource packs, saved and applied from the resource pack
 * screen's Profiles button. A profile is the Selected list top first, by pack id; ids differ between game versions, so each version
 * keeps its own profiles in thelads_pack_profiles.json. Applying one makes it the Selected list and loads it, as the screen's Done does.
 */
public final class PackProfiles {
    public static final int MAX_NAME = 32;
    private static final String FILE = "thelads_pack_profiles.json";
    /** version -> profile name -> pack ids, top first. */
    private static Map<String, Map<String, List<String>>> profiles;

    private PackProfiles() {}

    public static synchronized List<String> names(String version) {
        return new ArrayList<>(version(version).keySet());
    }

    public static synchronized List<String> get(String version, String name) {
        List<String> ids = version(version).get(name);
        return ids == null ? List.of() : List.copyOf(ids);
    }

    /** Saves (or replaces) a profile; false for an empty or too long name. */
    public static synchronized boolean save(String version, String name, List<String> ids) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty() || trimmed.length() > MAX_NAME) return false;
        version(version).put(trimmed, List.copyOf(ids));
        write();
        return true;
    }

    public static synchronized void delete(String version, String name) {
        if (version(version).remove(name) != null) write();
    }

    /**
     * The Selected list (top first) a profile gives: its packs that still exist, in its order, then the required packs it left out
     * (vanilla's own, mod resources) in their order, so the game never loses them.
     */
    public static List<String> arrange(List<String> profile, Collection<String> available, List<String> required) {
        List<String> out = new ArrayList<>(profile.size() + required.size());
        for (String id : profile) if (available.contains(id) && !out.contains(id)) out.add(id);
        for (String id : required) if (!out.contains(id)) out.add(id);
        return out;
    }

    /** Reads the file again on next use (a test's own base dir). */
    public static synchronized void reload() { profiles = null; }

    private static Path file() { return ClientPaths.getBaseDir().resolve(FILE); }

    private static Map<String, List<String>> version(String version) {
        if (profiles == null) profiles = read(file());
        return profiles.computeIfAbsent(version, v -> new LinkedHashMap<>());
    }

    private static Map<String, Map<String, List<String>>> read(Path file) {
        Map<String, Map<String, List<String>>> out = new LinkedHashMap<>();
        try {
            if (!Files.isRegularFile(file)) return out;
            JsonElement root = JsonParser.parseString(Files.readString(file));
            if (!root.isJsonObject()) return out;
            for (var version : root.getAsJsonObject().entrySet()) {
                if (!version.getValue().isJsonObject()) continue;
                Map<String, List<String>> named = new LinkedHashMap<>();
                for (var profile : version.getValue().getAsJsonObject().entrySet()) {
                    if (!profile.getValue().isJsonArray()) continue;
                    List<String> ids = new ArrayList<>();
                    for (JsonElement id : profile.getValue().getAsJsonArray()) if (id.isJsonPrimitive()) ids.add(id.getAsString());
                    named.put(profile.getKey(), ids);
                }
                out.put(version.getKey(), named);
            }
        } catch (Exception e) {
            LoggerFactory.getLogger("TheLadsCore").warn("Could not read pack profiles; starting empty", e);
        }
        return out;
    }

    private static void write() {
        JsonObject root = new JsonObject();
        for (var version : profiles.entrySet()) {
            JsonObject named = new JsonObject();
            for (var profile : version.getValue().entrySet()) {
                JsonArray ids = new JsonArray();
                profile.getValue().forEach(ids::add);
                named.add(profile.getKey(), ids);
            }
            root.add(version.getKey(), named);
        }
        Path file = file(), temp = null;
        try {
            Files.createDirectories(file.getParent());
            temp = Files.createTempFile(file.getParent(), "pack-profiles-", ".tmp");
            Files.writeString(temp, root.toString());
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            temp = null;
        } catch (Exception e) {
            LoggerFactory.getLogger("TheLadsCore").warn("Could not save pack profiles", e);
        } finally {
            if (temp != null) try { Files.deleteIfExists(temp); } catch (Exception ignored) {}
        }
    }
}
