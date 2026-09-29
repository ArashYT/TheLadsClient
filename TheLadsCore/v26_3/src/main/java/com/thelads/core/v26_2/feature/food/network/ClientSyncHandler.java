package com.thelads.core.v26_2.feature.food.network;

import com.thelads.core.v26_2.feature.food.client.HUDOverlayHandler;
import com.thelads.core.v26_2.feature.food.helpers.ExhaustionHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public final class ClientSyncHandler {
    public static boolean naturalRegeneration = true;
    public static boolean saturationSynced, exhaustionSynced, regenerationSynced;
    private static net.minecraft.client.player.LocalPlayer currentPlayer;
    public static void reset() {
        naturalRegeneration = true;
        saturationSynced = exhaustionSynced = regenerationSynced = false;
        currentPlayer = null;
        if (HUDOverlayHandler.INSTANCE != null) HUDOverlayHandler.INSTANCE.resetPlayer();
    }
    public static void ensurePlayer(net.minecraft.client.player.LocalPlayer player) {
        if (currentPlayer == player) return;
        // Upstream AppleSkin sends the game rule on login/change, not on every respawn
        // or portal transition. It belongs to the connection, unlike the player's food data.
        saturationSynced = exhaustionSynced = false;
        if (HUDOverlayHandler.INSTANCE != null) HUDOverlayHandler.INSTANCE.resetPlayer();
        currentPlayer = player;
    }
    public static void init() {
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> reset());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset());
        ClientPlayNetworking.registerGlobalReceiver(ExhaustionSyncPayload.ID, (payload, context) -> {
            ensurePlayer(context.player());
            if (context.player() != null && valid(payload.exhaustion(), 40)) {
                ExhaustionHelper.setExhaustion(context.player(), payload.exhaustion());
                exhaustionSynced = true;
            }
        });
        ClientPlayNetworking.registerGlobalReceiver(SaturationSyncPayload.ID, (payload, context) -> {
            ensurePlayer(context.player());
            if (context.player() != null && valid(payload.saturation(), 20)) {
                context.player().getFoodData().setSaturation(payload.saturation());
                saturationSynced = true;
            }
        });
        ClientPlayNetworking.registerGlobalReceiver(NaturalRegenerationSyncPayload.ID, (payload, context) -> {
            ensurePlayer(context.player());
            naturalRegeneration = payload.naturalRegeneration();
            regenerationSynced = true;
        });
    }
    public static boolean valid(float value, float maximum) { return Float.isFinite(value) && value >= 0 && value <= maximum; }
}
