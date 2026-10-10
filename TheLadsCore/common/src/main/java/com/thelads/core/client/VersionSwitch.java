package com.thelads.core.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.thelads.core.client.util.ClientPaths;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * "Switch versions" hand-off between the game and the launcher, mirroring the account switch
 * (lads_next_account.json): the launcher writes {@link #VERSIONS_FILE} next to the accounts file at every launch, plain profile
 * or Zero SMP alike ({"current":"26.3","versions":["26.3","1.20.1","1.8.9"]}; "current" is "Zero SMP" in the modpack); the game
 * writes {@link #REQUEST_FILE} ({"version":"1.8.9","requested":"..."}) and quits; the launcher reads it when the game exits and
 * starts that version.
 */
public final class VersionSwitch {
    public static final String VERSIONS_FILE = "lads_versions.json";
    public static final String REQUEST_FILE = "lads_next_version.json";
    /** What the launcher's Play screen offers, newest first (26.2 is dropped): the list when the launcher wrote none (a dev run). */
    public static final List<String> SUPPORTED = Collections.unmodifiableList(Arrays.asList("26.3", "1.20.1", "1.8.9"));

    private VersionSwitch() {}

    /** The running version as the launcher names it ("26.3", "Zero SMP"); {@code running} (this adapter's own) when the launcher wrote no file. */
    public static String current(String running) {
        return current(ClientPaths.getBaseDir(), running);
    }

    public static String current(Path dir, String running) {
        JsonObject root = read(dir);
        return root != null && root.has("current") ? root.get("current").getAsString() : running;
    }

    /** The versions the player can switch to, in the launcher's order, without the running one. */
    public static List<String> available(String running) {
        return available(ClientPaths.getBaseDir(), running);
    }

    public static List<String> available(Path dir, String running) {
        JsonObject root = read(dir);
        boolean listed = root != null && root.has("versions") && root.get("versions").isJsonArray();
        String current = current(dir, running);
        List<String> candidates = new ArrayList<>();
        if (listed) for (JsonElement e : root.getAsJsonArray("versions")) candidates.add(e.isJsonPrimitive() ? e.getAsString() : "");
        else candidates.addAll(SUPPORTED);
        List<String> out = new ArrayList<>();
        for (String candidate : candidates) {
            String v = candidate.trim();
            if (!v.isEmpty() && !v.equals(current) && !out.contains(v)) out.add(v);
        }
        return out;
    }

    /** Writes the request atomically; the caller then quits the game. Returns false when nothing was written. */
    public static boolean request(String version) {
        return request(ClientPaths.getBaseDir(), version);
    }

    public static boolean request(Path dir, String version) {
        if (version == null || version.trim().isEmpty()) return false;
        try {
            JsonObject obj = new JsonObject();
            obj.addProperty("version", version.trim());
            obj.addProperty("requested", Instant.now().toString());
            Files.createDirectories(dir);
            Path target = dir.resolve(REQUEST_FILE);
            Path temporary = Files.createTempFile(dir, "next-version-", ".tmp");
            try {
                Files.write(temporary, obj.toString().getBytes(StandardCharsets.UTF_8));
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            } finally {
                Files.deleteIfExists(temporary);
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static JsonObject read(Path dir) {
        try {
            Path file = dir.resolve(VERSIONS_FILE);
            if (!Files.isRegularFile(file)) return null;
            JsonElement e = JsonParser.parseString(new String(Files.readAllBytes(file), StandardCharsets.UTF_8));
            return e.isJsonObject() ? e.getAsJsonObject() : null;
        } catch (Exception e) {
            return null;
        }
    }
}
