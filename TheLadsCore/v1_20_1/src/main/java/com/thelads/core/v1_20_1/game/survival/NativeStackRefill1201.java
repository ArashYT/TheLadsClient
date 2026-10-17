package com.thelads.core.v1_20_1.game.survival;

import com.thelads.core.client.HypixelSafetyManager;
import com.thelads.core.config.ModuleSupport;
import com.thelads.core.modules.StackRefillModule;
import com.thelads.core.v1_20_1.compat.ModPresence1201;
import java.util.Objects;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;

/**
 * StackRefill on 1.20.1 (the 26.3 NativeStackRefill): a depleted hand stack or broken tool is refilled from a matching stack in the
 * main inventory (slots 9..35) by a swap click. Off on Hypixel and while the StackRefill mod is installed.
 */
public final class NativeStackRefill1201 {
    /** QA only: how many refills were done (ParityProbe1201). */
    public static volatile int refills;
    private static int lastSelectedSlot = -1;
    private static ItemStack lastMainHandStack = ItemStack.EMPTY;
    private static ItemStack lastOffHandStack = ItemStack.EMPTY;

    private NativeStackRefill1201() {}

    public static void register() {
        ModuleSupport.registerBuiltIn(StackRefillModule.NAME);
        ClientTickEvents.END_CLIENT_TICK.register(NativeStackRefill1201::tick);
    }

    private static void tick(Minecraft mc) {
        if (ModPresence1201.loaded("stackrefill") || HypixelSafetyManager.isHypixel()) {
            reset();
            return;
        }
        StackRefillModule mod = StackRefillModule.get();
        if (mod == null || !mod.isEnabled()) {
            reset();
            return;
        }

        LocalPlayer player = mc.player;
        if (player == null || !player.isAlive() || player.isCreative() || player.isSpectator()
                || mc.screen != null || mc.gameMode == null) {
            reset();
            return;
        }

        Inventory inv = player.getInventory();
        int currentSlot = inv.selected;
        ItemStack currentMain = player.getMainHandItem();
        ItemStack currentOff = player.getOffhandItem();

        if (currentSlot == lastSelectedSlot && !lastMainHandStack.isEmpty() && currentMain.isEmpty()) {
            boolean wasDamageable = lastMainHandStack.isDamageableItem();
            boolean shouldRefill = wasDamageable ? mod.canReplaceBrokenTools() : mod.canRefillConsumables();
            if (shouldRefill) {
                int matchSlot = findMatchingInventorySlot(inv, lastMainHandStack);
                if (matchSlot != -1) {
                    mc.gameMode.handleInventoryMouseClick(player.inventoryMenu.containerId, matchSlot, currentSlot, ClickType.SWAP, player);
                    refills++;
                    currentMain = player.getMainHandItem();
                }
            }
        }

        if (mod.canRefillOffhand() && !lastOffHandStack.isEmpty() && currentOff.isEmpty()) {
            boolean wasDamageable = lastOffHandStack.isDamageableItem();
            boolean shouldRefill = wasDamageable ? mod.canReplaceBrokenTools() : mod.canRefillConsumables();
            if (shouldRefill) {
                int matchSlot = findMatchingInventorySlot(inv, lastOffHandStack);
                if (matchSlot != -1) {
                    mc.gameMode.handleInventoryMouseClick(player.inventoryMenu.containerId, matchSlot, 40, ClickType.SWAP, player);
                    refills++;
                    currentOff = player.getOffhandItem();
                }
            }
        }

        lastSelectedSlot = currentSlot;
        lastMainHandStack = currentMain.isEmpty() ? ItemStack.EMPTY : currentMain.copy();
        lastOffHandStack = currentOff.isEmpty() ? ItemStack.EMPTY : currentOff.copy();
    }

    private static int findMatchingInventorySlot(Inventory inv, ItemStack previous) {
        for (int slot = 35; slot >= 9; slot--) {
            ItemStack candidate = inv.getItem(slot);
            if (!candidate.isEmpty() && matchesRefillCandidate(previous, candidate)) {
                return slot;
            }
        }
        return -1;
    }

    /** Same item; potions and tipped arrows also the same potion (26.3 compares the POTION_CONTENTS component, 1.20.1 keeps it in NBT). */
    private static boolean matchesRefillCandidate(ItemStack previous, ItemStack candidate) {
        if (!candidate.is(previous.getItem())) {
            return false;
        }
        CompoundTag a = previous.getTag(), b = candidate.getTag();
        if (!hasPotion(a) && !hasPotion(b)) {
            return true;
        }
        return a != null && b != null
                && Objects.equals(a.get("Potion"), b.get("Potion"))
                && Objects.equals(a.get("CustomPotionEffects"), b.get("CustomPotionEffects"))
                && Objects.equals(a.get("CustomPotionColor"), b.get("CustomPotionColor"));
    }

    private static boolean hasPotion(CompoundTag tag) {
        return tag != null && (tag.contains("Potion") || tag.contains("CustomPotionEffects") || tag.contains("CustomPotionColor"));
    }

    private static void reset() {
        lastSelectedSlot = -1;
        lastMainHandStack = ItemStack.EMPTY;
        lastOffHandStack = ItemStack.EMPTY;
    }
}
