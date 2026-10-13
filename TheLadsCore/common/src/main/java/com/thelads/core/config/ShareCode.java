package com.thelads.core.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.thelads.core.modules.HudModule;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * Shareable codes for HUD layouts and module configurations.
 * Format: "LADS1:" + base64url(deflate(json)).
 *
 * Security:
 *  - Rejects codes > 16 KB.
 *  - Caps inflate output at 256 KB (zip-bomb defense).
 *  - Strict JSON validation.
 *  - Ignores unmanaged modules and sensitive/machine-specific options.
 */
public final class ShareCode {
    public static final String PREFIX = "LADS1:";
    public static final int MAX_CODE_CHARS = 16 * 1024;       // 16 KB code string
    public static final int MAX_INFLATE_BYTES = 256 * 1024;    // 256 KB decompressed payload

    public enum ImportMode {
        MODS,
        SETTINGS,
        BOTH
    }

    private ShareCode() {}

    /**
     * Compress and encode a JSON payload into a self-contained share code string.
     */
    public static String encode(JsonObject payload) {
        if (payload == null) {
            throw new IllegalArgumentException("Payload cannot be null");
        }
        byte[] raw = payload.toString().getBytes(StandardCharsets.UTF_8);
        byte[] compressed = deflate(raw);
        return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(compressed);
    }

    /**
     * Decode and decompress a share code string into a validated JsonObject.
     * Tolerates leading/trailing/interstitial whitespace and newlines.
     */
    public static JsonObject decode(String code) {
        if (code == null) {
            throw new IllegalArgumentException("Share code cannot be null");
        }
        String cleaned = code.replaceAll("\\s+", "");
        if (cleaned.isEmpty()) {
            throw new IllegalArgumentException("Share code is empty");
        }
        if (cleaned.length() > MAX_CODE_CHARS) {
            throw new IllegalArgumentException("Share code exceeds maximum allowed size (16 KB)");
        }
        if (!cleaned.startsWith(PREFIX)) {
            throw new IllegalArgumentException("Invalid share code prefix; expected " + PREFIX);
        }

        String payloadB64 = cleaned.substring(PREFIX.length());
        if (payloadB64.isEmpty()) {
            throw new IllegalArgumentException("Share code payload is empty");
        }

        byte[] compressed;
        try {
            compressed = Base64.getUrlDecoder().decode(payloadB64);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid base64 encoding in share code: " + e.getMessage(), e);
        }

        byte[] decompressed;
        try {
            decompressed = inflate(compressed, MAX_INFLATE_BYTES);
        } catch (DataFormatException e) {
            throw new IllegalArgumentException("Corrupted or invalid compressed data: " + e.getMessage(), e);
        }

        String jsonText = new String(decompressed, StandardCharsets.UTF_8);
        try (JsonReader reader = new JsonReader(new StringReader(jsonText))) {
            reader.setLenient(false);
            JsonElement parsed = JsonParser.parseReader(reader);
            if (!parsed.isJsonObject()) {
                throw new IllegalArgumentException("Share code root must be a JSON object");
            }
            if (reader.peek() != JsonToken.END_DOCUMENT) {
                throw new IllegalArgumentException("Trailing characters after JSON object");
            }
            return parsed.getAsJsonObject();
        } catch (JsonParseException | IOException e) {
            throw new IllegalArgumentException("Malformed JSON in share code: " + e.getMessage(), e);
        }
    }

