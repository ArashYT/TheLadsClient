package com.thelads.core.features.alwayson.skinlayers;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.RemovalListener;
import com.google.common.cache.RemovalNotification;
import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.platform.NativeImage;
import com.thelads.core.features.alwayson.skinlayers.accessor.NativeImageAccessor;
import com.thelads.core.features.alwayson.skinlayers.accessor.PlayerSettings;
import com.thelads.core.features.alwayson.skinlayers.accessor.SkullSettings;
import com.thelads.core.features.alwayson.skinlayers.api.SkinLayersAPI;
import java.io.InputStream;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.entity.player.PlayerSkin;

public class SkinUtil {
    private static final Cache<AbstractTexture, NativeImage> cache = CacheBuilder.newBuilder()
            .expireAfterAccess(60L, TimeUnit.SECONDS)
            .removalListener(new RemovalListener<AbstractTexture, NativeImage>() {
                @Override
                public void onRemoval(RemovalNotification<AbstractTexture, NativeImage> notification) {
                    try {
                        if (notification.getValue() != null) {
                            notification.getValue().close();
                        }
                    } catch (Exception ex) {
                        SkinLayersModBase.LOGGER.error("Error while closing a texture.", ex);
                    }
                }
            }).build();

    public static class TextureResult implements AutoCloseable {
        public final NativeImage image;
        private final boolean shouldClose;

        public TextureResult(NativeImage image, boolean shouldClose) {
            this.image = image;
            this.shouldClose = shouldClose;
        }

        @Override
        public void close() {
            if (shouldClose && image != null) {
                image.close();
            }
        }
    }

    public static TextureResult getTexture(Identifier resourceLocation, SkullSettings settings) {
        if (resourceLocation == null) {
            return null;
        }
        try {
            Optional<Resource> optionalRes = Minecraft.getInstance().getResourceManager().getResource(resourceLocation);
            if (optionalRes.isPresent()) {
                Resource resource = optionalRes.get();
                try (InputStream inputStream = resource.open()) {
                    return new TextureResult(NativeImage.read(inputStream), true);
                }
            }
            AbstractTexture texture = Minecraft.getInstance().getTextureManager().getTexture(resourceLocation);
            if (texture == null) {
                return null;
            }
            NativeImage cachedImage = cache.getIfPresent(texture);
            if (cachedImage != null && ((Object) cachedImage) instanceof NativeImageAccessor && ((NativeImageAccessor) (Object) cachedImage).skinlayers$isAllocated()) {
                return new TextureResult(cachedImage, false);
            }
            cache.invalidate(texture);
            if (texture instanceof DynamicTexture) {
                try {
                    NativeImage img = ((DynamicTexture) texture).getPixels();
                    if (img != null && ((Object) img) instanceof NativeImageAccessor && ((NativeImageAccessor) (Object) img).skinlayers$isAllocated()) {
                        return new TextureResult(img, false);
                    }
                } catch (Exception exception) {
                    // empty catch block
                }
                return null;
            }
            if (settings != null) {
                settings.setInitialized(false);
            }
            SkinLayersModBase.LOGGER.warn("Unable to handle skin " + resourceLocation + ". Potentially a conflict with another mod. (" + texture.getClass().getName() + ")");
            return null;
        } catch (Exception ex) {
            SkinLayersModBase.LOGGER.error("Error while resolving a skin texture.", ex);
            return null;
        }
    }

    public static boolean setup3dLayers(AbstractClientPlayer abstractClientPlayerEntity, PlayerSettings settings, boolean thinArms) {
        PlayerSkin playerSkin = abstractClientPlayerEntity.getSkin();
        Identifier skinLocation = playerSkin != null && playerSkin.body() != null ? playerSkin.body().texturePath() : null;
        if (skinLocation == null) {
            return false;
        }
        if (skinLocation.equals(settings.getCurrentSkin()) && thinArms == settings.hasThinArms()) {
            return settings.getHeadMesh() != null;
        }
        try (TextureResult result = SkinUtil.getTexture(skinLocation, null)) {
            if (result == null || result.image == null) {
                settings.setCurrentSkin(skinLocation);
                settings.setThinArms(thinArms);
                settings.clearMeshes();
                return false;
            }
            NativeImage skin = result.image;
            if (skin.getWidth() != 64 || skin.getHeight() != 64) {
                settings.setCurrentSkin(skinLocation);
                settings.setThinArms(thinArms);
                settings.clearMeshes();
                return false;
            }
            settings.setLeftLegMesh(SkinLayersAPI.getMeshHelper().create3DMesh(skin, 4, 12, 4, 0, 48, true, 0.0f));
            settings.setRightLegMesh(SkinLayersAPI.getMeshHelper().create3DMesh(skin, 4, 12, 4, 0, 32, true, 0.0f));
            if (thinArms) {
                settings.setLeftArmMesh(SkinLayersAPI.getMeshHelper().create3DMesh(skin, 3, 12, 4, 48, 48, true, -2.0f));
                settings.setRightArmMesh(SkinLayersAPI.getMeshHelper().create3DMesh(skin, 3, 12, 4, 40, 32, true, -2.0f));
            } else {
                settings.setLeftArmMesh(SkinLayersAPI.getMeshHelper().create3DMesh(skin, 4, 12, 4, 48, 48, true, -2.0f));
                settings.setRightArmMesh(SkinLayersAPI.getMeshHelper().create3DMesh(skin, 4, 12, 4, 40, 32, true, -2.0f));
            }
            settings.setTorsoMesh(SkinLayersAPI.getMeshHelper().create3DMesh(skin, 8, 12, 4, 16, 32, true, 0.0f));
            settings.setHeadMesh(SkinLayersAPI.getMeshHelper().create3DMesh(skin, 8, 8, 8, 32, 0, false, 0.6f));
            settings.setCurrentSkin(skinLocation);
            settings.setThinArms(thinArms);
            return true;
        }
    }

    public static boolean setup3dLayers(GameProfile gameprofile, SkullSettings settings) {
        if (gameprofile == null) {
            return false;
        }
        PlayerSkin playerSkin = Minecraft.getInstance().getSkinManager().createLookup(gameprofile, false).get();
        Identifier playerSkinLocation = playerSkin != null && playerSkin.body() != null ? playerSkin.body().texturePath() : null;
        if (playerSkinLocation == null) {
            return false;
        }
        try (TextureResult result = SkinUtil.getTexture(playerSkinLocation, settings)) {
            if (result == null || result.image == null) {
                return false;
            }
            NativeImage skin = result.image;
            if (skin.getWidth() != 64 || skin.getHeight() != 64) {
                return false;
            }
            settings.setupHeadLayers(SkinLayersAPI.getMeshHelper().create3DMesh(skin, 8, 8, 8, 32, 0, false, 0.6f));
            settings.setInitialized(true);
            return true;
        }
    }

    public static boolean setup3dLayers(Identifier playerSkin, SkullSettings settings) {
        if (playerSkin == null) {
            return false;
        }
        try (TextureResult result = SkinUtil.getTexture(playerSkin, settings)) {
            if (result == null || result.image == null) {
                return false;
            }
            NativeImage skin = result.image;
            if (skin.getWidth() != 64 || skin.getHeight() != 64) {
                return false;
            }
            settings.setupHeadLayers(SkinLayersAPI.getMeshHelper().create3DMesh(skin, 8, 8, 8, 32, 0, false, 0.6f));
            settings.setInitialized(true);
            return true;
        }
    }
}
