// Ported from Capes 1.5.11+26.2 by Cael (LGPL-2.1-only) from Kotlin to Java; modified by The Lads: repackaged into Lads Core.
package com.thelads.core.v26_2.embedded.capes.menu;

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
        int offset = (buttonW / 2) + 5;

        addRenderableWidget(Button.builder(Component.translatable("options.capes.selector"), button -> {
            minecraft.gui.setScreen(new SelectorMenu(lastScreen, options));
        }).pos((width / 2) - (buttonW / 2), 35).size(buttonW, 20).build())
                .active = !(this instanceof SelectorMenu);

        addRenderableWidget(Button.builder(Component.translatable("options.capes.toggle"), button -> {
            minecraft.gui.setScreen(new ToggleMenu(lastScreen, options));
        }).pos((width / 2) - (buttonW + offset), 35).size(buttonW, 20).build())
                .active = !(this instanceof ToggleMenu);

        addRenderableWidget(Button.builder(Component.translatable("options.capes.other"), button -> {
            minecraft.gui.setScreen(new OtherMenu(lastScreen, options));
        }).pos((width / 2) + offset, 35).size(buttonW, 20).build())
                .active = !(this instanceof OtherMenu);
    }

    @Override
    protected void addOptions() {
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.centeredText(font, title, width / 2, 20, 16777215);
    }
}
