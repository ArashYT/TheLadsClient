package com.thelads.smp.features.arise.nb1.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.thelads.smp.features.arise.nb1.CarryOnExtendFeature;
import com.thelads.smp.features.arise.nb1.ThrowCarriedPayload;
import com.thelads.smp.features.arise.nb1.UsefulClipboardFeature;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

/**
 * Client entrypoint for Arise NB1 features.
 * Wires the Useful Clipboard GUI screen into the common Clipboard item interaction
 * and registers keybindings for Carry On Extend throw.
 */
public final class AriseNB1ClientMod implements ClientModInitializer {
    public static KeyMapping THROW_CARRIED_KEY;

    @Override
    public void onInitializeClient() {
        UsefulClipboardFeature.CLIENT_OPEN_SCREEN = (hand) -> {
            Minecraft.getInstance().setScreenAndShow(new ClipboardScreen(hand));
        };

        // Keybinding for throwing carried objects (Carry On Extend)
        THROW_CARRIED_KEY = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.ladssmp.throw_carried",
                InputConstants.KEY_R,
                KeyMapping.Category.GAMEPLAY
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;
            while (THROW_CARRIED_KEY.consumeClick()) {
                if (CarryOnExtendFeature.isEnabled()) {
                    ClientPlayNetworking.send(ThrowCarriedPayload.INSTANCE);
                }
            }
        });
    }
}
