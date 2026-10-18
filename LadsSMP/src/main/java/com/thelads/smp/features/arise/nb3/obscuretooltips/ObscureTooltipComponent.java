package com.thelads.smp.features.arise.nb3.obscuretooltips;

import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;

/**
 * Common data component for Obscure Tooltips holding the preview item stack.
 */
public record ObscureTooltipComponent(ItemStack stack) implements TooltipComponent {}
