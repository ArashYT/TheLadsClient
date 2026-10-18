package com.thelads.smp.features.arise.nb3.cosmeticarmor;

import com.thelads.smp.features.arise.nb3.AriseNb3Config;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cosmetic Armor Reworked:
 * Allows cosmetic armor pieces to override how armor looks while real equipped armor
 * preserves 100% of its protective stats, enchants, and attributes.
 * Includes per-slot visibility toggles.
 */
public final class CosmeticArmorFeature {
    private static final Map<UUID, CosmeticArmorData> DATA = new ConcurrentHashMap<>();

    private CosmeticArmorFeature() {}

    public static CosmeticArmorData getOrCreate(UUID uuid) {
        if (uuid == null) return new CosmeticArmorData();
        return DATA.computeIfAbsent(uuid, k -> new CosmeticArmorData());
    }

    public static void clear(UUID uuid) {
        if (uuid != null) DATA.remove(uuid);
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
     * Handles player death regarding cosmetic armor retention.
     */
    public static void onPlayerDeath(Player player) {
        if (!AriseNb3Config.get().cosmeticArmor.cosArmorKeepThroughDeath) {
            // Drop or clear cosmetic armor upon death if not kept
            CosmeticArmorData data = DATA.get(player.getUUID());
            if (data != null) {
                for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
                    ItemStack s = data.get(slot);
                    if (!s.isEmpty()) {
                        Containers.dropItemStack(player.level(), player.getX(), player.getY(), player.getZ(), s);
                    }
                }
                data.clear();
            }
        }
    }
}
