package com.thelads.core.client.capes.menu;

import net.minecraft.client.Options;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.network.chat.Component;

public class MainMenu extends OptionsSubScreen {
    public MainMenu(Screen parent, Options gameOptions) {
        super(parent, gameOptions, Component.translatable("options.capes.title"));
    }

    @Override
    protected void init() {
        int buttonW = 100;
        int offset = buttonW / 2 + 5;

        Button selectorBtn = Button.builder(Component.translatable("options.capes.selector"), button -> {
            if (this.minecraft != null) {
                this.minecraft.setScreenAndShow(new SelectorMenu(this.lastScreen, this.options));
            }
        }).pos(this.width / 2 - buttonW / 2, 35).size(buttonW, 20).build();
        selectorBtn.active = !(this instanceof SelectorMenu);
        this.addRenderableWidget(selectorBtn);

        Button toggleBtn = Button.builder(Component.translatable("options.capes.toggle"), button -> {
            if (this.minecraft != null) {
                this.minecraft.setScreenAndShow(new ToggleMenu(this.lastScreen, this.options));
            }
        }).pos(this.width / 2 - (buttonW + offset), 35).size(buttonW, 20).build();
        toggleBtn.active = !(this instanceof ToggleMenu);
        this.addRenderableWidget(toggleBtn);

        Button otherBtn = Button.builder(Component.translatable("options.capes.other"), button -> {
            if (this.minecraft != null) {
                this.minecraft.setScreenAndShow(new OtherMenu(this.lastScreen, this.options));
            }
        }).pos(this.width / 2 + offset, 35).size(buttonW, 20).build();
        otherBtn.active = !(this instanceof OtherMenu);
        this.addRenderableWidget(otherBtn);
    }

    @Override
    protected void addOptions() {
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.centeredText(this.font, this.title, this.width / 2, 20, 0xFFFFFF);
    }
}
