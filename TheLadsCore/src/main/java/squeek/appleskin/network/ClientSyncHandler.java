/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.fabricmc.api.EnvType
 *  net.fabricmc.api.Environment
 *  net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
 *  net.minecraft.world.entity.player.Player
 */
package squeek.appleskin.network;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.world.entity.player.Player;
import squeek.appleskin.helpers.ExhaustionHelper;
import squeek.appleskin.network.ExhaustionSyncPayload;
import squeek.appleskin.network.NaturalRegenerationSyncPayload;
import squeek.appleskin.network.SaturationSyncPayload;

public class ClientSyncHandler {
    public static boolean naturalRegeneration = true;

    @Environment(value=EnvType.CLIENT)
    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(ExhaustionSyncPayload.ID, (payload, context) -> context.client().execute(() -> ExhaustionHelper.setExhaustion((Player)context.client().player, payload.getExhaustion())));
        ClientPlayNetworking.registerGlobalReceiver(SaturationSyncPayload.ID, (payload, context) -> context.client().execute(() -> context.client().player.getFoodData().setSaturation(payload.getSaturation())));
        ClientPlayNetworking.registerGlobalReceiver(NaturalRegenerationSyncPayload.ID, (payload, context) -> {
            naturalRegeneration = payload.naturalRegeneration();
        });
    }
}

