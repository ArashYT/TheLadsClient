/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.mojang.blaze3d.platform.NativeImage
 *  net.minecraft.client.Minecraft
 *  net.minecraft.client.renderer.texture.AbstractTexture
 *  net.minecraft.client.renderer.texture.DynamicTexture
 *  net.minecraft.resources.Identifier
 */
package com.thelads.core.features.decentscreenshot;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import com.thelads.core.features.decentscreenshot.DecentScreenshot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

public class ScreenshotTextureCache {
    private static final Map<File, ScreenshotTexture> CACHE = new HashMap<File, ScreenshotTexture>();

    public static ScreenshotTexture getOrLoad(File screenshotFile) {
        ScreenshotTexture screenshotTexture = null;
        if (CACHE.containsKey(screenshotFile)) {
            return CACHE.get(screenshotFile);
        }
        try (FileInputStream fis = new FileInputStream(screenshotFile)) {
            NativeImage nativeImage = NativeImage.read((InputStream)fis);
            int width = nativeImage.getWidth();
            int height = nativeImage.getHeight();
            DynamicTexture texture = new DynamicTexture(screenshotFile::getName, nativeImage);
            String sanitized = screenshotFile.getName().toLowerCase().replaceAll("[^a-z0-9_/.-]", "_");
            Identifier location = Identifier.fromNamespaceAndPath((String)"decentscreenshot", (String)("screenshots/" + sanitized));
            Minecraft.getInstance().getTextureManager().register(location, (AbstractTexture)texture);
            ScreenshotTexture screenshotTexture2 = new ScreenshotTexture(location, width, height);
            CACHE.put(screenshotFile, screenshotTexture2);
            screenshotTexture = screenshotTexture2;
        } catch (Exception e) {
            DecentScreenshot.LOGGER.warn("[DecentScreenshot] Failed to load screenshot texture: {}", (Object)screenshotFile.getName(), (Object)e);
            return null;
        }
        return screenshotTexture;
    }

    public static void releaseAll() {
        for (ScreenshotTexture texture : CACHE.values()) {
            Minecraft.getInstance().getTextureManager().release(texture.id());
        }
        CACHE.clear();
    }

    public static void release(File file) {
        ScreenshotTexture texture = CACHE.remove(file);
        if (texture != null) {
            Minecraft.getInstance().getTextureManager().release(texture.id());
        }
    }

    public record ScreenshotTexture(Identifier id, int width, int height) {
    }
}

