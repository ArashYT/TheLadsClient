/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.Minecraft
 *  net.minecraft.client.renderer.RenderPipelines
 *  net.minecraft.world.item.ItemStack
 *  net.minecraft.world.item.Rarity
 */
package dev.ultimatchamp.enhancedtooltips.component;

import dev.ultimatchamp.enhancedtooltips.component.TooltipBackgroundComponent;
import dev.ultimatchamp.enhancedtooltips.config.EnhancedTooltipsConfig;
import dev.ultimatchamp.enhancedtooltips.tooltip.TooltipHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;

public class TooltipBorderColorComponent
extends TooltipBackgroundComponent {
    private final ItemStack stack;
    private final EnhancedTooltipsConfig config;

    public TooltipBorderColorComponent(ItemStack stack) {
        this.stack = stack;
        this.config = EnhancedTooltipsConfig.load();
    }

    @Override
    protected void renderBorder(GuiGraphicsExtractor context, int x, int y, int width, int height, int z, int page) {
        Integer[] color;
        if (this.config.border.borderColor == EnhancedTooltipsConfig.BorderColorMode.RARITY) {
            Identifier renderId = this.stack.getRarity() == Rarity.COMMON ? Identifier.withDefaultNamespace("tooltip/frame") : Identifier.fromNamespaceAndPath("enhancedtooltips", "tooltip/frame/" + this.stack.getRarity().name().toLowerCase());
            Identifier checkId = Identifier.fromNamespaceAndPath(renderId.getNamespace(), "textures/gui/sprites/" + renderId.getPath() + ".png");
            if (Minecraft.getInstance().getResourceManager().getResource(checkId).isPresent()) {
                context.blitSprite(RenderPipelines.GUI_TEXTURED, renderId, x - 9, y - 10, width + 18, height + 18);
                return;
            }
        }
        int startColor = (color = TooltipHelper.getItemBorderColor(this.stack))[0] == null || color[0] == -1 ? EnhancedTooltipsConfig.BorderColor.COMMON.getColor().getRGB() : 0xFF000000 | color[0];
        int endColor = EnhancedTooltipsConfig.BorderColor.END_COLOR.getColor().getRGB();
        if (this.config.border.borderColor == EnhancedTooltipsConfig.BorderColorMode.ITEM_NAME && color[1] != null) {
            endColor = 0xFF000000 | color[1];
        }
        if (this.config.border.borderColor == EnhancedTooltipsConfig.BorderColorMode.CUSTOM) {
            startColor = this.stack.getRarity() == Rarity.UNCOMMON ? this.config.border.customBorderColors.uncommon.getRGB() : (this.stack.getRarity() == Rarity.RARE ? this.config.border.customBorderColors.rare.getRGB() : (this.stack.getRarity() == Rarity.EPIC ? this.config.border.customBorderColors.epic.getRGB() : this.config.border.customBorderColors.common.getRGB()));
            endColor = this.config.border.customBorderColors.endColor.getRGB();
        }
        this.renderVerticalLine(context, x, y, height - 2, z, startColor, endColor);
        this.renderVerticalLine(context, x + width - 1, y, height - 2, z, startColor, endColor);
        this.renderHorizontalLine(context, x, y - 1, width, z, startColor);
        this.renderHorizontalLine(context, x, y - 1 + height - 1, width, z, endColor);
    }
}

