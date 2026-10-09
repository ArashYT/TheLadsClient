// Ported from Capes 1.5.10+26.3 by Cael (LGPL-2.1-only) from Kotlin to Java; modified by The Lads: ported to Minecraft 1.20.1 from the Lads 26.3 embedded copy.
package com.thelads.core.v1_20_1.embedded.capes.menu;

import com.thelads.core.v1_20_1.embedded.capes.CapeConfig;
import com.thelads.core.v1_20_1.embedded.capes.Capes;
import com.thelads.core.v1_20_1.embedded.capes.render.PlayerPreview;
import net.minecraft.client.Options;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

public class SelectorMenu extends MainMenu {

    private long lastTime = 0L;

    public SelectorMenu(Screen parent, Options gameOptions) {
        super(parent, gameOptions);
    }

    @Override
    protected void init() {
        super.init();

        int buttonW = 200;
        CapeConfig config = Capes.getConfig();

        addRenderableWidget(Button.builder(config.getClientCapeType().getText(), button -> {
            config.setClientCapeType(config.getClientCapeType().cycle());
            config.save();
            button.setMessage(config.getClientCapeType().getText());
            PlayerPreview.capeLoaded = false;
        }).pos((width / 2) - (buttonW / 2), 60).size(buttonW, 20).build());

        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> {
            minecraft.setScreen(lastScreen);
        }).pos((width / 2) - (buttonW / 2), 220).size(buttonW, 20).build());

        buttonW = 100;

        addRenderableWidget(Button.builder(Component.translatable("options.capes.selector.player"), button -> {
            PlayerPreview.showBody = !PlayerPreview.showBody;
        }).pos((width / 4) - (buttonW / 2), 145).size(buttonW, 20).build());

        addRenderableWidget(Button.builder(Component.translatable("options.capes.selector.elytra"), button -> {
            PlayerPreview.showElytra = !PlayerPreview.showElytra;
        }).pos((width / 4) - (buttonW / 2), 120).size(buttonW, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        super.render(graphics, mouseX, mouseY, delta);

        int playerX = (width / 2) - 50;
        int playerY = 65;

        long time = System.currentTimeMillis();

        if (time > lastTime + (1000 / 60)) {
            lastTime = time;
            PlayerPreview.prevX = PlayerPreview.x + 0.025;
            PlayerPreview.updateLimbs();
        }

        PlayerPreview.render(graphics, playerX, playerY, playerX + 100, playerY + 300, delta);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double offsetX, double offsetY) {
        super.mouseDragged(mouseX, mouseY, button, offsetX, offsetY);
        PlayerPreview.prevYaw = PlayerPreview.yaw;
        PlayerPreview.yaw -= (float) offsetX;
        return true;
    }

}
