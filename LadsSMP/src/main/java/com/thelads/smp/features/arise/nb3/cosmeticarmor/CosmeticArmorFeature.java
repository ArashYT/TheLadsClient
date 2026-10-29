package com.thelads.smp.features.arise.nb3.cosmeticarmor;

import com.thelads.smp.features.arise.nb3.AriseNb3Config;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.gamerules.GameRules;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cosmetic Armor Reworked:
 * Allows cosmetic armor pieces to override how armor looks while real equipped armor
 * preserves 100% of its protective stats, enchants, and attributes.
 * Includes per-slot visibility toggles.
 */
public final class CosmeticArmorFeature {
    /** Client render cache (filled only by SyncCosmeticArmorPayload). The server never reads it. */
    private static final Map<UUID, CosmeticArmorData> DATA = new ConcurrentHashMap<>();
    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    /** Server-authoritative store: real items, saved with the player, kept across respawn (death drops are handled below). */
    private static final class Store {
        // Holder class: created by init() at mod load, never touched by plain unit tests (needs Fabric's mixins)
        static final AttachmentType<CosmeticArmorData> TYPE = AttachmentRegistry.create(
                Identifier.fromNamespaceAndPath("ladssmp", "cosmetic_armor"),
                builder -> builder.persistent(CosmeticArmorData.CODEC).copyOnDeath());
    }
    /** Last accepted action tick per player (server thread only): caps clicks and the syncs they fan out. */
    private static final Map<ServerPlayer, Integer> LAST_ACTION_TICK = new WeakHashMap<>();
    private static final int MIN_ACTION_INTERVAL_TICKS = 2;

    private CosmeticArmorFeature() {}

    public static void init() {
        Store.TYPE.identifier(); // register before any player data loads, or saved cosmetics would be dropped
        // The client only sends slot clicks; the server moves the real items (never client-built stacks)
        ServerPlayNetworking.registerGlobalReceiver(UpdateCosmeticArmorPayload.TYPE, (payload, context) ->
                handleAction(context.player(), payload.slot(), payload.swap(), payload.visible()));
        EntityTrackingEvents.START_TRACKING.register((tracked, tracker) -> {
            if (tracked instanceof ServerPlayer trackedPlayer) sendTo(tracker, trackedPlayer);
        });
    }

    public static CosmeticArmorData getOrCreate(UUID uuid) {
        if (uuid == null) return new CosmeticArmorData();
        return DATA.computeIfAbsent(uuid, k -> new CosmeticArmorData());
    }

    /** Client: forget other servers' cosmetics on disconnect. */
    public static void clearClientCache() {
        DATA.clear();
    }

    /**
     * Resolves the visual item stack for the specified armor slot.
     * If cosmetic armor is equipped and visible, returns the cosmetic item.
     * If slot visibility is toggled off, returns ItemStack.EMPTY (hides visual armor).
     * Otherwise returns the real equipped armor stack.
     */
    public static ItemStack getVisualArmor(Player player, EquipmentSlot slot) {
        if (player == null) return ItemStack.EMPTY;
        ItemStack realEquipped = player.getItemBySlot(slot);

        if (!AriseNb3Config.get().cosmeticArmor.enabled) {
            return realEquipped;
        }

        CosmeticArmorData data = DATA.get(player.getUUID());
        if (data == null) {
            return realEquipped;
        }

        // Per-slot hide toggle
        if (!data.isVisible(slot)) {
            return ItemStack.EMPTY;
        }

        ItemStack cosmetic = data.get(slot);
        if (cosmetic != null && !cosmetic.isEmpty()) {
            return cosmetic;
        }

        return realEquipped;
    }

    /**
     * Server: a cosmetic slot click. Equip moves exactly one real item out of the main hand into the slot (any
     * previous cosmetic goes back to the inventory, or is dropped if it is full); an empty hand takes the cosmetic
     * out. Client-sent stacks are never trusted or stored. While the feature is disabled only taking items out works.
     */
    public static void handleAction(ServerPlayer player, int slotIndex, boolean swap, boolean visible) {
        if (slotIndex < 0 || slotIndex >= SLOTS.length || !player.isAlive() || player.isSpectator()) return;
        int now = player.tickCount;
        Integer last = LAST_ACTION_TICK.get(player);
        if (last != null && now - last < MIN_ACTION_INTERVAL_TICKS) return;
        LAST_ACTION_TICK.put(player, now);

        EquipmentSlot slot = SLOTS[slotIndex];
        CosmeticArmorData data = player.getAttachedOrCreate(Store.TYPE, CosmeticArmorData::new);
        boolean enabled = AriseNb3Config.get().cosmeticArmor.enabled;
        if (swap) {
            ItemStack held = player.getMainHandItem();
            ItemStack old = data.get(slot);
            if (held.isEmpty()) {
                if (old.isEmpty()) return;
                data.set(slot, ItemStack.EMPTY);
            } else {
                if (!enabled || !player.isEquippableInSlot(held, slot)) return;
                data.set(slot, held.split(1));
            }
            if (!old.isEmpty()) player.getInventory().placeItemBackInInventory(old, Prediction.SERVER_ONLY);
        } else {
            if (!enabled) return;
            data.setVisible(slot, visible);
        }
        player.setAttached(Store.TYPE, data);
        broadcast(player);
    }

    /** Server: send {@code about}'s cosmetics to {@code target} (if its client has Lads SMP). */
    public static void sendTo(ServerPlayer target, ServerPlayer about) {
        if (!ServerPlayNetworking.canSend(target, SyncCosmeticArmorPayload.TYPE)) return;
        CosmeticArmorData data = about.getAttached(Store.TYPE);
        if (data == null) data = new CosmeticArmorData();
        ServerPlayNetworking.send(target, new SyncCosmeticArmorPayload(about.getUUID(),
                data.get(EquipmentSlot.HEAD), data.get(EquipmentSlot.CHEST), data.get(EquipmentSlot.LEGS), data.get(EquipmentSlot.FEET),
                data.isVisible(EquipmentSlot.HEAD), data.isVisible(EquipmentSlot.CHEST),
                data.isVisible(EquipmentSlot.LEGS), data.isVisible(EquipmentSlot.FEET)));
    }

    private static void broadcast(ServerPlayer player) {
        for (ServerPlayer tracking : PlayerLookup.tracking(player)) sendTo(tracking, player);
        sendTo(player, player);
    }

    /**
     * Server: on death the stored (real) cosmetics drop like the rest of the inventory, unless
     * cosArmorKeepThroughDeath or the keepInventory game rule keeps them (the attachment is copied on respawn).
     */
    public static void onPlayerDeath(ServerPlayer player) {
        CosmeticArmorData data = player.getAttached(Store.TYPE);
        if (data == null || data.isEmpty()) return;
        if (AriseNb3Config.get().cosmeticArmor.cosArmorKeepThroughDeath
                || player.level().getGameRules().get(GameRules.KEEP_INVENTORY)) return;
        for (EquipmentSlot slot : SLOTS) {
            ItemStack s = data.get(slot);
            if (!s.isEmpty()) {
                Containers.dropItemStack(player.level(), player.getX(), player.getY(), player.getZ(), s);
            }
        }
        data.clear();
        player.setAttached(Store.TYPE, data);
        broadcast(player);
    }
}
