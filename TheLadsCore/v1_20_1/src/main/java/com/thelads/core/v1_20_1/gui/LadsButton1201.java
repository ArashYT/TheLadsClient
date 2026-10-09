package com.thelads.core.v1_20_1.gui;

import com.thelads.core.client.title.ButtonLift;
import com.thelads.core.client.title.TitleScreenTheme;
import com.thelads.core.v1_20_1.adapter.GuiGraphicsLadsAdapter1201;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** A Lads-styled button with the soft pink label of the Skin screens (26.3's LadsButton). */
public final class LadsButton1201 extends Button {
    public LadsButton1201(int x, int y, int width, int height, Component label, OnPress action) {
        super(x, y, width, height, label, action, DEFAULT_NARRATION);
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float delta) {
        TitleScreenTheme.renderButtonSurface(new GuiGraphicsLadsAdapter1201(g), getX(), getY(), getWidth(), getHeight(),
            isHoveredOrFocused(), isFocused(), active, alpha, ButtonLift.eased(this));
        g.drawCenteredString(Minecraft.getInstance().font, getMessage(), getX() + getWidth() / 2,
            getY() + (getHeight() - 9) / 2, active ? 0xFFFCECF3 : 0xFF86717B);
    }
}
