// Ported from Capes 1.5.4+1.21 by Cael (LGPL-2.1-only) from Kotlin to Java; modified by The Lads: remapped to Mojang mappings and repackaged into Lads Core.
package com.thelads.core.v1_21_1.embedded.capes.render;

import com.mojang.authlib.GameProfile;
import com.thelads.core.v1_21_1.embedded.capes.Capes;
import com.thelads.core.v1_21_1.embedded.capes.handler.PlayerHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;

/** Kotlin object: state is static and set up on first use, like the object's initializer. */
public final class PlaceholderEntity {
    public static final GameProfile gameProfile = Minecraft.getInstance().getGameProfile();

    public static PlayerSkin skin = DefaultPlayerSkin.get(gameProfile);

    public static boolean slim = false;

    public static boolean showBody = true;
    public static boolean showElytra = false;
    public static boolean capeLoaded = false;
    public static float limbDistance = 0f;
    public static float lastLimbDistance = 0f;
    public static float limbAngle = 0f;
    public static float yaw = 0f;
    public static float prevYaw = 0f;
    public static double x = 0.0;
    public static double prevX = 0.0;

    static {
        Minecraft.getInstance().getSkinManager().getOrLoad(gameProfile).thenAccept(it -> {
            skin = it;
            slim = skin.model() == PlayerSkin.Model.SLIM;
        });
    }

    private PlaceholderEntity() {
    }

    public static void updateLimbs() {
        lastLimbDistance = limbDistance;
        double d = x - prevX;
        float g = (float) Math.sqrt(d * d) * 4.0f;
        if (g > 1.0f) {
            g = 1.0f;
        }
        limbDistance += (g - limbDistance) * 0.4f;
        limbAngle += limbDistance;
    }

    public static ResourceLocation getCapeTexture() {
        if (!capeLoaded) {
            capeLoaded = true;
            PlayerHandler.onLoadTexture(gameProfile);
        }
        PlayerHandler handler = PlayerHandler.fromProfile(gameProfile);
        return handler.getHasCape() ? handler.getCape() : skin.capeTexture();
    }

    public static ResourceLocation getElytraTexture() {
        PlayerHandler handler = PlayerHandler.fromProfile(gameProfile);
        ResourceLocation capeTexture = getCapeTexture();
        return handler.getHasElytraTexture() && Capes.getConfig().getEnableElytraTexture() && capeTexture != null ? capeTexture : ResourceLocation.parse("textures/entity/elytra.png");
    }

    public static ResourceLocation getSkinTexture() {
        return skin.texture();
    }
}
