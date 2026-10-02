// Ported from Capes 1.5.10+1.21.11 by Cael (LGPL-2.1-only) from Kotlin to Java; modified by The Lads: remapped to Mojang mappings and repackaged into Lads Core.
package com.thelads.core.v1_21_11.embedded.capes.menu;

import com.thelads.core.v1_21_11.embedded.capes.CapeConfig;
import com.thelads.core.v1_21_11.embedded.capes.Capes;
import com.thelads.core.v1_21_11.embedded.capes.render.PlaceholderEntity;
import com.thelads.core.v1_21_11.embedded.capes.render.PlaceholderEntityRenderState;
import com.thelads.core.v1_21_11.embedded.capes.render.PlaceholderEntityRenderer;
import net.minecraft.client.Options;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.joml.Quaternionf;
import org.joml.Vector3f;

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
            PlaceholderEntity.capeLoaded = false;
        }).pos((width / 2) - (buttonW / 2), 60).size(buttonW, 20).build());

        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> {
            this.minecraft.setScreen(this.lastScreen);
        }).pos((width / 2) - (buttonW / 2), 220).size(buttonW, 20).build());

        buttonW = 100;

        addRenderableWidget(Button.builder(Component.translatable("options.capes.selector.player"), button -> {
            PlaceholderEntity.showBody = !PlaceholderEntity.showBody;
        }).pos((width / 4) - (buttonW / 2), 145).size(buttonW, 20).build());

        addRenderableWidget(Button.builder(Component.translatable("options.capes.selector.elytra"), button -> {
            PlaceholderEntity.showElytra = !PlaceholderEntity.showElytra;
        }).pos((width / 4) - (buttonW / 2), 120).size(buttonW, 20).build());

        addRenderableWidget(Button.builder(Component.literal("DO NOT ASK WHY THIS EXISTS"), button -> {
        }).size(0, 0).build());

    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        int playerX = (width / 2) - 50;
        int playerY = 65;

        long time = System.currentTimeMillis();

        if (time > lastTime + (1000 / 60)) {
            lastTime = time;
            PlaceholderEntity.prevX = PlaceholderEntity.x + 0.025;
            PlaceholderEntity.updateLimbs();
        }

        drawEntity(context, playerX, playerY, playerX + 100, playerY + 300);
    }

    /** Upstream also passes the PlaceholderEntity object; its state is static here. */
    public void drawEntity(GuiGraphics context, int x1, int y1, int x2, int y2) {
        context.enableScissor(x1, y1, x2, y2);

        PlaceholderEntityRenderer entityRenderer = PlaceholderEntity.getRenderer();
        PlaceholderEntityRenderState entityRenderState = entityRenderer.getAndUpdatePlaceholderRenderState();

        context.submitEntityRenderState(entityRenderState, 69f, new Vector3f(0.0f, 0.0f, 0.0f), new Quaternionf().rotateZ((float) Math.PI), null, x1, y1, x2, y2);
        context.disableScissor();
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent click, double offsetX, double offsetY) {
        super.mouseDragged(click, offsetX, offsetY);
        PlaceholderEntity.prevYaw = PlaceholderEntity.yaw;
        PlaceholderEntity.yaw -= (float) offsetX;
        return true;
    }

}
