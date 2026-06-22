/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
 *  net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
 *  net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
 *  net.minecraft.network.protocol.common.custom.CustomPacketPayload
 *  net.minecraft.server.level.ServerLevel
 *  net.minecraft.server.level.ServerPlayer
 *  net.minecraft.world.entity.player.Player
 *  net.minecraft.world.level.gamerules.GameRules
 */
package squeek.appleskin.network;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.gamerules.GameRules;
import squeek.appleskin.helpers.ExhaustionHelper;
import squeek.appleskin.network.ExhaustionSyncPayload;
import squeek.appleskin.network.NaturalRegenerationSyncPayload;
import squeek.appleskin.network.SaturationSyncPayload;

public class SyncHandler {
    private static final Map<UUID, Float> lastSaturationLevels = new HashMap<UUID, Float>();
    private static final Map<UUID, Float> lastExhaustionLevels = new HashMap<UUID, Float>();
    private static boolean naturalRegeneration = true;

    public static void init() {
        PayloadTypeRegistry.clientboundPlay().register(ExhaustionSyncPayload.ID, ExhaustionSyncPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(SaturationSyncPayload.ID, SaturationSyncPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(NaturalRegenerationSyncPayload.ID, NaturalRegenerationSyncPayload.CODEC);
        ServerTickEvents.END_LEVEL_TICK.register(SyncHandler::onServerWorldTick);
    }

    public static void onPlayerUpdate(ServerPlayer player) {
        Float lastSaturationLevel = lastSaturationLevels.get(player.getUUID());
        Float lastExhaustionLevel = lastExhaustionLevels.get(player.getUUID());
        float saturation = player.getFoodData().getSaturationLevel();
        if (lastSaturationLevel == null || lastSaturationLevel.floatValue() != saturation) {
            ServerPlayNetworking.send((ServerPlayer)player, (CustomPacketPayload)new SaturationSyncPayload(saturation));
            lastSaturationLevels.put(player.getUUID(), Float.valueOf(saturation));
        }
        float exhaustionLevel = ExhaustionHelper.getExhaustion((Player)player);
        if (lastExhaustionLevel == null || Math.abs(lastExhaustionLevel.floatValue() - exhaustionLevel) >= 0.01f) {
            ServerPlayNetworking.send((ServerPlayer)player, (CustomPacketPayload)new ExhaustionSyncPayload(exhaustionLevel));
            lastExhaustionLevels.put(player.getUUID(), Float.valueOf(exhaustionLevel));
        }
    }

    public static void onPlayerLoggedIn(ServerPlayer player) {
        lastSaturationLevels.remove(player.getUUID());
        lastExhaustionLevels.remove(player.getUUID());
        if (!naturalRegeneration) {
            ServerPlayNetworking.send((ServerPlayer)player, (CustomPacketPayload)new NaturalRegenerationSyncPayload(false));
        }
    }

    public static void onServerWorldTick(ServerLevel world) {
        Boolean cur = (Boolean)world.getGameRules().get(GameRules.NATURAL_HEALTH_REGENERATION);
        if (naturalRegeneration != cur) {
            for (ServerPlayer player : world.players()) {
                ServerPlayNetworking.send((ServerPlayer)player, (CustomPacketPayload)new NaturalRegenerationSyncPayload(cur));
            }
            naturalRegeneration = cur;
        }
    }
}

