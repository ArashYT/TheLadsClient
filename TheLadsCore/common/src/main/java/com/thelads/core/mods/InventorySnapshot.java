package com.thelads.core.mods;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The launcher's inventory for this profile, {@code <gameDir>/.lads-mod-cache/inventory.json}, written before every launch.
 * It is the only source for entries Fabric cannot see: disabled jars, pending downloads, unavailable and retired entries.
 * Ownership and status are the launcher's camelCase enum names (core, nativeModule, pack, user, embedded, platform, retired /
 * installed, disabled, pendingDownload, notDownloaded, unavailable, unsupported, embedded, retiredCopy, invalid).
 */
public record InventorySnapshot(boolean present, String minecraftVersion, List<Entry> entries, String notice) {
    public static final String MISSING_NOTICE = "Launch from The Lads Launcher to see disabled and pending entries.";

    /**
     * depends holds dependency ids only (the launcher writes {@code "<id> <version predicate>"}; Fabric checks versions at launch).
     * dependenciesKnown is false for entries whose jar was never downloaded, so their dependencies are unknown.
     * filePath is set only for a jar that is in mods right now (enabledOnDisk tells whether it is a .disabled copy).
     */
    public record Entry(String id, String displayName, String upstreamName, String version, String fileName, String filePath, String ownership,
                        String status, boolean enabledOnDisk, boolean requestedEnabled, boolean canToggle, String toggleBlockedReason,
                        String projectId, String projectUrl, String license, List<String> authors, List<String> depends,
                        List<String> provides, boolean isLibrary, String note, String parentId, List<Entry> children,
                        boolean dependenciesKnown) {}

    public InventorySnapshot {
        entries = List.copyOf(entries);
    }

    public static Path file(Path gameDirectory) {
        return gameDirectory.resolve(".lads-mod-cache").resolve("inventory.json");
    }

    /** Never throws: a missing or unreadable snapshot yields no entries and a notice explaining what is not shown. */
    public static InventorySnapshot read(Path gameDirectory) {
        Path file = file(gameDirectory);
        try {
            return parse(Files.readString(file));
        } catch (NoSuchFileException missing) {
            return new InventorySnapshot(false, null, List.of(), MISSING_NOTICE);
        } catch (IOException | JsonParseException | IllegalStateException | UnsupportedOperationException e) {
            return new InventorySnapshot(false, null, List.of(),
                "The launcher's mod list could not be read (" + e.getMessage() + "). Showing loaded mods only; relaunch from The Lads Launcher.");
        }
    }

    public static InventorySnapshot parse(String json) {
        JsonElement root = JsonParser.parseString(json);
        if (!root.isJsonObject()) throw new JsonParseException("inventory.json is not a JSON object");
        JsonObject object = root.getAsJsonObject();
        List<Entry> entries = new ArrayList<>();
        for (JsonElement element : array(object, "entries")) entries.add(entry(element, null));
        return new InventorySnapshot(true, string(object, "minecraftVersion"), entries, null);
    }

    private static Entry entry(JsonElement element, String parentId) {
        if (!element.isJsonObject()) throw new JsonParseException("an inventory entry is not a JSON object");
        JsonObject o = element.getAsJsonObject();
        String id = string(o, "id");
        if (id == null || id.isBlank()) throw new JsonParseException("an inventory entry has no id");
        String parent = string(o, "parentId");
        List<Entry> children = new ArrayList<>();
        for (JsonElement child : array(o, "children")) children.add(entry(child, id));
        String status = string(o, "status");
        List<String> depends = strings(o, "depends").stream().map(InventorySnapshot::dependencyId).toList();
        // Older snapshots lack the flag: an entry that was never downloaded and lists no dependencies has unknown ones.
        boolean dependenciesKnown = o.has("dependenciesKnown") ? bool(o, "dependenciesKnown")
            : !depends.isEmpty() || !"pendingDownload".equals(status) && !"notDownloaded".equals(status);
        return new Entry(id, string(o, "displayName"), string(o, "upstreamName"), string(o, "version"), string(o, "fileName"),
            string(o, "filePath"), string(o, "ownership"), status, bool(o, "enabledOnDisk"), bool(o, "requestedEnabled"), bool(o, "canToggle"),
            string(o, "toggleBlockedReason"), string(o, "projectId"), string(o, "projectUrl"), string(o, "license"),
            strings(o, "authors"), depends, strings(o, "provides"), bool(o, "isLibrary"), string(o, "note"),
            parent != null ? parent : parentId, List.copyOf(children), dependenciesKnown);
    }

    // "fabric-api >=0.100.0" -> "fabric-api", as the launcher's planner parses it (Fabric ids never contain spaces).
    private static String dependencyId(String dependency) {
        String trimmed = dependency.strip();
        int space = trimmed.indexOf(' ');
        return space < 0 ? trimmed : trimmed.substring(0, space);
    }

    private static String string(JsonObject o, String key) {
        JsonElement value = o.get(key);
        return value == null || value.isJsonNull() ? null : value.getAsString();
    }

    private static boolean bool(JsonObject o, String key) {
        JsonElement value = o.get(key);
        return value != null && !value.isJsonNull() && value.getAsBoolean();
    }

    private static JsonArray array(JsonObject o, String key) {
        JsonElement value = o.get(key);
        return value == null || value.isJsonNull() ? new JsonArray() : value.getAsJsonArray();
    }

    private static List<String> strings(JsonObject o, String key) {
        List<String> result = new ArrayList<>();
        for (JsonElement value : array(o, key)) result.add(value.getAsString());
        return List.copyOf(result);
    }
}
