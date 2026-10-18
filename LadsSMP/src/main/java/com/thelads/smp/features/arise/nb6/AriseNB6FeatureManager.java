package com.thelads.smp.features.arise.nb6;

import com.thelads.smp.features.arise.nb6.network.NB6Payloads;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main feature coordinator for Native Batch NB6 (Tide-style Fishing Overhaul).
 */
public final class AriseNB6FeatureManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("LadsSMP/AriseNB6");
    private static boolean initialized = false;

    private AriseNB6FeatureManager() {}

    public static synchronized void init() {
        if (initialized) {
            return;
        }
        initialized = true;

        AriseNB6Config config = AriseNB6Config.load();
        LOGGER.info("[Arise NB6] Initializing Tide-style Fishing Overhaul (enabled={}, holdToCast={}, minigame={}, lava={})...",
                config.enabled, config.holdToCast, config.doMinigame, config.lavaFishing);

        // Always register items unconditionally to preserve world/registry safety
        AriseNB6Items.registerAll();

        // Register networking payloads
        NB6Payloads.registerAll();

        // Register server packet receivers
        FishingMinigameServer.init();

        // Server tick: manage active fishing minigames and timeouts
        ServerTickEvents.END_SERVER_TICK.register(FishingMinigameServer::tickSessions);

        // Server stop: flush all fishing journal persistence to disk
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            LOGGER.info("[Arise NB6] Saving all player Fishing Journals...");
            FishingJournalManager.saveAll();
        });

        // Player join: sync fishing journal and optionally gift initial journal
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer player = handler.player;
            if (player != null) {
                FishingJournalManager.syncToPlayer(player);
                if (AriseNB6Config.get().enabled && AriseNB6Config.get().giveJournal) {
                    FishingJournalData data = FishingJournalManager.get(player.getUUID());
                    if (!data.hasReceivedStartingJournal()) {
                        data.setReceivedStartingJournal(true);
                        FishingJournalManager.save(player.getUUID());
                        if (AriseNB6Items.FISHING_JOURNAL != null) {
                            ItemStack journal = new ItemStack(AriseNB6Items.FISHING_JOURNAL);
                            if (!player.getInventory().add(journal)) {
                                Containers.dropItemStack(player.level(), player.getX(), player.getY(), player.getZ(), journal);
                            }
                            player.sendSystemMessage(
                                    Component.literal("§6[Fishing Overhaul] §eReceived a §6Fishing Journal§e! Right-click to record your catches."),
                                    false
                            );
                        }
                    }
                }
            }
        });
    }
}
