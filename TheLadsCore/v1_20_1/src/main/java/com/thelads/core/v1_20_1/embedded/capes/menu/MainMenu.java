// Ported from Capes 1.5.10+26.3 by Cael (LGPL-2.1-only) from Kotlin to Java; modified by The Lads: ported to Minecraft 1.20.1 from the Lads 26.3 embedded copy.
package com.thelads.core.v1_20_1.embedded.capes.menu;

import net.minecraft.client.Options;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.OptionsSubScreen;
import net.minecraft.client.gui.screens.Screen;
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
            minecraft.setScreen(new SelectorMenu(lastScreen, options));
        }).pos((width / 2) - (buttonW / 2), 35).size(buttonW, 20).build())
                .active = !(this instanceof SelectorMenu);

        addRenderableWidget(Button.builder(Component.translatable("options.capes.toggle"), button -> {
            minecraft.setScreen(new ToggleMenu(lastScreen, options));
        }).pos((width / 2) - (buttonW + offset), 35).size(buttonW, 20).build())
                .active = !(this instanceof ToggleMenu);

        addRenderableWidget(Button.builder(Component.translatable("options.capes.other"), button -> {
            minecraft.setScreen(new OtherMenu(lastScreen, options));
        }).pos((width / 2) + offset, 35).size(buttonW, 20).build())
                .active = !(this instanceof OtherMenu);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, delta);
        graphics.drawCenteredString(font, title, width / 2, 20, 16777215);
    }
}
