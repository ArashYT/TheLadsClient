package com.thelads.smp.features.arise.nb6;

import net.minecraft.world.item.ItemStack;

import java.util.UUID;

/**
 * Server-side session tracking an active fishing minigame instance.
 */
public final class FishingMinigameSession {
    private final UUID playerId;
    private final int hookEntityId;
    private final ItemStack pendingLoot;
    private final ItemStack rodStack;
    private final float targetCenter;
    private final float targetWidth;
    private final float difficulty;
    private final int maxTicks;
    private final long startTimeMs;

    private int ticksElapsed = 0;
    private boolean active = true;

    public FishingMinigameSession(
            UUID playerId,
            int hookEntityId,
            ItemStack pendingLoot,
            ItemStack rodStack,
            float targetCenter,
            float targetWidth,
            float difficulty,
            int maxTicks
    ) {
        this.playerId = playerId;
        this.hookEntityId = hookEntityId;
        this.pendingLoot = pendingLoot;
        this.rodStack = rodStack;
        this.targetCenter = targetCenter;
        this.targetWidth = targetWidth;
        this.difficulty = difficulty;
        this.maxTicks = maxTicks;
        this.startTimeMs = System.currentTimeMillis();
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public int getHookEntityId() {
        return hookEntityId;
    }

    public ItemStack getPendingLoot() {
        return pendingLoot;
    }

    public ItemStack getRodStack() {
        return rodStack;
    }

    public float getTargetCenter() {
        return targetCenter;
    }

    public float getTargetWidth() {
        return targetWidth;
    }

    public float getDifficulty() {
        return difficulty;
    }

    public int getMaxTicks() {
        return maxTicks;
    }

    public long getStartTimeMs() {
        return startTimeMs;
    }

    public int getTicksElapsed() {
        return ticksElapsed;
    }

    public void incrementTicks() {
        this.ticksElapsed++;
    }

    public boolean isActive() {
        return active;
    }

    public void deactivate() {
        this.active = false;
    }
}
