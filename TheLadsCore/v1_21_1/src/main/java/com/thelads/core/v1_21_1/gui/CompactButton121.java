package com.thelads.core.v1_21_1.gui;

import com.thelads.core.client.title.ButtonLift;
import com.thelads.core.client.title.TitleScreenTheme;
import com.thelads.core.v1_21_1.adapter.GuiGraphicsLadsAdapter;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

/**
 * A Lads-drawn compact button on the title and pause screens (Essential's row, the fullscreen toggle): icon and label,
 * or the icon alone with the label as its tooltip when the button is square (26.x CompactButton26).
 */
public final class CompactButton121 extends Button {
    private final Supplier<String> icon;

    public CompactButton121(int x, int y, int width, int height, Component label, Supplier<String> icon, OnPress press) {
        super(x, y, width, height, label, press, DEFAULT_NARRATION);
        this.icon = icon;
        if (width <= height) setTooltip(Tooltip.create(label));
    }

    /** Minecraft's own F11. */
    public static void toggleFullscreen(Button button) {
        var minecraft = Minecraft.getInstance();
        minecraft.getWindow().toggleFullScreen();
        minecraft.options.fullscreen().set(minecraft.getWindow().isFullscreen());
        minecraft.options.save();
    }

    @Override protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float delta) {
        TitleScreenTheme.renderCompactButton(new GuiGraphicsLadsAdapter(g), getX(), getY(), getWidth(), getHeight(),
            icon.get(), getWidth() <= getHeight() ? null : getMessage().getString(), isFocused(), active,
            ButtonLift.update(this, active && isHoveredOrFocused()));
    }
}
