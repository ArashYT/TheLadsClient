// Ported from Capes 1.5.4+1.21 by Cael (LGPL-2.1-only) from Kotlin to Java; modified by The Lads: remapped to Mojang mappings and repackaged into Lads Core.
package com.thelads.core.v1_21_1.embedded.capes.menu;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.thelads.core.v1_21_1.embedded.capes.CapeConfig;
import com.thelads.core.v1_21_1.embedded.capes.Capes;
import com.thelads.core.v1_21_1.embedded.capes.render.DisplayPlayerEntityRenderer;
import com.thelads.core.v1_21_1.embedded.capes.render.PlaceholderEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.joml.Matrix4fStack;
import org.joml.Quaternionf;

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

        addRenderableWidget(Button.builder(Component.translatable("options.capes.selector.elytra"), button -> {
            PlaceholderEntity.showElytra = !PlaceholderEntity.showElytra;
        }).pos((width / 4) - (buttonW / 2), 120).size(buttonW, 20).build());

        addRenderableWidget(Button.builder(Component.translatable("options.capes.selector.player"), button -> {
            PlaceholderEntity.showBody = !PlaceholderEntity.showBody;
        }).pos((width / 4) - (buttonW / 2), 145).size(buttonW, 20).build());

    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        int playerX = width / 2;
        int playerY = 215;

        long time = System.currentTimeMillis();

        if (time > lastTime + (1000 / 60)) {
            lastTime = time;
            PlaceholderEntity.prevX = PlaceholderEntity.x + 0.025;
            PlaceholderEntity.updateLimbs();
        }
        drawPlayer(playerX, playerY, 70);
    }


    public void drawPlayer(int x, int y, int size) {
        Matrix4fStack matrixStack = RenderSystem.getModelViewStack();
        matrixStack.pushMatrix();
        matrixStack.translate((float) x, (float) y, 1050.0f);
        matrixStack.scale(1.0f, 1.0f, -1.0f);
        RenderSystem.applyModelViewMatrix();
        PoseStack matrixStack2 = new PoseStack();
        matrixStack2.translate(0.0, 0.0, 1000.0);
        matrixStack2.scale((float) size, (float) size, (float) size);

        Quaternionf quaternion = Axis.ZP.rotationDegrees(180.0f);
        matrixStack2.mulPose(quaternion);

        Lighting.setupForEntityInInventory();
        EntityRenderDispatcher entityRenderDispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        entityRenderDispatcher.setRenderShadow(false);
        MultiBufferSource.BufferSource immediate = Minecraft.getInstance().renderBuffers().bufferSource();
        RenderSystem.runAsFancy(() -> {
            EntityRendererProvider.Context ctx = new EntityRendererProvider.Context(
                    Minecraft.getInstance().getEntityRenderDispatcher(),
                    Minecraft.getInstance().getItemRenderer(),
                    Minecraft.getInstance().getBlockRenderer(),
                    Minecraft.getInstance().getEntityRenderDispatcher().getItemInHandRenderer(),
                    Minecraft.getInstance().getResourceManager(),
                    Minecraft.getInstance().getEntityModels(),
                    Minecraft.getInstance().font
            );
            DisplayPlayerEntityRenderer displayPlayerEntityRenderer = new DisplayPlayerEntityRenderer(ctx, PlaceholderEntity.slim);
            displayPlayerEntityRenderer.render(1.0f, matrixStack2, immediate, 0xF000F0);
        });
        immediate.endBatch();
        entityRenderDispatcher.setRenderShadow(true);
        matrixStack.popMatrix();
        RenderSystem.applyModelViewMatrix();
        Lighting.setupFor3DItems();
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
        PlaceholderEntity.prevYaw = PlaceholderEntity.yaw;
        PlaceholderEntity.yaw -= (float) deltaX;
        return true;
    }

}
