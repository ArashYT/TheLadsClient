package com.thelads.smp.features.arise.nb6;

import com.thelads.smp.features.arise.nb6.network.FinishFishingMinigamePayload;
import com.thelads.smp.features.arise.nb6.network.StartFishingMinigamePayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.advancements.triggers.CriteriaTriggers;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side controller for active fishing minigames.
 * Strictly validates all client inputs, prevents item duplication or speed hacks,
 * and handles persistence and rewards.
 */
public final class FishingMinigameServer {
    private static final Logger LOGGER = LoggerFactory.getLogger("LadsSMP/FishingMinigameServer");
    private static final Map<UUID, FishingMinigameSession> SESSIONS = new ConcurrentHashMap<>();

    private FishingMinigameServer() {}

    public static void init() {
        ServerPlayNetworking.registerGlobalReceiver(
                FinishFishingMinigamePayload.TYPE,
                (payload, context) -> {
                    ServerPlayer player = context.player();
                    context.server().execute(() -> completeMinigame(
                            player,
                            payload.hookId(),
                            payload.won(),
                            payload.ticksElapsed()
                    ));
                }
        );
    }

    public static boolean isMinigameActive(UUID playerId) {
        if (playerId == null) return false;
        FishingMinigameSession s = SESSIONS.get(playerId);
        return s != null && s.isActive();
    }

    public static synchronized boolean startMinigame(ServerPlayer player, FishingHook hook, ItemStack rodStack) {
        if (player == null || hook == null || !hook.isAlive()) {
            return false;
        }

        UUID uuid = player.getUUID();
        if (isMinigameActive(uuid)) {
            return false;
        }

        ItemStack loot = FishingLootManager.rollLoot(hook, player, rodStack);
        if (loot.isEmpty()) {
            return false;
        }

        float diff = AriseNB6Config.get().minigameDifficulty;
        int maxTicks = AriseNB6Config.get().minigameDurationTicks;
        float targetCenter = 40.0f + hook.level().getRandom().nextFloat() * 20.0f;
        float targetWidth = Math.max(16.0f, 32.0f / Math.max(0.5f, diff));

        FishingMinigameSession session = new FishingMinigameSession(
                uuid,
                hook.getId(),
                loot,
                rodStack,
                targetCenter,
                targetWidth,
                diff,
                maxTicks
        );
        SESSIONS.put(uuid, session);

        String fishId = BuiltInRegistries.ITEM.getKey(loot.getItem()).getPath();
        StartFishingMinigamePayload payload = new StartFishingMinigamePayload(
                hook.getId(),
                fishId,
                targetCenter,
                targetWidth,
                diff,
                maxTicks
        );

        try {
            ServerPlayNetworking.send(player, payload);
            LOGGER.debug("[Fishing Minigame] Started minigame for {} targeting {}", player.getScoreboardName(), fishId);
            return true;
        } catch (Exception e) {
            LOGGER.error("[Fishing Minigame] Failed to send start packet to {}. Reverting to instant catch.", player.getScoreboardName(), e);
            SESSIONS.remove(uuid);
            awardLootDirect(player, hook, loot, rodStack);
            return false;
        }
    }

    public static synchronized void completeMinigame(ServerPlayer player, int hookId, boolean won, int ticksElapsed) {
        if (player == null) {
            return;
        }

        UUID uuid = player.getUUID();
        FishingMinigameSession session = SESSIONS.remove(uuid);
        if (session == null || !session.isActive()) {
            LOGGER.warn("[Fishing Minigame] Received minigame completion from {} without active session.", player.getScoreboardName());
            return;
        }
        session.deactivate();

        if (session.getHookEntityId() != hookId) {
            LOGGER.warn("[Fishing Minigame] Mismatched hookId for {}: expected {}, received {}.",
                    player.getScoreboardName(), session.getHookEntityId(), hookId);
            return;
        }

        Entity hookEntity = player.level().getEntity(hookId);
        FishingHook hook = (hookEntity instanceof FishingHook fh) ? fh : null;

        // Anti-exploit check: impossible speed completion
        if (won && ticksElapsed < 25) {
            LOGGER.warn("[Fishing Minigame] Player {} claimed impossible minigame completion in {} ticks. Marking as failed.",
                    player.getScoreboardName(), ticksElapsed);
            won = false;
        }

        // Distance check
        if (hook != null && player.distanceToSqr(hook) > 1024.0) {
            LOGGER.warn("[Fishing Minigame] Player {} is too far from hook ({} m^2). Session failed.",
                    player.getScoreboardName(), player.distanceToSqr(hook));
            won = false;
        }

        ItemStack rod = session.getRodStack();

        if (won) {
            ItemStack loot = session.getPendingLoot();
            awardLootDirect(player, hook, loot, rod);
        } else {
            // Failure: fish escaped, rod is never voided or destroyed, small line strain damage
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.FISH_SWIM, SoundSource.PLAYERS, 0.7f, 0.9f);
            player.sendSystemMessage(Component.literal("§cThe fish slipped off the hook!"), true);

            if (rod != null && !rod.isEmpty() && rod.isDamageableItem()) {
                rod.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
            }
        }

        if (hook != null && hook.isAlive()) {
            hook.discard();
        }
    }

    public static void awardLootDirect(ServerPlayer player, FishingHook hook, ItemStack loot, ItemStack rod) {
        if (player == null || loot == null || loot.isEmpty()) {
            return;
        }

        // Add to inventory or drop safely
        if (!player.getInventory().add(loot.copy())) {
            Containers.dropItemStack(player.level(), player.getX(), player.getY(), player.getZ(), loot.copy());
        }

        // Award experience (1 to 6)
        int xp = 1 + player.level().getRandom().nextInt(6);
        player.giveExperiencePoints(xp);

        // Record in Fishing Journal
        String fishId = BuiltInRegistries.ITEM.getKey(loot.getItem()).getPath();
        FishingJournalManager.recordCatch(player, fishId);

        // Stats & Advancement triggers
        player.awardStat(Stats.FISH_CAUGHT);
        if (rod != null && !rod.isEmpty()) {
            player.awardStat(Stats.ITEM_USED.get(rod.getItem()));
            if (rod.isDamageableItem()) {
                rod.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
            }
            if (hook != null) {
                CriteriaTriggers.FISHING_ROD_HOOKED.trigger(player, rod, hook, Collections.singletonList(loot));
            }
        }

        // Feedback sound & message
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.5f, 1.4f);
        player.sendSystemMessage(
                Component.literal("§aCaught §e" + loot.getHoverName().getString() + "§a!"),
                true
        );
    }

    public static synchronized void tickSessions(MinecraftServer server) {
        if (SESSIONS.isEmpty()) {
            return;
        }

        List<UUID> toRemove = new ArrayList<>();

        for (Map.Entry<UUID, FishingMinigameSession> entry : SESSIONS.entrySet()) {
            UUID uuid = entry.getKey();
            FishingMinigameSession session = entry.getValue();

            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player == null || !player.isAlive()) {
                toRemove.add(uuid);
                continue;
            }

            session.incrementTicks();

            // Timeout check (maxTicks + 30 grace ticks)
            if (session.getTicksElapsed() > session.getMaxTicks() + 30) {
                LOGGER.debug("[Fishing Minigame] Session timed out for {}", player.getScoreboardName());
                completeMinigame(player, session.getHookEntityId(), false, session.getTicksElapsed());
                toRemove.add(uuid);
            }
        }

        for (UUID uuid : toRemove) {
            SESSIONS.remove(uuid);
        }
    }
}
