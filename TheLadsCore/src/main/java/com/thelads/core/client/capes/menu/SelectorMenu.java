package com.thelads.core.client.capes.menu;

import com.thelads.core.client.capes.CapeConfig;
import com.thelads.core.client.capes.Capes;
import com.thelads.core.client.capes.render.PlaceholderEntity;
import com.thelads.core.client.capes.render.PlaceholderEntityRenderState;
import com.thelads.core.client.capes.render.PlaceholderEntityRenderer;
import net.minecraft.client.Options;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class SelectorMenu extends MainMenu {
    private long lastTime;

    public SelectorMenu(Screen parent, Options gameOptions) {
        super(parent, gameOptions);
    }

    public long getLastTime() {
        return this.lastTime;
    }

    public void setLastTime(long lastTime) {
        this.lastTime = lastTime;
    }

    @Override
    protected void init() {
        super.init();
        int buttonW = 200;
        CapeConfig config = Capes.INSTANCE.getCONFIG();

        this.addRenderableWidget(Button.builder(config.getClientCapeType().getText(), button -> {
            config.setClientCapeType(config.getClientCapeType().cycle());
            config.save();
            button.setMessage(config.getClientCapeType().getText());
            PlaceholderEntity.INSTANCE.setCapeLoaded(false);
        }).pos(this.width / 2 - buttonW / 2, 60).size(buttonW, 20).build());

        this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> {
            if (this.minecraft != null) {
                this.minecraft.setScreen(this.lastScreen);
            }
        }).pos(this.width / 2 - buttonW / 2, 220).size(buttonW, 20).build());

        buttonW = 100;
        this.addRenderableWidget(Button.builder(Component.translatable("options.capes.selector.player"), button -> {
            PlaceholderEntity.INSTANCE.setShowBody(!PlaceholderEntity.INSTANCE.getShowBody());
        }).pos(this.width / 4 - buttonW / 2, 145).size(buttonW, 20).build());

        this.addRenderableWidget(Button.builder(Component.translatable("options.capes.selector.elytra"), button -> {
            PlaceholderEntity.INSTANCE.setShowElytra(!PlaceholderEntity.INSTANCE.getShowElytra());
        }).pos(this.width / 4 - buttonW / 2, 120).size(buttonW, 20).build());

        // Dummy/hidden button matching CFR output:
        this.addRenderableWidget(Button.builder(Component.literal("DO NOT ASK WHY THIS EXISTS"), button -> {})
            .size(0, 0).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        int playerX = this.width / 2 - 50;
        int playerY = 65;
        long time = System.currentTimeMillis();
        PlaceholderEntity entity = PlaceholderEntity.INSTANCE;
        if (time > this.lastTime + 16) {
            this.lastTime = time;
            entity.setPrevX(entity.getX() + 0.025);
            entity.updateLimbs();
        }
        this.drawEntity(graphics, playerX, playerY, playerX + 100, playerY + 300, PlaceholderEntity.INSTANCE);
    }

    public void drawEntity(GuiGraphicsExtractor context, int x1, int y1, int x2, int y2, PlaceholderEntity entity) {
        context.enableScissor(x1, y1, x2, y2);
        PlaceholderEntityRenderer entityRenderer = PlaceholderEntity.INSTANCE.getRenderer();
        PlaceholderEntityRenderState entityRenderState = entityRenderer.getAndUpdatePlaceholderRenderState(entity);
        context.entity(entityRenderState, 69.0f, new Vector3f(0.0f, 0.0f, 0.0f), new Quaternionf().rotateZ((float) Math.PI), null, x1, y1, x2, y2);
        context.disableScissor();
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent click, double offsetX, double offsetY) {
        super.mouseDragged(click, offsetX, offsetY);
        PlaceholderEntity.INSTANCE.setPrevYaw(PlaceholderEntity.INSTANCE.getYaw());
        PlaceholderEntity.INSTANCE.setYaw(PlaceholderEntity.INSTANCE.getYaw() - (float) offsetX);
        return true;
    }
}
