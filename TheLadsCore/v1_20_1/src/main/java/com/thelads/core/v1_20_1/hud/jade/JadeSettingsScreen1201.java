package com.thelads.core.v1_20_1.hud.jade;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/** "Settings and addons" of the Lads Jade module: info lines, behaviour and placement, with a live preview of the panel. */
final class JadeSettingsScreen1201 extends Screen {
    private static final String[] ANCHORS = {"Top left", "Top centre", "Top right", "Middle left", "Centre", "Middle right",
        "Bottom left", "Bottom centre", "Bottom right"};
    private static final int W = 130, H = 20, GAP = 4;
    private final Screen parent;
    private final JadeConfig1201 config = JadeConfig1201.get();

    JadeSettingsScreen1201(Screen parent) {
        super(Component.literal("Jade Settings"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        List<AbstractWidget> widgets = new ArrayList<>();
        toggle(widgets, "Icon", config.icon, v -> config.icon = v);
        toggle(widgets, "Mod name", config.modName, v -> config.modName = v);
        toggle(widgets, "Health", config.health, v -> config.health = v);
        toggle(widgets, "Armor", config.armor, v -> config.armor = v);
        toggle(widgets, "Crop growth", config.growth, v -> config.growth = v);
        toggle(widgets, "Containers", config.containers, v -> config.containers = v);
        toggle(widgets, "Redstone", config.redstone, v -> config.redstone = v);
        toggle(widgets, "Block states", config.blockStates, v -> config.blockStates = v);
        toggle(widgets, "Spawners", config.spawner, v -> config.spawner = v);
        toggle(widgets, "Signs", config.signs, v -> config.signs = v);
        toggle(widgets, "Banners", config.banners, v -> config.banners = v);
        toggle(widgets, "Harvest tool", config.harvest, v -> config.harvest = v);
        toggle(widgets, "Animals", config.animals, v -> config.animals = v);
        toggle(widgets, "Display fluids", config.displayFluids, v -> config.displayFluids = v);
        toggle(widgets, "Only sneaking", config.onlySneaking, v -> config.onlySneaking = v);
        toggle(widgets, "Hide in creative", config.hideCreative, v -> config.hideCreative = v);
        toggle(widgets, "Hide with F3", config.hideWithDebug, v -> config.hideWithDebug = v);
        widgets.add(CycleButton.<Integer>builder(i -> Component.literal(ANCHORS[i]))
            .withValues(List.of(0, 1, 2, 3, 4, 5, 6, 7, 8)).withInitialValue(config.anchor)
            .create(0, 0, W, H, Component.literal("Position"), (button, value) -> config.anchor = value));
        widgets.add(new Slider("Scale", 0.5, 2.0, 0.05, config.scale, v -> config.scale = (float) v, v -> String.format("%.2fx", v)));
        widgets.add(new Slider("Opacity", 0, 1, 0.05, config.opacity, v -> config.opacity = (float) v, v -> Math.round(v * 100) + "%"));
        widgets.add(new Slider("Offset X", -200, 200, 1, config.offsetX, v -> config.offsetX = (int) v, v -> Integer.toString((int) v)));
        widgets.add(new Slider("Offset Y", -200, 200, 1, config.offsetY, v -> config.offsetY = (int) v, v -> Integer.toString((int) v)));

        int columns = Math.max(1, Math.min(3, (width - 20 + GAP) / (W + GAP)));
        int left = (width - (columns * W + (columns - 1) * GAP)) / 2;
        for (int i = 0; i < widgets.size(); i++) {
            AbstractWidget widget = widgets.get(i);
            widget.setPosition(left + (i % columns) * (W + GAP), 28 + (i / columns) * (H + GAP));
            addRenderableWidget(widget);
        }
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose()).bounds(width / 2 - 100, height - 26, 200, H).build());
    }

    private static void toggle(List<AbstractWidget> widgets, String label, boolean value, Consumer<Boolean> setter) {
        widgets.add(CycleButton.onOffBuilder(value).create(0, 0, W, H, Component.literal(label), (button, on) -> {
            setter.accept(on);
            JadeFeatures1201.markDirty();
        }));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.drawCenteredString(font, title, width / 2, 12, 0xFFFFFF);
        super.render(graphics, mouseX, mouseY, partialTick);
        if (!JadeFeatures1201.drawPreview(graphics)) {
            graphics.drawCenteredString(font, Component.literal("Look at a block or mob to preview the panel"), width / 2, height - 40, 0xA0A0A0);
        }
    }

    @Override
    public void onClose() {
        config.save();
        JadeFeatures1201.markDirty();
        minecraft.setScreen(parent);
    }

    private static final class Slider extends AbstractSliderButton {
        private final String label;
        private final double min, max, step;
        private final DoubleConsumer setter;
        private final DoubleFunction<String> format;

        Slider(String label, double min, double max, double step, double current, DoubleConsumer setter, DoubleFunction<String> format) {
            super(0, 0, W, H, Component.empty(), (current - min) / (max - min));
            this.label = label;
            this.min = min;
            this.max = max;
            this.step = step;
            this.setter = setter;
            this.format = format;
            updateMessage();
        }

        private double current() {
            return Math.round((min + value * (max - min)) / step) * step;
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.literal(label + ": " + format.apply(current())));
        }

        @Override
        protected void applyValue() {
            setter.accept(current());
        }
    }
}
