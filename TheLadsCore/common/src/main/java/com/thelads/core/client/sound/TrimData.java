package com.thelads.core.client.sound;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Trim metadata saved with a sound cut from the trimmer: per-sound volume (0.0 to 2.0, default 1.0 = 100%).
 * Old saves without it default to 100%.
 */
public record TrimData(double volume) {
    public static final double DEFAULT_VOLUME = 1.0;
    public static final TrimData DEFAULT = new TrimData(DEFAULT_VOLUME);

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("volume", volume);
        return json;
    }

    public static TrimData fromJson(JsonObject json) {
        if (json == null) return DEFAULT;
        double vol = json.has("volume") ? json.get("volume").getAsDouble() : DEFAULT_VOLUME;
        return new TrimData(vol);
    }

    public void save(Path path) throws IOException {
        if (path.getParent() != null) Files.createDirectories(path.getParent());
        Files.writeString(path, toJson().toString());
    }

    public static TrimData load(Path path) {
        if (path == null || !Files.exists(path)) return DEFAULT;
        try {
            String content = Files.readString(path);
            if (content.isBlank()) return DEFAULT;
            return fromJson(JsonParser.parseString(content).getAsJsonObject());
        } catch (Exception ignored) {
            return DEFAULT;
        }
    }
}