    /**
     * Builds a sanitized share payload {v:1, modules:{...}, hud:{...}} from full config.
     * Excludes personal/machine-specific data: file paths, account/server/Discord data,
     * keybinds, and unmanagedModules.
     */
    public static JsonObject createSharePayload(JsonObject fullConfig) {
        if (fullConfig == null) {
            fullConfig = ConfigManager.toJson();
        }
        JsonObject payload = new JsonObject();
        payload.addProperty("v", 1);

        JsonObject modulesOut = new JsonObject();
        JsonObject modulesIn = fullConfig.has("modules") && fullConfig.get("modules").isJsonObject()
            ? fullConfig.getAsJsonObject("modules")
            : new JsonObject();

        for (Module m : ModuleManager.getInstance().getModules()) {
            String name = m.getName();
            if (isExcludedModule(name)) {
                continue;
            }
            JsonObject srcObj = modulesIn.has(name) && modulesIn.get(name).isJsonObject()
                ? modulesIn.getAsJsonObject(name)
                : null;

            JsonObject mOut = new JsonObject();
            boolean enabled = m.isEnabled();
            if (srcObj != null && srcObj.has("enabled") && srcObj.get("enabled").isJsonPrimitive()) {
                JsonPrimitive p = srcObj.getAsJsonPrimitive("enabled");
                if (p.isBoolean()) {
                    enabled = p.getAsBoolean();
                }
            }
            mOut.addProperty("enabled", enabled);

            if (m instanceof HudModule hm) {
                boolean useGlobal = hm.isUseGlobalColor();
                int custom = hm.getCustomColor();
                if (srcObj != null) {
                    if (srcObj.has("useGlobalColor") && srcObj.get("useGlobalColor").isJsonPrimitive()) {
                        JsonPrimitive p = srcObj.getAsJsonPrimitive("useGlobalColor");
                        if (p.isBoolean()) {
                            useGlobal = p.getAsBoolean();
                        }
                    }
                    if (srcObj.has("customColor") && srcObj.get("customColor").isJsonPrimitive()) {
                        JsonPrimitive p = srcObj.getAsJsonPrimitive("customColor");
                        if (p.isNumber()) {
                            custom = p.getAsInt();
                        }
                    }
                }
                mOut.addProperty("useGlobalColor", useGlobal);
                mOut.addProperty("customColor", custom);
            }

            JsonObject optsIn = srcObj != null && srcObj.has("options") && srcObj.get("options").isJsonObject()
                ? srcObj.getAsJsonObject("options")
                : null;
            JsonObject optsOut = new JsonObject();
            if (optsIn != null) {
                for (Map.Entry<String, JsonElement> e : optsIn.entrySet()) {
                    String optName = e.getKey();
                    JsonElement optVal = e.getValue();
                    if (isExcludedOption(name, optName) || optVal == null || optVal.isJsonNull()) {
                        continue;
                    }
                    optsOut.add(optName, optVal.deepCopy());
                }
            } else {
                for (Option o : m.getOptions()) {
                    if (isExcludedOption(name, o.getName())) {
                        continue;
                    }
                    JsonElement saved = o.save();
                    if (saved != null && !saved.isJsonNull()) {
                        optsOut.add(o.getName(), saved);
                    }
                }
            }
            if (optsOut.size() > 0) {
                mOut.add("options", optsOut);
            }
            modulesOut.add(name, mOut);
        }
        payload.add("modules", modulesOut);

        if (fullConfig.has("hud") && fullConfig.get("hud").isJsonObject()) {
            payload.add("hud", fullConfig.getAsJsonObject("hud").deepCopy());
        }

        return payload;
    }

    /**
     * Pure function: transforms an imported payload into a partial JSON object
     * ready for ConfigManager.applyJson based on the selected ImportMode.
     */
    public static JsonObject prepareImportJson(JsonObject payload, ImportMode mode) {
        if (payload == null) {
            return new JsonObject();
        }
        if (mode == null) {
            mode = ImportMode.BOTH;
        }

        JsonObject out = new JsonObject();
        out.addProperty(SkinLayersLodMigration.FLAG, true);

        // HUD layout & appearances
        if ((mode == ImportMode.SETTINGS || mode == ImportMode.BOTH) && payload.has("hud") && payload.get("hud").isJsonObject()) {
            out.add("hud", payload.getAsJsonObject("hud").deepCopy());
        }

        // Modules
        if (payload.has("modules") && payload.get("modules").isJsonObject()) {
            JsonObject modulesIn = payload.getAsJsonObject("modules");
            JsonObject modulesOut = new JsonObject();

            Set<String> knownModules = new HashSet<>();
            for (Module m : ModuleManager.getInstance().getModules()) {
                knownModules.add(m.getName());
            }

            for (Map.Entry<String, JsonElement> entry : modulesIn.entrySet()) {
                String modName = entry.getKey();
                if (!knownModules.contains(modName) || isExcludedModule(modName)) {
                    continue; // Ignore unknown or excluded modules
                }
                if (!entry.getValue().isJsonObject()) {
                    continue;
                }
                JsonObject mIn = entry.getValue().getAsJsonObject();
                JsonObject mOut = new JsonObject();

                if (mode == ImportMode.MODS || mode == ImportMode.BOTH) {
                    if (mIn.has("enabled") && mIn.get("enabled").isJsonPrimitive()) {
                        JsonPrimitive p = mIn.getAsJsonPrimitive("enabled");
                        if (p.isBoolean()) {
                            mOut.addProperty("enabled", p.getAsBoolean());
                        }
                    }
                }

                if (mode == ImportMode.SETTINGS || mode == ImportMode.BOTH) {
                    if (mIn.has("useGlobalColor") && mIn.get("useGlobalColor").isJsonPrimitive()) {
                        JsonPrimitive p = mIn.getAsJsonPrimitive("useGlobalColor");
                        if (p.isBoolean()) {
                            mOut.addProperty("useGlobalColor", p.getAsBoolean());
                        }
                    }
                    if (mIn.has("customColor") && mIn.get("customColor").isJsonPrimitive()) {
                        JsonPrimitive p = mIn.getAsJsonPrimitive("customColor");
                        if (p.isNumber()) {
                            mOut.addProperty("customColor", p.getAsInt());
                        }
                    }
                    if (mIn.has("options") && mIn.get("options").isJsonObject()) {
                        JsonObject optsIn = mIn.getAsJsonObject("options");
                        JsonObject optsOut = new JsonObject();
                        for (Map.Entry<String, JsonElement> optEntry : optsIn.entrySet()) {
                            String optName = optEntry.getKey();
                            if (!isExcludedOption(modName, optName) && optEntry.getValue() != null && !optEntry.getValue().isJsonNull()) {
                                optsOut.add(optName, optEntry.getValue().deepCopy());
                            }
                        }
                        if (optsOut.size() > 0) {
                            mOut.add("options", optsOut);
                        }
                    }
                }

                if (mOut.size() > 0) {
                    modulesOut.add(modName, mOut);
                }
            }
            out.add("modules", modulesOut);
        }

        return out;
    }

