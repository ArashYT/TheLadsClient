package com.thelads.core.v1_8_9.gui;

import com.thelads.core.client.sound.ModuleVolumes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.audio.SoundHandler;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.ResourceLocation;

/**
 * Music &amp; Sounds' Kill Banners and Combo Sounds sliders (GuiScreenOptionsSoundsMixin), drawn and dragged as the game's own
 * volume sliders (its private GuiScreenOptionsSounds.Button), bound to each module's Volume (ModuleVolumes).
 */
public final class ModuleVolumeSlider189 extends GuiButton {
    private final int slider;
    private float value;
    private boolean dragging;

    public ModuleVolumeSlider189(int id, int x, int y, int slider) {
        super(id, x, y, 150, 20, "");
        this.slider = slider;
        this.value = (float) ModuleVolumes.get(slider);
        label();
    }

    private void label() {
        int percent = ModuleVolumes.percent(value);
        displayString = ModuleVolumes.LABELS[slider] + ": " + (percent == 0 ? I18n.format("options.off") : percent + "%");
    }

    private void set(int mouseX) {
        value = Math.max(0, Math.min(1, (float) (mouseX - (xPosition + 4)) / (float) (width - 8)));
        ModuleVolumes.set(slider, value);
        label();
    }

    @Override
    protected int getHoverState(boolean mouseOver) {
        return 0;
    }

    @Override
    protected void mouseDragged(Minecraft mc, int mouseX, int mouseY) {
        if (!visible) return;
        if (dragging) set(mouseX);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        drawTexturedModalRect(xPosition + (int) (value * (float) (width - 8)), yPosition, 0, 66, 4, 20);
        drawTexturedModalRect(xPosition + (int) (value * (float) (width - 8)) + 4, yPosition, 196, 66, 4, 20);
    }

    @Override
    public boolean mousePressed(Minecraft mc, int mouseX, int mouseY) {
        if (!super.mousePressed(mc, mouseX, mouseY)) return false;
        set(mouseX);
        dragging = true;
        return true;
    }

    @Override
    public void playPressSound(SoundHandler soundHandler) {}

    @Override
    public void mouseReleased(int mouseX, int mouseY) {
        if (dragging) Minecraft.getMinecraft().getSoundHandler().playSound(PositionedSoundRecord.create(new ResourceLocation("gui.button.press"), 1.0F));
        dragging = false;
    }
}
