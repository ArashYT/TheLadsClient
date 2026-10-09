package com.thelads.core.v1_20_1.gui;

import com.thelads.core.client.title.ButtonLift;
import com.thelads.core.client.title.TitleScreenTheme;
import com.thelads.core.v1_20_1.adapter.GuiGraphicsLadsAdapter1201;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

/**
 * A Lads-drawn compact button on the title and pause screens (the fullscreen toggle, the language globe, the account
 * switcher): icon and label, or the icon alone with the label as its tooltip when the button is square.
 */
public final class CompactButton1201 extends Button {
    private final Supplier<String> icon;

    public CompactButton1201(int x, int y, int width, int height, Component label, Supplier<String> icon, OnPress press) {
        super(x, y, width, height, label, press, DEFAULT_NARRATION);
        this.icon = icon;
        if (width <= height) setTooltip(Tooltip.create(label));
    }

    /** Minecraft's own F11 (the option's change callback flips the window). */
    public static void toggleFullscreen(Button button) {
        var options = Minecraft.getInstance().options;
        options.fullscreen().set(!options.fullscreen().get());
        options.save();
    }

    public void setBounds(int x, int y, int width, int height) {
        Widgets1201.setBounds(this, x, y, width, height);
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float delta) {
        var adapter = new GuiGraphicsLadsAdapter1201(g);
        float hover = ButtonLift.update(this, active && isHoveredOrFocused());
        adapter.managed(() -> TitleScreenTheme.renderCompactButton(adapter, getX(), getY(), getWidth(), getHeight(),
            icon.get(), getWidth() <= getHeight() ? null : getMessage().getString(), isFocused(), active, hover));
    }
}
