package com.thelads.smp.features.arise.nb2;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * Client-side entrypoint for Native Batch NB2 keymappings and client commands.
 */
@Environment(EnvType.CLIENT)
public final class NB2Client implements ClientModInitializer {
    public static final KeyMapping.Category CATEGORY_LADS = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("ladssmp", "keys"));

    private static KeyMapping keyHideName;
    private static KeyMapping keyEmptyHand;
    private static KeyMapping keyPanoramica;

    @Override
    public void onInitializeClient() {
        // Register client keymappings
        keyHideName = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.ladssmp.hidename",
                InputConstants.KEY_H,
                CATEGORY_LADS
        ));

        keyEmptyHand = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.ladssmp.empty_hand",
                InputConstants.UNKNOWN.getValue(),
                CATEGORY_LADS
        ));

        keyPanoramica = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.ladssmp.panoramica",
                InputConstants.KEY_F4,
                CATEGORY_LADS
        ));

        // Client tick to poll keybinds
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) {
                return;
            }

            // HideName toggle
            while (keyHideName.consumeClick()) {
                boolean hidden = HideNameManager.toggle();
                client.player.sendOverlayMessage(
                        Component.literal(hidden ? "§e[HideName] Overhead nametags hidden." : "§a[HideName] Overhead nametags visible.")
                );
            }

            // Hold My Beer (Empty Hand)
            if (NB2Config.get().pressAndHold) {
                HoldMyBeerManager.setHeld(keyEmptyHand.isDown());
            } else {
                while (keyEmptyHand.consumeClick()) {
                    boolean active = HoldMyBeerManager.toggle();
                    client.player.sendOverlayMessage(
                            Component.literal(active ? "§e[Hold My Beer] Empty hand mode active." : "§7[Hold My Beer] Empty hand mode off.")
                    );
                }
            }

            // Panoramica capture
            while (keyPanoramica.consumeClick()) {
                PanoramicaManager.capturePanorama();
            }
        });

        // Register client commands: /ladssmp panorama
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommands.literal("ladssmp")
                    .then(ClientCommands.literal("panorama")
                            .executes(context -> {
                                PanoramicaManager.capturePanorama();
                                return 1;
                            })));
        });

        // Hitmarker attack callback (registered on client only)
        net.fabricmc.fabric.api.event.player.AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (world.isClientSide() && entity instanceof net.minecraft.world.entity.LivingEntity) {
                HitmarkerManager.onHit();
            }
            return net.minecraft.world.InteractionResult.PASS;
        });
    }
}
