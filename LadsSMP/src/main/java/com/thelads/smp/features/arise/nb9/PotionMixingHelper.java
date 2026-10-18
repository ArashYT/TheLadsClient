package com.thelads.smp.features.arise.nb9;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Clean-room helper for combining potion effects, inverse potion neutralization, and color blending.
 * Designed to be pure logic, thread-safe, and unit testable without Minecraft level state.
 */
public final class PotionMixingHelper {

    public record PotionEffectEntry(String effectId, int duration, int amplifier) {}

    public record MixedPotionResult(List<PotionEffectEntry> effects, int blendedColor) {}

    /**
     * Blends two RGB colors together evenly.
     */
    public static int blendColors(int colorA, int colorB) {
        int rA = (colorA >> 16) & 0xFF;
        int gA = (colorA >> 8) & 0xFF;
        int bA = colorA & 0xFF;

        int rB = (colorB >> 16) & 0xFF;
        int gB = (colorB >> 8) & 0xFF;
        int bB = colorB & 0xFF;

        int r = (rA + rB) / 2;
        int g = (gA + gB) / 2;
        int b = (bA + bB) / 2;

        return (r << 16) | (g << 8) | b;
    }

    /**
     * Mixes incoming effects into existing effects, applying inverse neutralization and mixing limits.
     *
     * @param existingEffects Existing list of effects in cauldron.
     * @param incomingEffects Incoming list of effects added.
     * @param inverseMap      Map of inverse effect IDs (e.g. "minecraft:slowness" -> "minecraft:speed").
     * @param limit           Maximum allowed combined effects (potion_mixing_limit).
     * @param existingColor   Current liquid color.
     * @param incomingColor   Incoming liquid color.
     * @return MixedPotionResult containing new effects and blended color.
     */
    public static MixedPotionResult mix(
            List<PotionEffectEntry> existingEffects,
            List<PotionEffectEntry> incomingEffects,
            Map<String, String> inverseMap,
            int limit,
            int existingColor,
            int incomingColor
    ) {
        List<PotionEffectEntry> result = new ArrayList<>(existingEffects);

        for (PotionEffectEntry incoming : incomingEffects) {
            String inverseTarget = inverseMap != null ? inverseMap.get(incoming.effectId()) : null;
            boolean neutralized = false;

            if (inverseTarget != null) {
                for (int i = 0; i < result.size(); i++) {
                    PotionEffectEntry current = result.get(i);
                    if (current.effectId().equals(inverseTarget)) {
                        // Neutralize: if amplifier is equal or lower, remove; if higher, reduce amplifier
                        if (current.amplifier() <= incoming.amplifier()) {
                            result.remove(i);
                        } else {
                            result.set(i, new PotionEffectEntry(current.effectId(), current.duration(), current.amplifier() - 1));
                        }
                        neutralized = true;
                        break;
                    }
                }
            }

            if (!neutralized) {
                // Check if effect already exists
                boolean existingFound = false;
                for (int i = 0; i < result.size(); i++) {
                    PotionEffectEntry current = result.get(i);
                    if (current.effectId().equals(incoming.effectId())) {
                        int maxAmp = Math.max(current.amplifier(), incoming.amplifier());
                        int maxDur = Math.max(current.duration(), incoming.duration());
                        result.set(i, new PotionEffectEntry(current.effectId(), maxDur, maxAmp));
                        existingFound = true;
                        break;
                    }
                }

                if (!existingFound && result.size() < limit) {
                    result.add(incoming);
                }
            }
        }

        int newColor = blendColors(existingColor, incomingColor);
        return new MixedPotionResult(result, newColor);
    }
}
