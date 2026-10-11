package com.thelads.core.v1_8_9.gui;

import com.thelads.core.client.title.ButtonLift;
import com.thelads.core.client.title.TitleScreenTheme;
import com.thelads.core.v1_8_9.adapter.GuiLadsAdapter;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.GlStateManager;

/**
 * A Lads-drawn compact button on the title and pause screens (Essential's row, the fullscreen toggle): icon and label, or
 * the icon alone when the button is square (CompactButton121 on the other versions). Its screen calls press() when clicked.
 */
public final class CompactButton189 extends GuiButton {
    private final Supplier<String> icon;
    private final Runnable press;
    /** Optional: the label follows this each frame (the fullscreen toggle's "Fullscreen" / "Windowed"). */
    Supplier<String> dynamicLabel;

    public CompactButton189(int id, int x, int y, int width, int height, String label, Supplier<String> icon, Runnable press) {
        super(id, x, y, width, height, label);
        this.icon = icon;
        this.press = press;
    }

    public void press() { press.run(); }

    @Override
    public void drawButton(Minecraft mc, int mouseX, int mouseY) {
        drawButton(mc, mouseX, mouseY, 1f);
    }

    /** Drawn at {@code scale} screen pixels per GUI pixel (LadsTitleScreen189's layout scale): icon and label grow with the box. */
    public void drawButton(Minecraft mc, int mouseX, int mouseY, float scale) {
        if (!visible) return;
        if (dynamicLabel != null) displayString = dynamicLabel.get();
        hovered = mouseX >= xPosition && mouseY >= yPosition && mouseX < xPosition + width && mouseY < yPosition + height;
        int x = Math.round(xPosition / scale), y = Math.round(yPosition / scale);
        int w = Math.round((xPosition + width) / scale) - x, h = Math.round((yPosition + height) / scale) - y;
        GlStateManager.pushMatrix();
        GlStateManager.scale(scale, scale, 1f);
        TitleScreenTheme.renderCompactButton(new GuiLadsAdapter(mc.fontRendererObj, 0, 0), x, y, w, h,
            icon.get(), w <= h ? null : displayString, false, enabled, ButtonLift.update(this, enabled && hovered));
        GlStateManager.popMatrix();
    }
}
