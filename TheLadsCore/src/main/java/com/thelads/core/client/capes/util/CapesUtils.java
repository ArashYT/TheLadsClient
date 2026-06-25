package com.thelads.core.client.capes.util;

import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collection;

public final class CapesUtils {
    private CapesUtils() {
    }

    public static boolean isValidProfile(GameProfile profile) {
        return profile != null;
    }
}
