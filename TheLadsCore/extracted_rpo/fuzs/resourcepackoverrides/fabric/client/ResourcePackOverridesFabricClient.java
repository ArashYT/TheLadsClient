/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.fabricmc.api.ClientModInitializer
 *  net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
 *  net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
 *  net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents
 *  net.fabricmc.fabric.api.client.screen.v1.Screens
 *  net.minecraft.client.gui.screens.Screen
 *  net.minecraft.client.gui.screens.packs.PackSelectionScreen
 */
package fuzs.resourcepackoverrides.fabric.client;

import fuzs.resourcepackoverrides.common.client.handler.PackActionsHandler;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.packs.PackSelectionScreen;

public class ResourcePackOverridesFabricClient
implements ClientModInitializer {
    public void onInitializeClient() {
        ResourcePackOverridesFabricClient.registerEventHandlers();
    }

    private static void registerEventHandlers() {
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (screen instanceof PackSelectionScreen) {
                ScreenEvents.afterExtract((Screen)screen).register((packSelectionScreen, guiGraphics, mouseX, mouseY, partialTick) -> PackActionsHandler.onBeforeRenderScreen(Screens.getMinecraft((Screen)packSelectionScreen), (PackSelectionScreen)packSelectionScreen, guiGraphics, mouseX, mouseY, partialTick));
                ScreenKeyboardEvents.afterKeyPress((Screen)screen).register((packSelectionScreen, keyEvent) -> PackActionsHandler.onAfterKeyPressed(Screens.getMinecraft((Screen)packSelectionScreen), (PackSelectionScreen)packSelectionScreen, keyEvent));
            }
        });
        ClientTickEvents.END_CLIENT_TICK.register(PackActionsHandler::onEndClientTick);
    }
}

