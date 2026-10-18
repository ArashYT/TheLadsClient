package com.thelads.smp.features.arise.nb2;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.network.chat.Component;

/**
 * Handles confirmation dialog prompt before disconnecting or quitting to title from the Pause menu (Quit).
 */
public final class QuitConfirmManager {
    private QuitConfirmManager() {}

    public static boolean isQuitConfirmEnabled() {
        return NB2Config.get().quitConfirm;
    }

    @Environment(EnvType.CLIENT)
    public static void promptDisconnectConfirmation(PauseScreen parent, Runnable onConfirm) {
        if (!isQuitConfirmEnabled()) {
            onConfirm.run();
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        boolean singleplayer = mc.hasSingleplayerServer();
        Component title = singleplayer ? Component.literal("Save and Quit to Title?") : Component.literal("Disconnect from Server?");
        Component message = singleplayer
                ? Component.literal("Your world progress will be saved.")
                : Component.literal("Are you sure you want to leave the server?");

        ConfirmScreen confirmScreen = new ConfirmScreen(
                (boolean confirmed) -> {
                    if (confirmed) {
                        onConfirm.run();
                    } else {
                        mc.setScreenAndShow(parent);
                    }
                },
                title,
                message,
                Component.literal("Yes"),
                Component.literal("Cancel")
        );

        mc.setScreenAndShow(confirmScreen);
    }
}
