package com.thelads.smp;

import com.thelads.smp.command.LadsCommands;
import com.thelads.smp.compat.LadsDataUpgrader;
import com.thelads.smp.config.LadsConfig;
import com.thelads.smp.disabler.ItemDisablerSystem;
import com.thelads.smp.features.ConsolidatedFeatures;
import com.thelads.smp.features.DeathBackupManager;
import com.thelads.smp.features.EnchantingCommandsManager;
import com.thelads.smp.features.LoginProtectionManager;
import com.thelads.smp.features.NameTagTweaksManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main Fabric entrypoint for The Lads SMP custom mod (modid: ladssmp).
 */
public final class LadsSMPMod implements ModInitializer {
    public static final String MOD_ID = "ladssmp";
    public static final Logger LOGGER = LoggerFactory.getLogger("LadsSMP");

    @Override
    public void onInitialize() {
        long startMs = System.currentTimeMillis();
        LadsConfig config = LadsConfig.load();
        ItemDisablerSystem.init();
        ConsolidatedFeatures.init();
        DeathBackupManager.init();
        LadsCommands.init();
        NameTagTweaksManager.init();
        EnchantingCommandsManager.init();
        LoginProtectionManager.init();
        com.thelads.smp.features.arise.nb4.AriseNB4FeatureManager.init();

        // Sanitize player inventory immediately upon joining the server
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            if (handler.player != null) {
                ItemDisablerSystem.sanitizePlayerInventory(handler.player, "player_join");
            }
        });

        // Log 26.3 data upgrader & loot balancer summary when server finishes starting
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            if (LadsConfig.get().compat.logUpgradeSummary) {
                LOGGER.info("[Lads SMP] Server started! 26.3 Data Upgrader Summary: upgraded {} loot tables ({} conditions), {} modded recipes; stripped {} disabled/unbalanced loot entries and {} recipes.",
                        LadsDataUpgrader.UPGRADED_LOOT_TABLES.get(),
                        LadsDataUpgrader.UPGRADED_LOOT_CONDITIONS.get(),
                        LadsDataUpgrader.UPGRADED_RECIPES.get(),
                        LadsDataUpgrader.FILTERED_LOOT_ENTRIES.get(),
                        LadsDataUpgrader.FILTERED_RECIPES.get());
            }
        });

        LOGGER.info("[Lads SMP] Initialized in {} ms (AlwaysEat={}, NoTemptDelay={}, SpawnerControl={}, CryingPortals={}, EggHatch={}, DoubleDoors={}, PetProtect={}, AttrFix={}, DeathBackup={})",
                System.currentTimeMillis() - startMs,
                config.features.alwaysEat,
                config.features.noAnimalTemptDelay,
                config.features.betterSpawnerControl,
                config.features.cryingPortals,
                config.features.despawningEggsHatch,
                config.features.doubleDoors,
                config.features.friendlyFireProtection,
                config.features.attributeFix,
                config.features.deathBackup);
    }
}
