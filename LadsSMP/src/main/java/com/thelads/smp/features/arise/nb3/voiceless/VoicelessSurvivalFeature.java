package com.thelads.smp.features.arise.nb3.voiceless;

import com.thelads.smp.features.arise.nb3.AriseNb3Config;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * Voiceless Survival Feature:
 * When players speak above a microphone volume threshold in voice chat,
 * nearby hostile monsters hear the sound and pathfind to investigate the noise source.
 */
public final class VoicelessSurvivalFeature {
    private VoicelessSurvivalFeature() {}

    /**
     * Calculates the effective detection range considering whisper and sneaking multipliers.
     */
    public static double computeEffectiveRange(double baseRange, boolean isWhispering, boolean isSneaking) {
        double r = baseRange;
        if (isWhispering) {
            r *= AriseNb3Config.get().voicelessSurvival.whisperRangeMultiplier;
        }
        if (isSneaking) {
            r *= AriseNb3Config.get().voicelessSurvival.sneakingRangeMultiplier;
        }
        return r;
    }

    /**
     * Handles voice input from a player. If sound volume exceeds threshold, alerts nearby monsters.
     * @return count of alerted monsters
     */
    public static int onPlayerVoice(ServerPlayer player, double decibels, boolean isWhispering) {
        if (!AriseNb3Config.get().voicelessSurvival.enabled) return 0;
        if (player == null || player.isSpectator()) return 0;

        double threshold = AriseNb3Config.get().voicelessSurvival.voiceThreshold;
        if (decibels < threshold) return 0;

        double baseRange = AriseNb3Config.get().voicelessSurvival.detectionRange;
        double range = computeEffectiveRange(baseRange, isWhispering, player.isShiftKeyDown());
        double speed = AriseNb3Config.get().voicelessSurvival.movementSpeedMultiplier;

        AABB box = player.getBoundingBox().inflate(range);
        List<Monster> monsters = player.level().getEntitiesOfClass(Monster.class, box);
        int alerted = 0;

        for (Monster monster : monsters) {
            if (monster.isAlive() && !monster.isNoAi()) {
                monster.getNavigation().moveTo(player.getX(), player.getY(), player.getZ(), speed);
                alerted++;
            }
        }
        return alerted;
    }
}
