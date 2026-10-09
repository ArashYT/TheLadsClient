package com.thelads.core.v1_20_1.gui;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.PlayerModelPart;
import org.joml.Quaternionf;

/**
 * A player model with a given skin, drag to turn it (26.3's PlayerSkinWidget): the vanilla player model drawn straight from the
 * skin texture, so it works on screens without a world.
 */
public final class PlayerPreview1201 extends AbstractWidget {
    private final Supplier<ResourceLocation> texture;
    private final BooleanSupplier slim;
    private PlayerModel<AbstractClientPlayer> wide, slender;
    private float yaw = -25f;

    public PlayerPreview1201(int width, int height, Supplier<ResourceLocation> texture, BooleanSupplier slim) {
        super(0, 0, width, height, Component.literal("Player preview"));
        this.texture = texture;
        this.slim = slim;
    }

    private PlayerModel<AbstractClientPlayer> model(boolean slimArms) {
        if (slimArms) {
            if (slender == null) slender = new PlayerModel<>(Minecraft.getInstance().getEntityModels().bakeLayer(ModelLayers.PLAYER_SLIM), true);
            return slender;
        }
        if (wide == null) wide = new PlayerModel<>(Minecraft.getInstance().getEntityModels().bakeLayer(ModelLayers.PLAYER), false);
        return wide;
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float delta) {
        Minecraft mc = Minecraft.getInstance();
        PlayerModel<AbstractClientPlayer> model = model(slim.getAsBoolean());
        ResourceLocation skin = texture.get();
        float scale = Math.min(getHeight() / 2.1f, getWidth() / 1.1f);
        g.flush();
        var pose = g.pose();
        pose.pushPose();
        pose.translate(getX() + getWidth() / 2f, getY() + getHeight() - 4f, 50f);
        pose.scale(scale, scale, -scale);
        pose.mulPose(new Quaternionf().rotateZ((float) Math.PI).rotateY((float) Math.toRadians(yaw)));
        pose.scale(-1f, -1f, 1f);
        pose.translate(0f, -1.501f, 0f);
        pose.scale(0.9375f, 0.9375f, 0.9375f);
        Lighting.setupForEntityInInventory();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        model.young = false;
        model.crouching = false;
        model.head.xRot = 0;
        model.head.yRot = 0;
        var options = mc.options;
        model.hat.visible = options.isModelPartEnabled(PlayerModelPart.HAT);
        model.jacket.visible = options.isModelPartEnabled(PlayerModelPart.JACKET);
        model.leftSleeve.visible = options.isModelPartEnabled(PlayerModelPart.LEFT_SLEEVE);
        model.rightSleeve.visible = options.isModelPartEnabled(PlayerModelPart.RIGHT_SLEEVE);
        model.leftPants.visible = options.isModelPartEnabled(PlayerModelPart.LEFT_PANTS_LEG);
        model.rightPants.visible = options.isModelPartEnabled(PlayerModelPart.RIGHT_PANTS_LEG);
        RenderSystem.runAsFancy(() -> {
            // The vanilla layer order: skin colour model, then its outer layer parts, in one translucent entity pass.
            model.renderToBuffer(pose, buffers.getBuffer(RenderType.entityTranslucent(skin)), 0xF000F0, OverlayTexture.NO_OVERLAY, 1f, 1f, 1f, 1f);
        });
        buffers.endBatch();
        pose.popPose();
        Lighting.setupFor3DItems();
    }

    @Override
    protected void onDrag(double mouseX, double mouseY, double dragX, double dragY) {
        yaw += (float) dragX * 2.5f;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }

    @Override
    public void playDownSound(net.minecraft.client.sounds.SoundManager soundManager) {
    }
}
