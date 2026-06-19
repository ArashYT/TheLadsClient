package com.thelads.core.features.auto.shulkerboxutils;

import net.minecraft.core.NonNullList;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;

/** Tooltip data payload carrying a shulker box's 27 slots. */
public record ShulkerBoxTooltipData(NonNullList<ItemStack> items) implements TooltipComponent {
}
