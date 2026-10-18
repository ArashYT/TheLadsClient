package com.thelads.smp.features.arise.nb6;

import com.thelads.smp.features.arise.nb6.network.SyncFishingJournalPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side manager for persistent player Fishing Journal records.
 * Files stored under config/ladssmp/fishing_journal/<uuid>.json.
 */
public final class FishingJournalManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("LadsSMP/FishingJournalManager");
    private static final File STORAGE_DIR = new File("config/ladssmp/fishing_journal");
    private static final Map<UUID, FishingJournalData> CACHE = new ConcurrentHashMap<>();

    private FishingJournalManager() {}

    public static synchronized FishingJournalData get(UUID uuid) {
        if (uuid == null) {
            return new FishingJournalData();
        }
        return CACHE.computeIfAbsent(uuid, FishingJournalManager::loadFromDisk);
    }

    private static FishingJournalData loadFromDisk(UUID uuid) {
        File file = new File(STORAGE_DIR, uuid.toString() + ".json");
        if (file.exists()) {
            try (FileReader reader = new FileReader(file)) {
                StringBuilder sb = new StringBuilder();
                char[] buf = new char[1024];
                int read;
                while ((read = reader.read(buf)) != -1) {
                    sb.append(buf, 0, read);
                }
                FishingJournalData data = FishingJournalData.fromJson(sb.toString());
                if (data != null) {
                    data.setPlayerId(uuid);
                    return data;
                }
            } catch (Exception e) {
                LOGGER.error("[Fishing Journal] Failed to load journal for {}. Initializing fresh.", uuid, e);
            }
        }
        FishingJournalData fresh = new FishingJournalData(uuid);
        saveToDisk(fresh);
        return fresh;
    }

    public static synchronized void save(UUID uuid) {
        FishingJournalData data = CACHE.get(uuid);
        if (data != null) {
            saveToDisk(data);
        }
    }

    public static synchronized void saveAll() {
        for (FishingJournalData data : CACHE.values()) {
            saveToDisk(data);
        }
    }

    private static void saveToDisk(FishingJournalData data) {
        if (data == null || data.getPlayerId() == null) {
            return;
        }
        if (!STORAGE_DIR.exists()) {
            STORAGE_DIR.mkdirs();
        }
        File file = new File(STORAGE_DIR, data.getPlayerId().toString() + ".json");
        try (FileWriter writer = new FileWriter(file)) {
            writer.write(data.toJson());
        } catch (IOException e) {
            LOGGER.error("[Fishing Journal] Failed to write journal file for {}", data.getPlayerId(), e);
        }
    }

    public static synchronized void recordCatch(ServerPlayer player, String fishId) {
        if (player == null || fishId == null) {
            return;
        }
        FishingJournalData data = get(player.getUUID());
        boolean isFirstCatch = data.recordCatch(fishId);
        saveToDisk(data);
        syncToPlayer(player);

        if (isFirstCatch && AriseNB6Config.get().showToasts) {
            String formattedName = formatSpeciesName(fishId);
            player.sendSystemMessage(
                    Component.literal("§6§l[Fishing Journal] §aNew Species Discovered: §e" + formattedName + "§a!"),
                    false
            );
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6f, 1.2f);
        }
    }

    public static void syncToPlayer(ServerPlayer player) {
        if (player == null) {
            return;
        }
        FishingJournalData data = get(player.getUUID());
        SyncFishingJournalPayload payload = new SyncFishingJournalPayload(new ArrayList<>(data.getCaughtFish()));
        try {
            ServerPlayNetworking.send(player, payload);
        } catch (Exception e) {
            LOGGER.debug("[Fishing Journal] Note: Could not send sync payload to {} (likely client not ready): {}",
                    player.getScoreboardName(), e.getMessage());
        }
    }

    public static String formatSpeciesName(String id) {
        if (id == null) return "";
        String[] parts = id.split("_");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (!p.isEmpty()) {
                if (!sb.isEmpty()) sb.append(" ");
                sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1).toLowerCase());
            }
        }
        return sb.toString();
    }
}
