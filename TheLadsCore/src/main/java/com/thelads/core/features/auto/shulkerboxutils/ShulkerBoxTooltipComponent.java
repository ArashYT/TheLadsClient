package com.thelads.core.features.auto.shulkerboxutils;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/**
 * Renders a graphical 9x3 grid preview of a shulker box's contents in the tooltip,
 * matching the vanilla shulker box GUI texture. Ported from shulkerboxutils 1.3.0.
 */
public class ShulkerBoxTooltipComponent implements ClientTooltipComponent {
    private static final Identifier TEXTURE = Identifier.withDefaultNamespace("textures/gui/container/shulker_box.png");
    private static final int WIDTH = 176;
    private static final int HEIGHT = 71;
    private static final int TEX_SIZE = 256;

    private final NonNullList<ItemStack> items;

    public ShulkerBoxTooltipComponent(ShulkerBoxTooltipData data) {
        this.items = data.items();
    }

    @Override
    public int getWidth(Font font) {
        return WIDTH;
    }

    @Override
    public int getHeight(Font font) {
        return HEIGHT;
    }

    @Override
    public void extractImage(Font font, int x, int y, int width, int height, GuiGraphicsExtractor graphics) {
        // Top border, slot grid, bottom border, blitted from the vanilla shulker GUI atlas.
        graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, x, y, 0.0F, 0.0F, WIDTH, 7, TEX_SIZE, TEX_SIZE);
        graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, x, y + 7, 0.0F, 17.0F, WIDTH, 54, TEX_SIZE, TEX_SIZE);
        graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, x, y + 7 + 54, 0.0F, 160.0F, WIDTH, 7, TEX_SIZE, TEX_SIZE);

        for (int i = 0; i < this.items.size(); i++) {
            ItemStack stack = this.items.get(i);
            if (!stack.isEmpty()) {
                int col = i % 9;
                int row = i / 9;
                int itemX = x + 8 + col * 18;
                int itemY = y + 8 + row * 18;
                graphics.item(stack, itemX, itemY);
                graphics.itemDecorations(font, stack, itemX, itemY);
            }
        }
    }
}
