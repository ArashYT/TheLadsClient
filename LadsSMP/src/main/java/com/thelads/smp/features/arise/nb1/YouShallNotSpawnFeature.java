package com.thelads.smp.features.arise.nb1;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Mob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.regex.Pattern;

/**
 * Native clean-room implementation of You Shall Not Spawn!
 * Allows configuring mob spawn blacklists and custom spawn chances globally or per dimension.
 */
public final class YouShallNotSpawnFeature {
    private static final Logger LOGGER = LoggerFactory.getLogger("LadsSMP-YouShallNotSpawn");

    public static void init() {
        LOGGER.info("[Lads SMP] You Shall Not Spawn! initialized ({} dimension rules)",
                AriseNB1Config.get().youShallNotSpawn.dimensions.size());
    }

    public static boolean isEnabled() {
        return AriseNB1Config.get().youShallNotSpawn.enabled;
    }

    public static boolean shouldBlockSpawn(Mob mob, String dimension, RandomSource random) {
        if (!isEnabled()) return false;

        String entityId = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).toString();

        for (AriseNB1Config.YouShallNotSpawnConfig.SpawnRule rule : AriseNB1Config.get().youShallNotSpawn.dimensions) {
            if (matches(rule.dimension, dimension) && matches(rule.entityId, entityId)) {
                if (rule.spawn_chance <= 0.0) {
                    return true; // completely disabled
                }
                if (rule.spawn_chance < 1.0) {
                    // Random roll against spawn chance: if roll fails, block spawn
                    return random.nextDouble() >= rule.spawn_chance;
                }
            }
        }
        return false;
    }

    private static boolean matches(String pattern, String value) {
        if (pattern == null || pattern.isEmpty() || pattern.equals("*")) return true;
        if (pattern.startsWith("!")) {
            try {
                return Pattern.compile(pattern.substring(1)).matcher(value).matches();
            } catch (Exception e) {
                return pattern.substring(1).equalsIgnoreCase(value);
            }
        }
        return pattern.equalsIgnoreCase(value);
    }
}
