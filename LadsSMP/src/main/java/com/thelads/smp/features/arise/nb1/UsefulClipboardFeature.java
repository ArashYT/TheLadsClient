package com.thelads.smp.features.arise.nb1;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Consumer;

/**
 * Native clean-room implementation of Useful Clipboard.
 * Registers the craftable clipboard item and network synchronization handlers.
 */
public final class UsefulClipboardFeature {
    private static final Logger LOGGER = LoggerFactory.getLogger("LadsSMP-UsefulClipboard");

    public static Item CLIPBOARD;

    private static Item register(String name, Item item) {
        return Registry.register(BuiltInRegistries.ITEM, Identifier.fromNamespaceAndPath("ladssmp", name), item);
    }

    // Functional hook populated only on client side to avoid dedicated server classloader issues
    public static Consumer<InteractionHand> CLIENT_OPEN_SCREEN = null;

    public static void init() {
        if (CLIPBOARD == null) {
            CLIPBOARD = register("clipboard", new ClipboardItem(new Item.Properties().stacksTo(1)));
        }

        // Register networking payload and receiver
        PayloadTypeRegistry.serverboundPlay().register(SaveClipboardPayload.TYPE, SaveClipboardPayload.STREAM_CODEC);

        ServerPlayNetworking.registerGlobalReceiver(SaveClipboardPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            context.server().execute(() -> {
                savePlayerClipboardText(player, payload.text());
            });
        });

        LOGGER.info("[Lads SMP] Useful Clipboard initialized");
    }

    public static boolean isEnabled() {
        return AriseNB1Config.get().usefulClipboard.enabled;
    }

    public static void savePlayerClipboardText(ServerPlayer player, String text) {
        if (!isEnabled()) return;

        ItemStack stack = player.getItemInHand(InteractionHand.MAIN_HAND);
        if (!stack.is(CLIPBOARD)) {
            stack = player.getItemInHand(InteractionHand.OFF_HAND);
        }
        if (stack.is(CLIPBOARD)) {
            CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putString("ClipboardText", text));
        }
    }
}
