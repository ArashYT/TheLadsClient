package com.thelads.core.v1_8_9.gui;

import com.thelads.core.client.BackgroundFps;
import com.thelads.core.config.ConfigManager;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.SliderOption;
import com.thelads.core.v1_8_9.feature.DynamicFPS189;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.fml.client.config.GuiSlider;

/** Dynamic FPS's Background Profiles on 1.8.9 (26.x opens its full profile editor): each background state's frame rate and volume. */
public final class DynamicFpsScreen189 extends GuiScreen implements GuiSlider.ISlider {
    private static final int UNFOCUSED = 1, UNFOCUSED_VOLUME = 2, HIDDEN = 3, HIDDEN_VOLUME = 4, IDLE = 5, IDLE_AFTER = 6;
    private final GuiScreen parent;

    public DynamicFpsScreen189(GuiScreen parent) { this.parent = parent; }

    private static SliderOption slider(String name) { return (SliderOption) ModuleManager.getInstance().getModule(DynamicFPS189.NAME).getOption(name); }

    @Override
    public void initGui() {
        buttonList.clear();
        BackgroundFps.Profile p = DynamicFPS189.PROFILE;
        int x = width / 2 - 155, y = height / 6;
        add(UNFOCUSED, x, y, "Unfocused FPS", 0, 260, slider("Unfocused FPS").getValue());
        add(UNFOCUSED_VOLUME, x + 160, y, "Unfocused volume", 0, 100, p.unfocusedVolume * 100);
        add(HIDDEN, x, y + 24, "Minimized FPS", 0, 260, slider("Hidden FPS").getValue());
        add(HIDDEN_VOLUME, x + 160, y + 24, "Minimized volume", 0, 100, p.hiddenVolume * 100);
        add(IDLE, x, y + 48, "Idle FPS", 0, 260, p.idleFps);
        add(IDLE_AFTER, x + 160, y + 48, "Idle after", 0, 60, p.idleMinutes);
        buttonList.add(new GuiButton(0, width / 2 - 100, y + 96, 200, 20, "Done"));
    }

    private void add(int id, int x, int y, String name, double min, double max, double value) {
        GuiSlider slider = new GuiSlider(id, x, y, 150, 20, name + ": ", "", min, max, value, false, true, this);
        buttonList.add(slider);
        onChangeSliderValue(slider);
    }

    @Override
    public void onChangeSliderValue(GuiSlider slider) {
        int value = slider.getValueInt();
        String name = slider.dispString;
        switch (slider.id) {
            case UNFOCUSED: case HIDDEN: case IDLE:
                slider.displayString = name + (value == BackgroundFps.SUSPEND ? "Paused" : value >= BackgroundFps.UNLIMITED ? "Unlimited" : value);
                break;
            case IDLE_AFTER: slider.displayString = name + (value == 0 ? "Never" : value + " min"); break;
            default: slider.displayString = name + value + "%";
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 0) mc.displayGuiScreen(parent);
    }

    /** Saves on every way out (Done or Escape). */
    @Override
    public void onGuiClosed() {
        BackgroundFps.Profile p = DynamicFPS189.PROFILE;
        for (GuiButton button : buttonList) {
            if (!(button instanceof GuiSlider)) continue;
            int value = ((GuiSlider) button).getValueInt();
            switch (button.id) {
                case UNFOCUSED: slider("Unfocused FPS").setValue(value); break;
                case HIDDEN: slider("Hidden FPS").setValue(value); break;
                case UNFOCUSED_VOLUME: p.unfocusedVolume = value / 100f; break;
                case HIDDEN_VOLUME: p.hiddenVolume = value / 100f; break;
                case IDLE: p.idleFps = value; break;
                case IDLE_AFTER: p.idleMinutes = value; break;
                default:
            }
        }
        Module module = ModuleManager.getInstance().getModule(DynamicFPS189.NAME);
        module.touch();
        DynamicFPS189.save();
        ConfigManager.save();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawCenteredString(fontRendererObj, "Dynamic FPS: Background Profiles", width / 2, height / 6 - 28, 0xFFFFFF);
        drawCenteredString(fontRendererObj, "Never above your own FPS limit. 0 pauses rendering; 260 is unlimited.", width / 2, height / 6 - 14, 0xA0A0A0);
        drawCenteredString(fontRendererObj, "Under the mouse an unfocused window runs at 60 with full volume; idle counts while focused.", width / 2, height / 6 + 76, 0xA0A0A0);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }
}
