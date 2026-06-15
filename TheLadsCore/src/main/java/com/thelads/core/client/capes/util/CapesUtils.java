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
        if (profile == null) {
            return false;
        }
        try {
            Collection<Property> texturesProperties = profile.properties().get("textures");
            if (texturesProperties == null || texturesProperties.isEmpty()) {
                return false;
            }
            Property texturesProperty = texturesProperties.iterator().next();
            String textures = texturesProperty.value();
            byte[] decoded = Base64.getDecoder().decode(textures);
            String json = new String(decoded, StandardCharsets.UTF_8);
            String profileName = JsonParser.parseString(json)
                                           .getAsJsonObject()
                                           .get("profileName")
                                           .getAsString();
            if (profileName == null) {
                return false;
            }
            int version = profile.id().version();
            return version == 4 || (version == 2 && profileName.equals(profile.name()));
        } catch (Exception e) {
            return false;
        }
    }
}
