package com.thelads.smp.features.arise.nb6.client;

import com.thelads.smp.features.arise.nb6.network.FinishFishingMinigamePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client-side physics simulation and input handler for the fishing minigame.
 * Player holds Use (Right-Click) or Jump (Space) to raise the catch bar against gravity,
 * keeping the bar centered on the swimming fish.
 */
public final class FishingMinigameClient {
    private static final Logger LOGGER = LoggerFactory.getLogger("LadsSMP/FishingMinigameClient");

    private static boolean active = false;
    private static int hookId = -1;
    private static String fishId = "";
    private static float difficulty = 1.0f;
    private static int maxTicks = 100;
    private static int ticksElapsed = 0;

    // Bar physics: 0 = bottom, 100 = top
    private static float barPos = 30.0f;
    private static float barVelocity = 0.0f;
    private static float barHeight = 26.0f;

    // Fish movement: 0 = bottom, 100 = top
    private static float fishPos = 50.0f;
    private static float fishTarget = 50.0f;
    private static float fishSpeed = 1.5f;

    // Progress: 0 to 100 (100 = catch, 0 = escape)
    private static float progress = 45.0f;

    private FishingMinigameClient() {}

    public static void start(int id, String fish, float targetCenter, float targetWidth, float diff, int ticks) {
        hookId = id;
        fishId = fish;
        difficulty = Math.max(0.5f, diff);
        maxTicks = Math.max(40, ticks);
        ticksElapsed = 0;

        barPos = 30.0f;
        barVelocity = 0.0f;
        barHeight = Math.max(16.0f, targetWidth);

        fishPos = Mth.clamp(targetCenter, 20.0f, 80.0f);
        fishTarget = fishPos;
        fishSpeed = 1.2f * difficulty;

        progress = 40.0f;
        active = true;

        LOGGER.debug("[Fishing Minigame Client] Started minigame for hook {} (fish={}, diff={})", id, fish, diff);
    }

    public static boolean isActive() {
        return active;
    }

    public static float getBarPos() {
        return barPos;
    }

    public static float getBarHeight() {
        return barHeight;
    }

    public static float getFishPos() {
        return fishPos;
    }

    public static float getProgress() {
        return progress;
    }

    public static String getFishId() {
        return fishId;
    }

    public static void tick(Minecraft client) {
        if (!active) {
            return;
        }

        if (client.player == null || !client.player.isAlive()) {
            cancel();
            return;
        }

        ticksElapsed++;

        // 1. Player Input & Gravity Physics
        boolean pulling = client.options.keyUse.isDown() || client.options.keyJump.isDown();
        if (pulling) {
            barVelocity += 0.85f; // Upward acceleration
        } else {
            barVelocity -= 0.65f; // Gravity pulling downward
        }

        // Air damping
        barVelocity *= 0.88f;
        barPos += barVelocity;

        // Bounce off bounds [0, 100 - barHeight]
        float maxBar = 100.0f - barHeight;
        if (barPos < 0.0f) {
            barPos = 0.0f;
            barVelocity = -barVelocity * 0.3f; // Slight floor bounce
        } else if (barPos > maxBar) {
            barPos = maxBar;
            barVelocity = -barVelocity * 0.3f; // Slight ceiling bounce
        }

        // 2. Fish AI Movement
        if (Math.abs(fishPos - fishTarget) < 2.0f || client.player.level().getRandom().nextFloat() < 0.08f) {
            // Pick new target position
            fishTarget = 10.0f + client.player.level().getRandom().nextFloat() * 80.0f;
        }

        float fishDelta = (fishTarget - fishPos);
        float step = Math.signum(fishDelta) * Math.min(Math.abs(fishDelta) * 0.1f, fishSpeed);
        fishPos = Mth.clamp(fishPos + step, 5.0f, 95.0f);

        // 3. Check if fish is inside bar zone
        boolean inside = (fishPos >= barPos && fishPos <= (barPos + barHeight));

        if (inside) {
            progress += 1.3f;
        } else {
            progress -= 0.85f;
        }

        progress = Mth.clamp(progress, 0.0f, 100.0f);

        // 4. Win / Loss resolution
        if (progress >= 100.0f) {
            finish(true);
        } else if (progress <= 0.0f || ticksElapsed >= maxTicks) {
            finish(false);
        }
    }

    public static void finish(boolean won) {
        if (!active) {
            return;
        }
        active = false;

        if (ClientPlayNetworking.canSend(FinishFishingMinigamePayload.TYPE)) {
            ClientPlayNetworking.send(new FinishFishingMinigamePayload(hookId, won, ticksElapsed));
        }

        Minecraft client = Minecraft.getInstance();
        if (client.player != null) {
            if (won) {
                client.player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.6f, 1.3f);
            } else {
                client.player.playSound(SoundEvents.FISH_SWIM, 0.5f, 0.8f);
            }
        }

        LOGGER.debug("[Fishing Minigame Client] Concluded minigame: won={}, ticks={}", won, ticksElapsed);
    }

    public static void cancel() {
        if (active) {
            active = false;
            hookId = -1;
        }
    }
}
