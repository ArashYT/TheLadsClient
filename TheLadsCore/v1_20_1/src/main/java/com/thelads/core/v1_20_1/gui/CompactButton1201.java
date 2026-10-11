package com.thelads.core.v1_20_1.gui;

import com.thelads.core.client.title.ButtonLift;
import com.thelads.core.client.title.TitleScreenTheme;
import com.thelads.core.v1_20_1.adapter.GuiGraphicsLadsAdapter1201;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
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
    private Supplier<Component> dynamicLabel;

    public CompactButton1201(int x, int y, int width, int height, Component label, Supplier<String> icon, OnPress press) {
        super(x, y, width, height, label, press, DEFAULT_NARRATION);
        this.icon = icon;
        if (width <= height) setTooltip(Tooltip.create(label));
    }

    /**
     * The top-right fullscreen toggle, labelled by what pressing it does ("Fullscreen" / "Windowed"); placed by
     * placeFullscreen.
     */
    public static CompactButton1201 fullscreen() {
        var button = new CompactButton1201(0, 6, TitleScreenTheme.HEADER_SIZE, TitleScreenTheme.HEADER_SIZE, fullscreenLabel(),
            () -> Minecraft.getInstance().options.fullscreen().get() ? "windowed" : "fullscreen", CompactButton1201::toggleFullscreen);
        button.dynamicLabel = CompactButton1201::fullscreenLabel;
        return button;
    }

    private static Component fullscreenLabel() {
        return Minecraft.getInstance().options.fullscreen().get() ? Component.literal(TitleScreenTheme.WINDOWED) : Component.translatable("options.fullscreen");
    }

    /** Sizes the fullscreen toggle to its label (an icon alone on a narrow screen), keeping its right edge; returns its width. */
    public int placeFullscreen(Font font, int screenWidth) {
        int widest = Math.max(font.width(Component.translatable("options.fullscreen")), font.width(TitleScreenTheme.WINDOWED));
        int w = TitleScreenTheme.fullscreenWidth(screenWidth, widest);
        setBounds(TitleScreenTheme.fullscreenX(screenWidth, w), 6, w, TitleScreenTheme.HEADER_SIZE);
        setTooltip(w <= TitleScreenTheme.HEADER_SIZE ? Tooltip.create(getMessage()) : null);
        return w;
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
        if (dynamicLabel != null) setMessage(dynamicLabel.get());
        var adapter = new GuiGraphicsLadsAdapter1201(g);
        float hover = ButtonLift.update(this, active && isHoveredOrFocused());
        adapter.managed(() -> TitleScreenTheme.renderCompactButton(adapter, getX(), getY(), getWidth(), getHeight(),
            icon.get(), getWidth() <= getHeight() ? null : getMessage().getString(), isFocused(), active, hover));
    }
}
