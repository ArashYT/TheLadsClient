// Ported from Capes 1.5.10+26.3 by Cael (LGPL-2.1-only) from Kotlin to Java; modified by The Lads: repackaged into Lads Core.
package com.thelads.core.v26_2.embedded.capes.util;

import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;

public final class Utils {
    private Utils() {
    }

    /** Upstream extension {@code GameProfile.isValidProfile()}. */
    /** 1.9.12 perf: textures property value -> its profileName ("" = none). PlayerInfo.getSkin calls this every frame for every
     *  drawn player; decoding Base64 and parsing the JSON each time was 6.7 % of all allocations in the Legion bench. */
    private static final java.util.Map<String, String> PROFILE_NAMES = new java.util.concurrent.ConcurrentHashMap<>();

    public static boolean isValidProfile(GameProfile profile) {
        String profileName;
        try { // runCatching { ... }.getOrNull()
            var all = profile.properties().get("textures");
            if (all.isEmpty()) return false; // 1.9.12 perf: offline profiles threw (and filled in a stack trace) on every call
            String textures = all.iterator().next().value();
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

        return profile.id().version() == 4 || (profile.id().version() == 2 && Objects.equals(profile.name(), profileName));
    }
}