    /**
     * Applies an imported payload: saves "Before import" snapshot, applies via applyJson, then saves.
     */
    public static void applySharePayload(JsonObject payload, ImportMode mode) {
        ProfileManager.get().saveAs("Before import");
        JsonObject prepared = prepareImportJson(payload, mode);
        ConfigManager.applyJson(prepared);
        ConfigManager.saveLater();
    }

    /**
     * Preview text describing the imported layout ("N modules, layout").
     */
    public static String summarize(JsonObject payload) {
        if (payload == null) {
            return "0 modules, layout";
        }
        int count = 0;
        if (payload.has("modules") && payload.get("modules").isJsonObject()) {
            count = payload.getAsJsonObject("modules").size();
        }
        return count + " modules, layout";
    }

    public static boolean isExcludedModule(String moduleName) {
        if (moduleName == null) return true;
        String lower = moduleName.toLowerCase(Locale.ROOT);
        return lower.equals("discordrpc") || lower.contains("discord");
    }

    public static boolean isExcludedOption(String moduleName, String optionName) {
        if (optionName == null) return false;
        String lower = optionName.toLowerCase(Locale.ROOT);
        // Excluded file paths
        if (lower.equals("background image") || lower.equals("replay folder")) {
            return true;
        }
        // Excluded personal account / identity
        if (lower.equals("your display name") || lower.equals("nicknames")) {
            return true;
        }
        // Excluded keybinds
        if (lower.contains("keybind") || lower.endsWith(" key") || lower.contains("hotkey")) {
            return true;
        }
        // Excluded secrets/credentials/servers
        if (lower.contains("password") || lower.contains("token") || lower.contains("secret") || lower.contains("api key") || lower.contains("server address")) {
            return true;
        }
        return false;
    }

    private static byte[] deflate(byte[] data) {
        Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION);
        deflater.setInput(data);
        deflater.finish();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        while (!deflater.finished()) {
            int count = deflater.deflate(buffer);
            baos.write(buffer, 0, count);
        }
        deflater.end();
        return baos.toByteArray();
    }

    private static byte[] inflate(byte[] compressed, int maxBytes) throws DataFormatException {
        Inflater inflater = new Inflater();
        inflater.setInput(compressed);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int totalBytes = 0;
        try {
            while (!inflater.finished()) {
                int count = inflater.inflate(buffer);
                if (count == 0) {
                    if (inflater.needsInput() || inflater.needsDictionary()) {
                        throw new IllegalArgumentException("Incomplete or truncated compressed data");
                    }
                    break;
                }
                totalBytes += count;
                if (totalBytes > maxBytes) {
                    throw new IllegalArgumentException("Decompressed data exceeds maximum allowed size (" + (maxBytes / 1024) + " KB)");
                }
                baos.write(buffer, 0, count);
            }
            return baos.toByteArray();
        } finally {
            inflater.end();
        }
    }
}
