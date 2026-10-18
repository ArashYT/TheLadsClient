package com.thelads.smp.features.arise.nb3.obscuretooltips;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.world.item.ItemStack;

/**
 * Client-side 3D/item miniature preview component inside item tooltips.
 */
public class ClientObscureTooltipComponent implements ClientTooltipComponent {
    private final ItemStack stack;

    public ClientObscureTooltipComponent(ItemStack stack) {
        this.stack = stack;
    }

    @Override
    public int getHeight(Font font) {
        return 22;
    }

    @Override
    public int getWidth(Font font) {
        return 22;
    }

    @Override
    public void extractImage(Font font, int x, int y, int width, int height, GuiGraphicsExtractor guiGraphics) {
        if (stack != null && !stack.isEmpty()) {
            guiGraphics.item(stack, x + 3, y + 3);
        }
    }
}
