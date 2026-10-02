// Ported from Capes 1.5.11+26.2 by Cael (LGPL-2.1-only) from Kotlin to Java; modified by The Lads: repackaged into Lads Core.
package com.thelads.core.v26_2.embedded.capes.render;

import com.mojang.authlib.GameProfile;
import com.thelads.core.v26_2.embedded.capes.Capes;
import com.thelads.core.v26_2.embedded.capes.handler.PlayerHandler;
import com.thelads.core.v26_2.embedded.capes.mixin.AccessorEntityRenderDispatcher;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;

/** Kotlin object: state is static and set up on first use, like the object's initializer. */
public final class PlaceholderEntity {
    public static final GameProfile gameProfile = Minecraft.getInstance().getGameProfile();

    private static PlayerSkin skin = DefaultPlayerSkin.get(gameProfile);

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
    private static PlaceholderEntityRenderer renderer;

    static {
        EntityRendererProvider.Context ctx = new EntityRendererProvider.Context(
                Minecraft.getInstance().getEntityRenderDispatcher(),
                ((AccessorEntityRenderDispatcher) Minecraft.getInstance().getEntityRenderDispatcher()).getBlockModelResolver(),
                Minecraft.getInstance().getItemModelResolver(),
                Minecraft.getInstance().getMapRenderer(),
                Minecraft.getInstance().getResourceManager(),
                Minecraft.getInstance().getEntityModels(),
                ((AccessorEntityRenderDispatcher) Minecraft.getInstance().getEntityRenderDispatcher()).getEquipmentAssets(),
                Minecraft.getInstance().getAtlasManager(),
                Minecraft.getInstance().font,
                Minecraft.getInstance().playerSkinRenderCache()
        );
        renderer = new PlaceholderEntityRenderer(ctx, slim);
        Minecraft.getInstance().getSkinManager().get(gameProfile).thenAccept(it -> {
            skin = it.get();
            slim = skin.model() == PlayerModelType.SLIM;
            renderer = new PlaceholderEntityRenderer(ctx, slim);
        });
    }

    private PlaceholderEntity() {
    }

    public static PlaceholderEntityRenderer getRenderer() {
        return renderer;
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

    public static ClientAsset.Texture getCapeTexture() {
        if (!capeLoaded) {
            capeLoaded = true;
            PlayerHandler.onLoadTexture(gameProfile);
        }
        PlayerHandler handler = PlayerHandler.fromProfile(gameProfile);
        return handler.getHasCape() ? handler.getCape() : skin.cape();
    }

    public static ClientAsset.Texture getElytraTexture() {
        PlayerHandler handler = PlayerHandler.fromProfile(gameProfile);
        ClientAsset.Texture capeTexture = getCapeTexture();
        return handler.getHasElytraTexture() && Capes.getConfig().getEnableElytraTexture() && capeTexture != null ? capeTexture
                : new ClientAsset.ResourceTexture(Identifier.withDefaultNamespace("entity/equipment/wings/elytra"));
    }

    public static PlayerSkin getSkinTextures() {
        return new PlayerSkin(skin.body(), getCapeTexture(), getElytraTexture(), skin.model(), skin.secure());
    }
}
