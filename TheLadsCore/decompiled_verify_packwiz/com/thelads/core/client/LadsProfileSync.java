/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.google.gson.Gson
 *  com.google.gson.JsonObject
 *  net.minecraft.client.Minecraft
 *  net.minecraft.client.User
 */
package com.thelads.core.client;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.thelads.core.mixin.MinecraftClientAccessor;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;

public class LadsProfileSync {
    private static final Gson GSON = new Gson();
    private static final Path PROFILE_PATH = Path.of("C:/The Lads Client/lads_profile.json", new String[0]);
    private static volatile LadsProfile cached;
    private static volatile long lastModified;

    public static LadsProfile read() {
        try {
            if (!Files.exists(PROFILE_PATH, new LinkOption[0])) {
                return null;
            }
            long mod = Files.getLastModifiedTime(PROFILE_PATH, new LinkOption[0]).toMillis();
            if (mod == lastModified && cached != null) {
                return cached;
            }
            String json = Files.readString(PROFILE_PATH);
            JsonObject obj = (JsonObject)GSON.fromJson(json, JsonObject.class);
            if (obj == null) {
                return null;
            }
            cached = new LadsProfile(LadsProfileSync.str(obj, "username"), LadsProfileSync.str(obj, "uuid"), obj.has("type") ? obj.get("type").getAsString() : "offline", LadsProfileSync.str(obj, "accessToken"), LadsProfileSync.str(obj, "skinUrl"), LadsProfileSync.str(obj, "capeUrl"));
            lastModified = mod;
            return cached;
        }
        catch (IOException e) {
            return null;
        }
    }

    public static LadsProfile getCached() {
        return cached;
    }

    public static void applyToSession(Minecraft mc) {
        LadsProfile p = LadsProfileSync.read();
        if (p == null || p.username() == null || p.username().isBlank()) {
            return;
        }
        mc.execute(() -> {
            try {
                UUID uuid = LadsProfileSync.parseUuid(p);
                String token = p.accessToken() != null && !p.accessToken().isBlank() ? p.accessToken() : "0";
                User user = new User(p.username(), uuid, token, Optional.empty(), Optional.empty());
                ((MinecraftClientAccessor)mc).setUser(user);
            }
            catch (Exception exception) {
                // empty catch block
            }
        });
    }

    public static boolean applyIfChanged(Minecraft mc) {
        try {
            if (!Files.exists(PROFILE_PATH, new LinkOption[0])) {
                return false;
            }
            long mod = Files.getLastModifiedTime(PROFILE_PATH, new LinkOption[0]).toMillis();
            if (mod == lastModified) {
                return false;
            }
            LadsProfileSync.applyToSession(mc);
            return true;
        }
        catch (IOException e) {
            return false;
        }
    }

    private static UUID parseUuid(LadsProfile p) {
        if (p.uuid() != null && !p.uuid().isBlank()) {
            try {
                String id = p.uuid().replace("-", "");
                return UUID.fromString(id.replaceFirst("(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}+)", "$1-$2-$3-$4-$5"));
            }
            catch (IllegalArgumentException illegalArgumentException) {
                // empty catch block
            }
        }
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + p.username()).getBytes());
    }

    private static String str(JsonObject o, String key) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : null;
    }

    static {
        lastModified = -1L;
    }

    public record LadsProfile(String username, String uuid, String type, String accessToken, String skinUrl, String capeUrl) {
    }
}

