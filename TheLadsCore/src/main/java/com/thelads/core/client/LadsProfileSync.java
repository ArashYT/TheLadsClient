package com.thelads.core.client;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import com.thelads.core.mixin.MinecraftClientAccessor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads lads_profile.json written by the launcher on login/account switch
 * and applies it to the active Minecraft session.
 *
 * File location: C:\The Lads Client\lads_profile.json
 * Format: { username, uuid, type, accessToken, skinUrl, capeUrl, lastUpdated }
 */
public class LadsProfileSync {

    public record LadsProfile(
        String username,
        String uuid,
        String type,
        String accessToken,
        String skinUrl,
        String capeUrl
    ) {}

    private static final Gson GSON = new Gson();
    private static final Path PROFILE_PATH = Path.of("C:/The Lads Client/lads_profile.json");

    private static volatile LadsProfile cached;
    private static volatile long lastModified = -1;

    /** Read + cache the profile; returns null if missing or malformed. */
    public static LadsProfile read() {
        try {
            if (!Files.exists(PROFILE_PATH)) return null;
            long mod = Files.getLastModifiedTime(PROFILE_PATH).toMillis();
            if (mod == lastModified && cached != null) return cached;
            String json = Files.readString(PROFILE_PATH);
            JsonObject obj = GSON.fromJson(json, JsonObject.class);
            if (obj == null) return null;
            cached = new LadsProfile(
                str(obj, "username"),
                str(obj, "uuid"),
                obj.has("type") ? obj.get("type").getAsString() : "offline",
                str(obj, "accessToken"),
                str(obj, "skinUrl"),
                str(obj, "capeUrl")
            );
            lastModified = mod;
            return cached;
        } catch (IOException e) {
            return null;
        }
    }

    public static LadsProfile getCached() { return cached; }

    /**
     * Apply the launcher profile to the live Minecraft User object.
     * Safe to call from any thread (posts work to the main thread via mc.execute).
     */
    public static void applyToSession(Minecraft mc) {
        LadsProfile p = read();
        if (p == null || p.username() == null || p.username().isBlank()) return;
        mc.execute(() -> {
            try {
                UUID uuid = parseUuid(p);
                String token = (p.accessToken() != null && !p.accessToken().isBlank())
                    ? p.accessToken() : "0";
                User user = new User(p.username(), uuid, token, Optional.empty(), Optional.empty());
                ((MinecraftClientAccessor) mc).setUser(user);
            } catch (Exception ignored) {}
        });
    }

    /** Re-apply if the profile file was updated since the last read. */
    public static boolean applyIfChanged(Minecraft mc) {
        try {
            if (!Files.exists(PROFILE_PATH)) return false;
            long mod = Files.getLastModifiedTime(PROFILE_PATH).toMillis();
            if (mod == lastModified) return false;
            applyToSession(mc);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private static UUID parseUuid(LadsProfile p) {
        if (p.uuid() != null && !p.uuid().isBlank()) {
            try {
                String id = p.uuid().replace("-", "");
                return UUID.fromString(
                    id.replaceFirst(
                        "(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}+)",
                        "$1-$2-$3-$4-$5"
                    )
                );
            } catch (IllegalArgumentException ignored) {}
        }
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + p.username()).getBytes());
    }

    private static String str(JsonObject o, String key) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : null;
    }
}
