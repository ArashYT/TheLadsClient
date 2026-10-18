package com.thelads.smp.features.arise.nb6;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.function.Consumer;

/**
 * Fireproof Fishing Line upgrade item.
 * Allows fishing bobbers to survive, float, and catch fish in Nether lava.
 */
public class FireproofLineItem extends Item {

    public FireproofLineItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.literal("§6Lava Fishing Upgrade"));
        tooltip.accept(Component.literal("§7Allows fishing bobbers to survive and fish in Nether lava."));
        tooltip.accept(Component.literal("§8Keep in inventory or offhand while fishing."));
    }

    public static boolean isFireproofUpgradePresent(Player player, ItemStack rod) {
        if (player == null) {
            return false;
        }

        // 1. Check if rod has the fireproof_line custom data component
        if (rod != null && !rod.isEmpty()) {
            CustomData customData = rod.get(DataComponents.CUSTOM_DATA);
            if (customData != null && customData.copyTag().contains("fireproof_line")) {
                return true;
            }
        }

        // 2. Check player's offhand
        if (player.getOffhandItem().is(AriseNB6Items.FIREPROOF_LINE)) {
            return true;
        }

        // 3. Check player's inventory
        return player.getInventory().contains(stack -> !stack.isEmpty() && stack.is(AriseNB6Items.FIREPROOF_LINE));
    }
}
