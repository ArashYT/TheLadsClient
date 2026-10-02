package com.thelads.core.v1_8_9.gui;

import com.thelads.core.client.title.ButtonLift;
import com.thelads.core.client.title.TitleScreenTheme;
import com.thelads.core.v1_8_9.adapter.GuiLadsAdapter;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;

/**
 * A Lads-drawn compact button on the title and pause screens (Essential's row, the fullscreen toggle): icon and label, or
 * the icon alone when the button is square (CompactButton121 on the other versions). Its screen calls press() when clicked.
 */
public final class CompactButton189 extends GuiButton {
    private final Supplier<String> icon;
    private final Runnable press;

    public CompactButton189(int id, int x, int y, int width, int height, String label, Supplier<String> icon, Runnable press) {
        super(id, x, y, width, height, label);
        this.icon = icon;
        this.press = press;
    }

    public void press() { press.run(); }

    @Override
    public void drawButton(Minecraft mc, int mouseX, int mouseY) {
        if (!visible) return;
        hovered = mouseX >= xPosition && mouseY >= yPosition && mouseX < xPosition + width && mouseY < yPosition + height;
        TitleScreenTheme.renderCompactButton(new GuiLadsAdapter(mc.fontRendererObj, 0, 0), xPosition, yPosition, width, height,
            icon.get(), width <= height ? null : displayString, false, enabled, ButtonLift.update(this, enabled && hovered));
    }
}
