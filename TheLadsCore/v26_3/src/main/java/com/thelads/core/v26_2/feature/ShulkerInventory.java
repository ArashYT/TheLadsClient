package com.thelads.core.v26_2.feature;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.block.ShulkerBoxBlock;

public final class ShulkerInventory {
    private static final ThreadLocal<Boolean> DRAWING_BADGE = ThreadLocal.withInitial(() -> false);
    private ShulkerInventory() {}

    public static boolean eligible(ItemStack item) {
        if (!NativeQualityOfLife.enabled("ShulkerBoxUtils") || item.isEmpty()
            || !(item.getItem() instanceof BlockItem block) || !(block.getBlock() instanceof ShulkerBoxBlock)) return false;
        TooltipDisplay display = item.getOrDefault(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT);
        return !display.hideTooltip() && display.shows(DataComponents.CONTAINER);
    }

    public static boolean preview(ItemStack stack) {
        return eligible(stack) && NativeQualityOfLife.bool("ShulkerBoxUtils", "Contents Preview", true);
    }

    public static void decorate(GuiGraphicsExtractor graphics, ItemStack item, int x, int y) {
        if (DRAWING_BADGE.get() || !eligible(item)) return;
        boolean badge = NativeQualityOfLife.bool("ShulkerBoxUtils", "Inventory Badge", true);
        boolean bar = NativeQualityOfLife.bool("ShulkerBoxUtils", "Free Slot Bar", true);
        if (!badge && !bar) return;
        ShulkerSummary contents = ShulkerSummary.of(item.get(DataComponents.CONTAINER));
        if (badge && contents.badgeVisible()) {
            var pose = graphics.pose();
            pose.pushMatrix();
            DRAWING_BADGE.set(true);
            try {
                pose.translate(x + 4, y + 4);
                pose.scale(.5f, .5f);
                graphics.item(contents.first(), 0, 0);
            } finally {
                DRAWING_BADGE.set(false);
                pose.popMatrix();
            }
        }
        if (bar) {
            graphics.fill(x + 2, y + 13, x + 15, y + 15, 0xff000000);
            graphics.fill(x + 2, y + 13, x + 2 + contents.freeBarWidth(), y + 14, contents.freeBarColor());
        }
    }
}
