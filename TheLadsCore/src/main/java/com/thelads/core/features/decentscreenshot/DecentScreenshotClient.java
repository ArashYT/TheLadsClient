/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.fabricmc.api.ClientModInitializer
 *  net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
 *  net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper
 *  net.minecraft.client.KeyMapping
 *  net.minecraft.client.KeyMapping$Category
 *  net.minecraft.client.gui.screens.Screen
 *  net.minecraft.resources.Identifier
 */
package com.thelads.core.features.decentscreenshot;

import com.thelads.core.features.decentscreenshot.DecentScreenshot;
import com.thelads.core.features.decentscreenshot.ScreenshotGalleryScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.Identifier;

public class DecentScreenshotClient
implements ClientModInitializer {
    public static KeyMapping openGalleryKey;

    public void onInitializeClient() {
        KeyMapping.Category category = KeyMapping.Category.register((Identifier)Identifier.fromNamespaceAndPath((String)"decentscreenshot", (String)"main"));
        openGalleryKey = KeyMappingHelper.registerKeyMapping((KeyMapping)new KeyMapping("key.decentscreenshot.open_gallery", 299, category));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openGalleryKey.consumeClick()) {
                if (client.gui.screen() != null) continue;
                client.setScreenAndShow((Screen)new ScreenshotGalleryScreen(null));
            }
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {});
        DecentScreenshot.LOGGER.info("[DecentScreenshot] Client initialized. Press F10 to open the screenshot gallery.");
    }
}

