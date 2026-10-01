// Ported from Capes 1.5.10+1.21.11 by Cael (LGPL-2.1-only) from Kotlin to Java; modified by The Lads: repackaged into Lads Core.
package com.thelads.core.v1_21_11.embedded.capes.util;

import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;

public final class Utils {
    private Utils() {
    }

    /** Upstream extension {@code GameProfile.isValidProfile()}. */
    public static boolean isValidProfile(GameProfile profile) {
        String profileName;
        try { // runCatching { ... }.getOrNull()
            String textures = profile.properties().get("textures").stream().findFirst().get().value();
            String json = new String(Base64.getDecoder().decode(textures), StandardCharsets.UTF_8);
            profileName = JsonParser.parseString(json).getAsJsonObject().get("profileName").getAsString();
        } catch (Throwable t) {
            profileName = null;
        }
        if (profileName == null) return false;

        return profile.id().version() == 4 || (profile.id().version() == 2 && Objects.equals(profile.name(), profileName));
    }
}
