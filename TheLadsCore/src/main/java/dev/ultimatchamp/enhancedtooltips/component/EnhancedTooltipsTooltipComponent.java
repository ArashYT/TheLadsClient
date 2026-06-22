/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.gui.Font
 *  net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent
 *  org.jetbrains.annotations.NotNull
 */
package dev.ultimatchamp.enhancedtooltips.component;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import org.jetbrains.annotations.NotNull;

public interface EnhancedTooltipsTooltipComponent
extends ClientTooltipComponent {
    default public int height() {
        return 0;
    }

    default public int getHeight(@NotNull Font font) {
        return this.height();
    }

    default public void drawText(@NotNull GuiGraphicsExtractor graphics, @NotNull Font font, int x, int y) {
        ClientTooltipComponent.super.extractText(graphics, font, x, y);
    }

    default public void extractText(@NotNull GuiGraphicsExtractor graphics, @NotNull Font font, int x, int y) {
        this.drawText(graphics, font, x, y);
    }

    default public void drawImage(Font font, int x, int y, int width, int height, GuiGraphicsExtractor graphics) {
        ClientTooltipComponent.super.extractImage(font, x, y, width, height, graphics);
    }

    default public void extractImage(@NotNull Font font, int x, int y, int width, int height, @NotNull GuiGraphicsExtractor graphics) {
        this.drawImage(font, x, y, width, height, graphics);
    }
}

