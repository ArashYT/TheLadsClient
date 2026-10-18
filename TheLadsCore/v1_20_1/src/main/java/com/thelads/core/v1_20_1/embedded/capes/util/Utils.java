// Ported from Capes 1.5.10+26.3 by Cael (LGPL-2.1-only) from Kotlin to Java; modified by The Lads: ported to Minecraft 1.20.1 from the Lads 26.3 embedded copy.
package com.thelads.core.v1_20_1.embedded.capes.util;

import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;

public final class Utils {
    private Utils() {
    }

    /** Upstream extension {@code GameProfile.isValidProfile()}. */
    /** 1.9.12 perf: textures property value -> its profileName ("" = none); decoded once instead of on every skin read. */
    private static final java.util.Map<String, String> PROFILE_NAMES = new java.util.concurrent.ConcurrentHashMap<>();

    public static boolean isValidProfile(GameProfile profile) {
        String profileName;
        try { // runCatching { ... }.getOrNull()
            String textures = profile.getProperties().get("textures").stream().findFirst().get().getValue();
            if (PROFILE_NAMES.size() > 4096) PROFILE_NAMES.clear(); // ponytail: wholesale clear; an LRU if servers ever cycle this many skins
            profileName = PROFILE_NAMES.computeIfAbsent(textures, t -> {
                try {
                    String json = new String(Base64.getDecoder().decode(t), StandardCharsets.UTF_8);
                    return JsonParser.parseString(json).getAsJsonObject().get("profileName").getAsString();
                } catch (Throwable e) {
                    return "";
                }
            });
            if (profileName.isEmpty()) profileName = null;
        } catch (Throwable t) {
            profileName = null;
        }
        if (profileName == null) return false;

        return profile.getId().version() == 4 || (profile.getId().version() == 2 && Objects.equals(profile.getName(), profileName));
    }
}
