// Adapted from Raised 6.0.0 source by yurisuika, LGPL-3.0-or-later.
// Pinned source 4b6a3b8718316d681c3ae6832cdd19c3174a29a4; see META-INF/lads-sources/raised.
package com.thelads.core.v26_2.feature.raised;

import com.thelads.core.v26_2.feature.raised.client.RaisedOptions;
import com.thelads.core.v26_2.feature.raised.client.commands.RaisedCommand;
import com.thelads.core.v26_2.feature.raised.client.gui.layer.Layers;
import com.thelads.core.v26_2.feature.raised.client.gui.screens.SelectScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;

public class RaisedClient implements ClientModInitializer {

    public static void registerKeyMappings() {
        KeyMappingHelper.registerKeyMapping(RaisedOptions.OPTIONS);
    }

    public static void registerInputEvents() {
        ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
            while (RaisedOptions.OPTIONS.consumeClick()) {
                minecraft.gui.setScreen(new SelectScreen(null));
            }
        });
    }

    public static void registerCommands() {
        ClientCommandRegistrationCallback.EVENT.register(RaisedCommand::register);
    }

    public static void registerLayers() {
        Layers.boostrap();
    }

    @Override
    public void onInitializeClient() {
        registerKeyMappings();
        registerInputEvents();
        registerCommands();
        registerLayers();
    }

}