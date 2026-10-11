package com.thelads.core.v26_2.gui;

import com.thelads.core.client.title.ButtonLift;
import com.thelads.core.client.title.TitleScreenTheme;
import com.thelads.core.v26_2.adapter.GuiGraphicsExtractorLadsAdapter;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

/**
 * A Lads-drawn compact button on the title and pause screens (Essential's row, the fullscreen toggle): icon and label,
 * or the icon alone with the label as its tooltip when the button is square.
 */
public final class CompactButton26 extends Button {
    private final Supplier<String> icon;
    private Supplier<Component> dynamicLabel;

    public CompactButton26(int x, int y, int width, int height, Component label, Supplier<String> icon, OnPress press) {
        super(x, y, width, height, label, press, DEFAULT_NARRATION);
        this.icon = icon;
        if (width <= height) setTooltip(Tooltip.create(label));
    }

    /**
     * The top-right fullscreen toggle, labelled by what pressing it does ("Fullscreen" / "Windowed"); placed by
     * placeFullscreen.
     */
    public static CompactButton26 fullscreen() {
        var button = new CompactButton26(0, 6, TitleScreenTheme.HEADER_SIZE, TitleScreenTheme.HEADER_SIZE, fullscreenLabel(),
            () -> Minecraft.getInstance().options.fullscreen().get() ? "windowed" : "fullscreen", CompactButton26::toggleFullscreen);
        button.dynamicLabel = CompactButton26::fullscreenLabel;
        return button;
    }

    private static Component fullscreenLabel() {
        return Minecraft.getInstance().options.fullscreen().get() ? Component.literal(TitleScreenTheme.WINDOWED) : Component.translatable("options.fullscreen");
    }

    /** Sizes the fullscreen toggle to its label (an icon alone on a narrow screen), keeping its right edge; returns its width. */
    public int placeFullscreen(Font font, int screenWidth) {
        int widest = Math.max(font.width(Component.translatable("options.fullscreen")), font.width(TitleScreenTheme.WINDOWED));
        int w = TitleScreenTheme.fullscreenWidth(screenWidth, widest);
        setRectangle(w, TitleScreenTheme.HEADER_SIZE, TitleScreenTheme.fullscreenX(screenWidth, w), 6);
        setTooltip(w <= TitleScreenTheme.HEADER_SIZE ? Tooltip.create(getMessage()) : null);
        return w;
    }

    /** Minecraft's own F11. */
    public static void toggleFullscreen(Button button) {
        var options = Minecraft.getInstance().options;
        options.fullscreen().set(!options.fullscreen().get());
        options.save();
    }

    @Override protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        if (dynamicLabel != null) setMessage(dynamicLabel.get());
        TitleScreenTheme.renderCompactButton(new GuiGraphicsExtractorLadsAdapter(g), getX(), getY(), getWidth(), getHeight(),
            icon.get(), getWidth() <= getHeight() ? null : getMessage().getString(), isFocused(), active,
            ButtonLift.update(this, active && isHoveredOrFocused()));
    }
}
