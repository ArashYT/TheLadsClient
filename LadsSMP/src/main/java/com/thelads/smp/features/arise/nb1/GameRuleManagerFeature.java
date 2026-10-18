package com.thelads.smp.features.arise.nb1;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Native clean-room implementation of GameRule Manager (The Ruler).
 * Enforces global default gamerules from configuration on world load and server startup.
 */
public final class GameRuleManagerFeature {
    private static final Logger LOGGER = LoggerFactory.getLogger("LadsSMP-GameRuleManager");

    public static void init() {
        ServerLifecycleEvents.SERVER_STARTED.register(GameRuleManagerFeature::applyForcedRules);
        LOGGER.info("[Lads SMP] GameRule Manager initialized");
    }

    public static boolean isEnabled() {
        return AriseNB1Config.get().gameruleManager.enabled;
    }

    public static void applyForcedRules(MinecraftServer server) {
        if (!isEnabled()) return;

        Map<String, String> rules = AriseNB1Config.get().gameruleManager.forcedRules;
        if (rules.isEmpty()) return;

        int applied = 0;
        for (Map.Entry<String, String> entry : rules.entrySet()) {
            String command = "gamerule " + entry.getKey() + " " + entry.getValue();
            try {
                server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), command);
                applied++;
            } catch (Exception e) {
                LOGGER.error("Failed to apply forced gamerule: {}", command, e);
            }
        }
        LOGGER.info("[Lads SMP] Applied {} forced gamerules from arise-nb1 configuration", applied);
    }
}
