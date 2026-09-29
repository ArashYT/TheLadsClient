package com.thelads.core.v26_2.feature;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;

/** Lads-owned 9×3 preview with native stack-count decorations. */
public record ShulkerTooltip(ShulkerSummary contents) implements TooltipComponent, ClientTooltipComponent {
    @Override public int getWidth(Font font) { return 174; }
    @Override public int getHeight(Font font) { return 76; }
    @Override public void extractImage(Font font, int x, int y, int width, int height, GuiGraphicsExtractor graphics) {
        graphics.fill(x, y, x + 174, y + 73, 0xff151a21);
        graphics.fill(x, y, x + 174, y + 1, 0xff61d9b4);
        graphics.text(font, contents.occupied() + "/27 slots  ·  " + contents.count() + " items", x + 6, y + 4, 0xffb8c9d0, false);
        for (int slot = 0; slot < 27; slot++) {
            int left = x + 6 + slot % 9 * 18, top = y + 16 + slot / 9 * 18;
            graphics.fill(left, top, left + 17, top + 17, 0xff252e39);
            ItemStack item = contents.slots().get(slot);
            if (item.isEmpty()) continue;
            graphics.item(item, left, top);
            if (NativeQualityOfLife.bool("ShulkerBoxUtils", "Item Counts", true)) graphics.itemDecorations(font, item, left, top);
        }
    }
}
