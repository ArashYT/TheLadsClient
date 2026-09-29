package com.thelads.core.v26_2.feature.food.network;

import com.thelads.core.v26_2.feature.food.NativeFoodOverlay;
import com.thelads.core.v26_2.feature.food.helpers.ExhaustionHelper;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.gamerules.GameRules;
import java.util.Map;
import java.util.WeakHashMap;

/** Compatible AppleSkin wire format, with per-player lifecycle and channel negotiation. */
public final class SyncHandler {
    private record State(float saturation, float exhaustion, boolean regeneration,
                         net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension) {}
    private static final Map<ServerPlayer, State> last = new WeakHashMap<>();
    public static void init() {
        PayloadTypeRegistry.clientboundPlay().register(ExhaustionSyncPayload.ID, ExhaustionSyncPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(SaturationSyncPayload.ID, SaturationSyncPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(NaturalRegenerationSyncPayload.ID, NaturalRegenerationSyncPayload.CODEC);
        ServerTickEvents.END_LEVEL_TICK.register(world -> world.players().forEach(SyncHandler::onPlayerUpdate));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> last.clear());
    }
    public static void onPlayerLoggedIn(ServerPlayer player) { last.remove(player); }
    public static void onPlayerUpdate(ServerPlayer player) {
        if (!NativeFoodOverlay.active()) return;
        // Vanilla LAN clients must never receive unregistered custom payloads.
        if (!ServerPlayNetworking.canSend(player, SaturationSyncPayload.ID)
                || !ServerPlayNetworking.canSend(player, ExhaustionSyncPayload.ID)
                || !ServerPlayNetworking.canSend(player, NaturalRegenerationSyncPayload.ID)) return;
        var previous = last.get(player);
        // Dimension changes replace the client player/food data while preserving ServerPlayer.
        if (previous != null && !previous.dimension.equals(player.level().dimension())) previous = null;
        float saturation = player.getFoodData().getSaturationLevel();
        float exhaustion = ExhaustionHelper.getExhaustion(player);
        boolean regeneration = player.level().getGameRules().get(GameRules.NATURAL_HEALTH_REGENERATION);
        if (previous == null || previous.saturation != saturation)
            ServerPlayNetworking.send(player, new SaturationSyncPayload(saturation));
        boolean exhaustionChanged = previous == null || Math.abs(previous.exhaustion - exhaustion) >= .01f;
        if (exhaustionChanged) ServerPlayNetworking.send(player, new ExhaustionSyncPayload(exhaustion));
        if (previous == null || previous.regeneration != regeneration)
            ServerPlayNetworking.send(player, new NaturalRegenerationSyncPayload(regeneration));
        last.put(player, new State(saturation, exhaustionChanged ? exhaustion : previous.exhaustion, regeneration, player.level().dimension()));
    }
}
