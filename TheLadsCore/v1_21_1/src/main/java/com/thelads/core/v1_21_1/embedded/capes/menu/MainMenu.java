// Ported from Capes 1.5.4+1.21 by Cael (LGPL-2.1-only) from Kotlin to Java; modified by The Lads: remapped to Mojang mappings and repackaged into Lads Core.
package com.thelads.core.v1_21_1.embedded.capes.menu;

import net.minecraft.client.Options;
import net.minecraft.client.gui.GuiGraphics;
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
            this.minecraft.setScreen(new SelectorMenu(this.lastScreen, this.options));
        }).pos((width / 2) - (buttonW / 2), 35).size(buttonW, 20).build())
                .active = !(this instanceof SelectorMenu);

        addRenderableWidget(Button.builder(Component.translatable("options.capes.toggle"), button -> {
            this.minecraft.setScreen(new ToggleMenu(this.lastScreen, this.options));
        }).pos((width / 2) - (buttonW + offset), 35).size(buttonW, 20).build())
                .active = !(this instanceof ToggleMenu);

        addRenderableWidget(Button.builder(Component.translatable("options.capes.other"), button -> {
            this.minecraft.setScreen(new OtherMenu(this.lastScreen, this.options));
        }).pos((width / 2) + offset, 35).size(buttonW, 20).build())
                .active = !(this instanceof OtherMenu);

    }

    @Override
    protected void addOptions() {
        // Upstream: TODO("Not yet implemented"); never reached, init() is overridden without calling super.
        throw new UnsupportedOperationException("An operation is not implemented: Not yet implemented");
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);
        context.drawCenteredString(this.font, this.title, this.width / 2, 20, 16777215);
        super.render(context, mouseX, mouseY, delta);
    }
}
