package com.thelads.smp.features;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.RegistryOps;
import com.thelads.smp.config.LadsConfig;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Clean-room implementation of 'deathbackup':
 * Automatically snapshots a player's inventory and equipment before death into
 * config/ladssmp/death_backups/<uuid>.json so admins can inspect or restore items via /ladssmp.
 */
public final class DeathBackupManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("LadsSMP/DeathBackup");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private DeathBackupManager() {}

    public static void init() {
        ServerLivingEntityEvents.ALLOW_DEATH.register((entity, damageSource, damageAmount) -> {
            if (LadsConfig.get().features.deathBackup && entity instanceof ServerPlayer player) {
                recordDeathSnapshot(player, damageSource.getMsgId());
            }
            return true;
        });
    }

    public static synchronized void recordDeathSnapshot(ServerPlayer player, String deathCause) {
        try {
            Path dir = getBackupDir();
            Files.createDirectories(dir);
            Path file = dir.resolve(player.getStringUUID() + ".json");

            JsonArray history = new JsonArray();
            if (Files.exists(file)) {
                try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                    JsonElement parsed = JsonParser.parseReader(reader);
                    if (parsed != null && parsed.isJsonArray()) {
                        history = parsed.getAsJsonArray();
                    }
                } catch (Exception e) {
                    LOGGER.error("Failed to parse death backup for {}, backing up corrupt file", player.getScoreboardName(), e);
                    try {
                        Path corrupt = file.resolveSibling(file.getFileName().toString() + ".corrupt-" + System.currentTimeMillis());
                        Files.copy(file, corrupt, StandardCopyOption.REPLACE_EXISTING);
                    } catch (Exception ignored) {}
                }
            }

            JsonObject snapshot = new JsonObject();
            snapshot.addProperty("timestamp", Instant.now().toString());
            snapshot.addProperty("player", player.getScoreboardName());
            snapshot.addProperty("dimension", player.level().dimension().identifier().toString());
            snapshot.addProperty("x", player.getBlockX());
            snapshot.addProperty("y", player.getBlockY());
            snapshot.addProperty("z", player.getBlockZ());
            snapshot.addProperty("cause", deathCause);
            snapshot.addProperty("xpLevel", player.experienceLevel);

            RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, player.registryAccess());

            JsonArray itemsArr = new JsonArray();
            Inventory inv = player.getInventory();
            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack stack = inv.getItem(i);
                if (!stack.isEmpty()) {
                    itemsArr.add(serializeStack(ops, stack, i));
                }
            }

            JsonArray equipArr = new JsonArray();
            for (EquipmentSlot eq : EquipmentSlot.VALUES) {
                ItemStack eqStack = player.getItemBySlot(eq);
                if (!eqStack.isEmpty()) {
                    JsonObject entry = serializeStack(ops, eqStack, -1);
                    entry.addProperty("slot", eq.getName());
                    equipArr.add(entry);
                }
            }

            snapshot.add("inventory", itemsArr);
            snapshot.add("equipment", equipArr);
            history.add(snapshot);

            int max = Math.max(1, LadsConfig.get().features.maxBackupsPerPlayer);
            while (history.size() > max) {
                history.remove(0);
            }

            Path temp = file.resolveSibling(file.getFileName().toString() + ".tmp");
            try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
                GSON.toJson(history, writer);
            }
            Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            LOGGER.error("Failed to save death backup for {}", player.getScoreboardName(), e);
        }
    }

    public static synchronized List<JsonObject> getSnapshots(String playerUuid) {
        List<JsonObject> list = new ArrayList<>();
        Path file = getBackupDir().resolve(playerUuid + ".json");
        if (!Files.exists(file)) return list;
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonElement parsed = JsonParser.parseReader(reader);
            if (parsed != null && parsed.isJsonArray()) {
                for (JsonElement el : parsed.getAsJsonArray()) {
                    if (el.isJsonObject()) list.add(el.getAsJsonObject());
                }
            }
        } catch (Exception e) {
            LOGGER.error("Failed to read death backups for {}", playerUuid, e);
        }
        return list;
    }

    public static synchronized int restoreSnapshot(ServerPlayer target, int indexFromLatest) {
        List<JsonObject> snapshots = getSnapshots(target.getStringUUID());
        if (snapshots.isEmpty()) return -1;
        int actualIdx = snapshots.size() - 1 - Math.max(0, indexFromLatest);
        if (actualIdx < 0 || actualIdx >= snapshots.size()) return -1;

        RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, target.registryAccess());
        JsonObject snap = snapshots.get(actualIdx);
        int restored = 0;
        if (snap.has("inventory") && snap.get("inventory").isJsonArray()) {
            Inventory inv = target.getInventory();
            for (JsonElement el : snap.getAsJsonArray("inventory")) {
                if (!el.isJsonObject()) continue;
                JsonObject obj = el.getAsJsonObject();
                ItemStack stack = deserializeStack(ops, obj);
                if (!stack.isEmpty()) {
                    int slot = obj.has("slot") ? obj.get("slot").getAsInt() : -1;
                    if (slot >= 0 && slot < inv.getContainerSize() && inv.getItem(slot).isEmpty()) {
                        inv.setItem(slot, stack);
                    } else if (!inv.add(stack)) {
                        target.drop(stack, false, null);
                    }
                    restored++;
                }
            }
        }
        target.containerMenu.broadcastChanges();
        return restored;
    }

    private static Path getBackupDir() {
        try {
            return FabricLoader.getInstance().getConfigDir().resolve("ladssmp").resolve("death_backups");
        } catch (Throwable ignored) {
            return Path.of("build", "test-config", "ladssmp", "death_backups");
        }
    }

    public static JsonObject serializeStack(RegistryOps<JsonElement> ops, ItemStack stack, int slot) {
        JsonObject entry = new JsonObject();
        if (slot >= 0) {
            entry.addProperty("slot", slot);
        }
        entry.addProperty("id", BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        entry.addProperty("count", stack.getCount());
        entry.addProperty("damage", stack.getDamageValue());
        ItemStack.CODEC.encodeStart(ops, stack).result().ifPresent(json -> entry.add("stack", json));
        return entry;
    }

    public static ItemStack deserializeStack(RegistryOps<JsonElement> ops, JsonObject obj) {
        if (obj == null) return ItemStack.EMPTY;
        JsonElement stackElement = obj.has("stack") ? obj.get("stack") : (obj.has("item") ? obj.get("item") : null);
        if (stackElement != null) {
            ItemStack parsed = ItemStack.CODEC.parse(ops, stackElement).result().orElse(ItemStack.EMPTY);
            if (!parsed.isEmpty()) return parsed;
        }
        if (obj.has("id")) {
            String idStr = obj.get("id").getAsString();
            int count = obj.has("count") ? obj.get("count").getAsInt() : 1;
            int damage = obj.has("damage") ? obj.get("damage").getAsInt() : 0;
            Identifier id = Identifier.tryParse(idStr);
            if (id != null && BuiltInRegistries.ITEM.containsKey(id)) {
                Item item = BuiltInRegistries.ITEM.getValue(id);
                if (item != Items.AIR) {
                    ItemStack stack = new ItemStack(item, count);
                    if (damage > 0 && stack.isDamageableItem()) {
                        stack.setDamageValue(damage);
                    }
                    return stack;
                }
            }
        }
        return ItemStack.EMPTY;
    }
}
