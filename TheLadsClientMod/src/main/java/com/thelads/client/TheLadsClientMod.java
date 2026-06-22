package com.thelads.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.thelads.client.config.ConfigManager;
import com.thelads.client.gui.SettingsScreen;
import net.minecraft.client.gui.screens.Screen;

public class TheLadsClientMod implements ClientModInitializer {
    public static final String MOD_ID = "theladsclientmod";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static KeyMapping rightShiftKeyBinding;



    @Override
    public void onInitializeClient() {
        LOGGER.info("The Lads Client Mod initializing!");

        ConfigManager.loadConfig();

        KeyMapping temp = new KeyMapping(
                "key.theladsclient.settings",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_RIGHT_SHIFT,
                KeyMapping.Category.MISC
        );
        rightShiftKeyBinding = KeyMappingHelper.registerKeyMapping(temp);
        if (rightShiftKeyBinding == null) {
            rightShiftKeyBinding = temp;
        }

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (rightShiftKeyBinding.consumeClick()) {
                if (client != null && !(client.screen instanceof SettingsScreen)) {
                    client.setScreen(new SettingsScreen(client.screen));
                }
            }
        });
    }
}
