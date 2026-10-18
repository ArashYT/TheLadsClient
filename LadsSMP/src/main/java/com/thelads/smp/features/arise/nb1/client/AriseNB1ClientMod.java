package com.thelads.smp.features.arise.nb1.client;

import com.thelads.smp.features.arise.nb1.UsefulClipboardFeature;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.Minecraft;

/**
 * Client entrypoint for Arise NB1 features.
 * Wires the Useful Clipboard GUI screen into the common Clipboard item interaction.
 */
public final class AriseNB1ClientMod implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        UsefulClipboardFeature.CLIENT_OPEN_SCREEN = (hand) -> {
            Minecraft.getInstance().setScreenAndShow(new ClipboardScreen(hand));
        };
    }
}
