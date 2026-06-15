package com.thelads.client.gui;

import com.thelads.client.config.ConfigManager;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import com.mojang.blaze3d.vertex.PoseStack;

public class SettingsScreen extends Screen {
    private final Screen parent;

    public SettingsScreen(Screen parent) {
        super(Component.literal("The Lads Client Settings"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        this.addRenderableWidget(Button.builder(Component.literal("Cape: " + (ConfigManager.getConfig().isCapesEnabled() ? "ON" : "OFF")), button -> {
            boolean newState = !ConfigManager.getConfig().isCapesEnabled();
            ConfigManager.getConfig().setCapesEnabled(newState);
            button.setMessage(Component.literal("Cape: " + (newState ? "ON" : "OFF")));
        }).bounds(this.width / 2 - 100, this.height / 2 - 24, 200, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("Scale: " + (ConfigManager.getConfig().isUiScalingEnabled() ? "ON" : "OFF")), button -> {
            boolean newState = !ConfigManager.getConfig().isUiScalingEnabled();
            ConfigManager.getConfig().setUiScalingEnabled(newState);
            button.setMessage(Component.literal("Scale: " + (newState ? "ON" : "OFF")));
            if (this.minecraft != null) {
                this.minecraft.resizeGui();
            }
        }).bounds(this.width / 2 - 100, this.height / 2 + 4, 200, 20).build());
    }

    @Override
    public void extractRenderState(net.minecraft.client.gui.GuiGraphicsExtractor extractor, int mouseX, int mouseY, float delta) {
        extractor.fill(0, 0, this.width, this.height, 0x80000000);
        super.extractRenderState(extractor, mouseX, mouseY, delta);
        extractor.centeredText(this.font, this.title, this.width / 2, 20, 0xFFFFFF);
    }

    @Override
    public void onClose() {
        ConfigManager.save();
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }
}
