package com.thelads.core.v1_8_9.feature.jade;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.fml.client.config.GuiSlider;
import org.lwjgl.input.Keyboard;

/** "Settings and addons" of the Lads Jade module on 1.8.9: info lines, behaviour and placement, with a live preview of the panel. */
final class JadeSettingsScreen189 extends GuiScreen implements GuiSlider.ISlider {
    private static final String[] ANCHORS = {"Top left", "Top centre", "Top right", "Middle left", "Centre", "Middle right",
        "Bottom left", "Bottom centre", "Bottom right"};
    private static final int W = 130, H = 20, GAP = 4, POSITION = 100, SCALE = 101, OPACITY = 102, OFFSET_X = 103, OFFSET_Y = 104, DONE = 105;
    private final GuiScreen parent;
    private final Jade189.Config config = Jade189.config();

    JadeSettingsScreen189(GuiScreen parent) {
        this.parent = parent;
    }

    @Override
    public void initGui() {
        buttonList.clear();
        toggle("Icon", () -> config.icon, v -> config.icon = v);
        toggle("Mod name", () -> config.modName, v -> config.modName = v);
        toggle("Health", () -> config.health, v -> config.health = v);
        toggle("Armor", () -> config.armor, v -> config.armor = v);
        toggle("Crop growth", () -> config.growth, v -> config.growth = v);
        toggle("Item frames", () -> config.containers, v -> config.containers = v);
        toggle("Redstone", () -> config.redstone, v -> config.redstone = v);
        toggle("Block states", () -> config.blockStates, v -> config.blockStates = v);
        toggle("Spawners", () -> config.spawner, v -> config.spawner = v);
        toggle("Signs", () -> config.signs, v -> config.signs = v);
        toggle("Banners", () -> config.banners, v -> config.banners = v);
        toggle("Harvest tool", () -> config.harvest, v -> config.harvest = v);
        toggle("Animals", () -> config.animals, v -> config.animals = v);
        toggle("Display fluids", () -> config.displayFluids, v -> config.displayFluids = v);
        toggle("Only sneaking", () -> config.onlySneaking, v -> config.onlySneaking = v);
        toggle("Hide in creative", () -> config.hideCreative, v -> config.hideCreative = v);
        toggle("Hide with F3", () -> config.hideWithDebug, v -> config.hideWithDebug = v);
        buttonList.add(new GuiButton(POSITION, 0, 0, W, H, "Position: " + ANCHORS[config.anchor]));
        GuiSlider scale = new GuiSlider(SCALE, 0, 0, W, H, "Scale: ", "x", 0.5, 2.0, config.scale, true, true, this);
        scale.precision = 2;
        scale.updateSlider();
        buttonList.add(scale);
        buttonList.add(new GuiSlider(OPACITY, 0, 0, W, H, "Opacity: ", "%", 0, 100, Math.round(config.opacity * 100), false, true, this));
        buttonList.add(new GuiSlider(OFFSET_X, 0, 0, W, H, "Offset X: ", "", -200, 200, config.offsetX, false, true, this));
        buttonList.add(new GuiSlider(OFFSET_Y, 0, 0, W, H, "Offset Y: ", "", -200, 200, config.offsetY, false, true, this));

        int columns = Math.max(1, Math.min(3, (width - 20 + GAP) / (W + GAP)));
        int left = (width - (columns * W + (columns - 1) * GAP)) / 2;
        for (int i = 0; i < buttonList.size(); i++) {
            GuiButton button = buttonList.get(i);
            button.xPosition = left + (i % columns) * (W + GAP);
            button.yPosition = 28 + (i / columns) * (H + GAP);
        }
        buttonList.add(new GuiButton(DONE, width / 2 - 100, height - 26, 200, H, "Done"));
    }

    private void toggle(String label, BooleanSupplier value, Consumer<Boolean> setter) {
        buttonList.add(new Toggle(buttonList.size(), label, value, setter));
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button instanceof Toggle) ((Toggle) button).flip();
        else if (button.id == POSITION) {
            config.anchor = (config.anchor + 1) % ANCHORS.length;
            button.displayString = "Position: " + ANCHORS[config.anchor];
        } else if (button.id == DONE) mc.displayGuiScreen(parent);
    }

    @Override
    public void onChangeSliderValue(GuiSlider slider) {
        if (slider.id == SCALE) config.scale = Math.round(slider.getValue() * 20) / 20f;
        else if (slider.id == OPACITY) config.opacity = slider.getValueInt() / 100f;
        else if (slider.id == OFFSET_X) config.offsetX = slider.getValueInt();
        else if (slider.id == OFFSET_Y) config.offsetY = slider.getValueInt();
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (keyCode == Keyboard.KEY_ESCAPE) mc.displayGuiScreen(parent);
    }

    /** Saved however the screen closes (Done, Escape, or another screen opening over it). */
    @Override
    public void onGuiClosed() {
        config.save();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawCenteredString(fontRendererObj, "Jade Settings", width / 2, 12, 0xFFFFFF);
        super.drawScreen(mouseX, mouseY, partialTicks);
        if (!Jade189.drawPreview(width, height)) {
            drawCenteredString(fontRendererObj, "Look at a block or mob to preview the panel", width / 2, height - 40, 0xA0A0A0);
        }
    }

    /** An ON/OFF button for one config switch; the panel is rebuilt every tick, so a change shows at once. */
    private static final class Toggle extends GuiButton {
        private final String label;
        private final BooleanSupplier value;
        private final Consumer<Boolean> setter;

        Toggle(int id, String label, BooleanSupplier value, Consumer<Boolean> setter) {
            super(id, 0, 0, W, H, "");
            this.label = label;
            this.value = value;
            this.setter = setter;
            refresh();
        }

        void flip() {
            setter.accept(!value.getAsBoolean());
            refresh();
        }

        private void refresh() {
            displayString = label + ": " + (value.getAsBoolean() ? "ON" : "OFF");
        }
    }
}
