/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.Minecraft
 *  net.minecraft.client.gui.Font
 *  net.minecraft.client.renderer.RenderPipelines
 *  org.jetbrains.annotations.NotNull
 */
package dev.ultimatchamp.enhancedtooltips.component;

import dev.ultimatchamp.enhancedtooltips.component.EnhancedTooltipsTooltipComponent;
import dev.ultimatchamp.enhancedtooltips.config.EnhancedTooltipsConfig;
import java.awt.Color;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.NotNull;

public class TooltipBackgroundComponent
implements EnhancedTooltipsTooltipComponent {
    protected static final int INNER_PADDING = 4;

    public void render(GuiGraphicsExtractor context, int x, int y, int width, int height, int z, int page) throws Exception {
        int i = x - 4;
        int j = y - 4;
        int k = width + 8;
        int l = height + 8;
        int bgColor = EnhancedTooltipsConfig.load().background.backgroundColor.getRGB();
        if (EnhancedTooltipsConfig.load().background.backgroundMode == EnhancedTooltipsConfig.BackgroundMode.DEFAULT) {
            Identifier renderId = Identifier.withDefaultNamespace("tooltip/background");
            Identifier checkId = Identifier.withDefaultNamespace("textures/gui/sprites/" + renderId.getPath() + ".png");
            if (Minecraft.getInstance().getResourceManager().getResource(checkId).isPresent()) {
                this.renderBackgroundTexture(context, i, j, k, l, z, renderId);
            } else {
                this.renderBackground(context, i, j, k, l, z, new Color(-267386864, true).getRGB());
            }
        } else {
            this.renderBackground(context, i, j, k, l, z, bgColor);
        }
        this.renderBorder(context, i, j + 1, k, l, z, page);
    }

    protected void renderBorder(GuiGraphicsExtractor context, int x, int y, int width, int height, int z, int page) {
        if (EnhancedTooltipsConfig.load().border.borderColor == EnhancedTooltipsConfig.BorderColorMode.RARITY) {
            Identifier renderId = Identifier.withDefaultNamespace("tooltip/frame");
            Identifier checkId = Identifier.withDefaultNamespace("textures/gui/sprites/" + renderId.getPath() + ".png");
            if (Minecraft.getInstance().getResourceManager().getResource(checkId).isPresent()) {
                context.blitSprite(RenderPipelines.GUI_TEXTURED, renderId, x - 9, y - 10, width + 18, height + 18);
                return;
            }
        }
        int startColor = EnhancedTooltipsConfig.BorderColor.COMMON.getColor().getRGB();
        int endColor = EnhancedTooltipsConfig.BorderColor.END_COLOR.getColor().getRGB();
        if (EnhancedTooltipsConfig.load().border.borderColor == EnhancedTooltipsConfig.BorderColorMode.CUSTOM) {
            startColor = EnhancedTooltipsConfig.load().border.customBorderColors.common.getRGB();
            endColor = EnhancedTooltipsConfig.load().border.customBorderColors.endColor.getRGB();
        }
        this.renderVerticalLine(context, x, y, height - 2, z, startColor, endColor);
        this.renderVerticalLine(context, x + width - 1, y, height - 2, z, startColor, endColor);
        this.renderHorizontalLine(context, x, y - 1, width, z, startColor);
        this.renderHorizontalLine(context, x, y - 1 + height - 1, width, z, endColor);
    }

    protected void renderVerticalLine(GuiGraphicsExtractor context, int x, int y, int height, int z, int startColor, int endColor) {
        context.fillGradient(x, y, x + 1, y + height, startColor, endColor);
    }

    protected void renderHorizontalLine(GuiGraphicsExtractor context, int x, int y, int width, int z, int color) {
        context.fill(x, y, x + width, y + 1, color);
    }

    protected void renderRectangle(GuiGraphicsExtractor context, int x, int y, int width, int height, int z, int bgColor) {
        context.fill(x, y, x + width, y + height, bgColor);
    }

    protected void renderBackground(GuiGraphicsExtractor context, int x, int y, int width, int height, int z, int bgColor) {
        this.renderHorizontalLine(context, x, y - 1, width, z, bgColor);
        this.renderHorizontalLine(context, x, y + height, width, z, bgColor);
        this.renderRectangle(context, x, y, width, height, z, bgColor);
        this.renderVerticalLine(context, x - 1, y, height, z, bgColor, bgColor);
        this.renderVerticalLine(context, x + width, y, height, z, bgColor, bgColor);
    }

    protected void renderBackgroundTexture(GuiGraphicsExtractor context, int x, int y, int width, int height, int z, Identifier id) {
        context.blitSprite(RenderPipelines.GUI_TEXTURED, id, x - 9, y - 9, width + 18, height + 18);
    }

    public int getWidth(@NotNull Font textRenderer) {
        return 0;
    }
}

