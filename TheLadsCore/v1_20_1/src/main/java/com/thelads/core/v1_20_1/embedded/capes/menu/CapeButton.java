// Adapted from Capes 1.5.10+26.3 by Cael (LGPL-2.1-only); modified by The Lads: ported to Minecraft 1.20.1 from the Lads 26.3 embedded copy.
package com.thelads.core.v1_20_1.embedded.capes.menu;

import com.mojang.blaze3d.systems.RenderSystem;
import com.thelads.core.v1_20_1.embedded.capes.Capes;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * The 20x20 icon button of the skin customization screen. 26.x uses a sprite button with the GUI atlas; 1.20.1 has neither
 * {@code SpriteIconButton} nor GUI sprites, so this is a plain button that draws a 16x16 texture over its face.
 */
public final class CapeButton extends Button {
    private static final ResourceLocation ICON = Capes.identifier("textures/gui/cape_options.png");

    public CapeButton(int x, int y, OnPress onPress) {
        super(x, y, 20, 20, Component.translatable("options.capes.title"), onPress, DEFAULT_NARRATION);
    }

    @Override
    public void renderString(GuiGraphics graphics, Font font, int color) {
        // the icon replaces the label
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        super.renderWidget(graphics, mouseX, mouseY, delta);
        RenderSystem.enableBlend();
        graphics.blit(ICON, getX() + 2, getY() + 2, 0.0F, 0.0F, 16, 16, 16, 16);
    }
}
