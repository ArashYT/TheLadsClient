package com.thelads.core.v26_2.feature;

import com.thelads.core.client.HypixelSafetyManager;
import com.thelads.core.config.ModuleSupport;
import com.thelads.core.modules.StackRefillModule;
import java.util.Objects;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;

/**
 * Consolidates StackRefill as a Lads module: automatically refills a depleted hand stack or broken tool
 * from matching stacks in the main inventory (slots 9..35). Automatically disabled on Hypixel.
 */
public final class NativeStackRefill {
    private static final boolean EXTERNAL_LOADED = FabricLoader.getInstance().isModLoaded("stackrefill");

    private static int lastSelectedSlot = -1;
    private static ItemStack lastMainHandStack = ItemStack.EMPTY;
    private static ItemStack lastOffHandStack = ItemStack.EMPTY;

    private NativeStackRefill() {}

    public static void register() {
        ModuleSupport.registerBuiltIn(StackRefillModule.NAME);
    }

    public static void tick() {
        if (EXTERNAL_LOADED || HypixelSafetyManager.isHypixel()) {
            reset();
            return;
        }
        StackRefillModule mod = StackRefillModule.get();
        if (mod == null || !mod.isEnabled()) {
            reset();
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || !player.isAlive() || player.isCreative() || player.isSpectator()
                || mc.gui.screen() != null || mc.gameMode == null) {
            reset();
            return;
        }

        Inventory inv = player.getInventory();
        int currentSlot = inv.getSelectedSlot();
        ItemStack currentMain = player.getMainHandItem();
        ItemStack currentOff = player.getOffhandItem();

        if (currentSlot == lastSelectedSlot && !lastMainHandStack.isEmpty() && currentMain.isEmpty()) {
            boolean wasDamageable = lastMainHandStack.isDamageableItem();
            boolean shouldRefill = wasDamageable ? mod.canReplaceBrokenTools() : mod.canRefillConsumables();
            if (shouldRefill) {
                int matchSlot = findMatchingInventorySlot(inv, lastMainHandStack);
                if (matchSlot != -1) {
                    mc.gameMode.handleContainerInput(
                            player.inventoryMenu.containerId,
                            matchSlot,
                            currentSlot,
                            ContainerInput.SWAP,
                            player
                    );
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
                    mc.gameMode.handleContainerInput(
                            player.inventoryMenu.containerId,
                            matchSlot,
                            40,
                            ContainerInput.SWAP,
                            player
                    );
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

    private static boolean matchesRefillCandidate(ItemStack previous, ItemStack candidate) {
        if (!candidate.is(previous.getItem())) {
            return false;
        }
        if (previous.has(DataComponents.POTION_CONTENTS) || candidate.has(DataComponents.POTION_CONTENTS)) {
            return Objects.equals(
                    previous.get(DataComponents.POTION_CONTENTS),
                    candidate.get(DataComponents.POTION_CONTENTS)
            );
        }
        return true;
    }

    private static void reset() {
        lastSelectedSlot = -1;
        lastMainHandStack = ItemStack.EMPTY;
        lastOffHandStack = ItemStack.EMPTY;
    }
}
