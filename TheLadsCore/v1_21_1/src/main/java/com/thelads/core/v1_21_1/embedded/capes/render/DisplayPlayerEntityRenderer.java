// Ported from Capes 1.5.4+1.21 by Cael (LGPL-2.1-only) from Kotlin to Java; modified by The Lads: remapped to Mojang mappings and repackaged into Lads Core.
package com.thelads.core.v1_21_1.embedded.capes.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.model.ElytraModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.PlayerModelPart;

public class DisplayPlayerEntityRenderer extends LivingEntityRenderer<LivingEntity, PlayerModel<LivingEntity>> {

    private final EntityRendererProvider.Context ctx;
    private final ElytraModel<LivingEntity> elytra;

    public DisplayPlayerEntityRenderer(EntityRendererProvider.Context ctx, boolean slim) {
        super(
                ctx,
                new PlayerModel<>(
                        ctx.bakeLayer(slim ? ModelLayers.PLAYER_SLIM : ModelLayers.PLAYER),
                        slim
                ),
                0.5f
        );
        this.ctx = ctx;
        this.elytra = new ElytraModel<>(ctx.getModelSet().bakeLayer(ModelLayers.ELYTRA));
    }

    /** Draws the placeholder (upstream passes the PlaceholderEntity object, whose state is static here). */
    public void render(float tickDelta, PoseStack matrixStack, MultiBufferSource vertexConsumerProvider, int light) {
        setModelPose();
        matrixStack.pushPose();

        model.young = false;

        matrixStack.scale(0.9375f, 0.9375f, 0.9375f);
        matrixStack.mulPose(Axis.YP.rotationDegrees(180.0f - PlaceholderEntity.yaw));
        matrixStack.scale(-1.0f, -1.0f, 1.0f);
        matrixStack.translate(0.0f, -1.501f, 0.0f);

        float limbDistance = Mth.lerp(tickDelta, PlaceholderEntity.lastLimbDistance, PlaceholderEntity.limbDistance);
        float limbAngle = PlaceholderEntity.limbAngle - PlaceholderEntity.limbDistance * (1.0f - tickDelta);

        if (limbDistance > 1.0f) {
            limbDistance = 1.0f;
        }

        setAngles(limbAngle, limbDistance);

        if (PlaceholderEntity.showBody) {
            RenderType renderLayer = this.model.renderType(PlaceholderEntity.getSkinTexture());
            VertexConsumer vertexConsumer = vertexConsumerProvider.getBuffer(renderLayer);
            int overlay = OverlayTexture.pack(OverlayTexture.u(0f), OverlayTexture.v(false));
            model.renderToBuffer(matrixStack, vertexConsumer, light, overlay);
        }

        if (!PlaceholderEntity.showElytra) {
            if (PlaceholderEntity.getCapeTexture() == null) return;
            matrixStack.pushPose();
            matrixStack.translate(0.0f, 0.0f, 0.125f);

            matrixStack.mulPose(Axis.XP.rotationDegrees(3.0f));
            matrixStack.mulPose(Axis.YP.rotationDegrees(180.0f));

            VertexConsumer vertexConsumer = vertexConsumerProvider.getBuffer(RenderType.armorCutoutNoCull(PlaceholderEntity.getCapeTexture()));
            ctx.bakeLayer(ModelLayers.PLAYER).getChild("cloak")
                    .render(matrixStack, vertexConsumer, light, OverlayTexture.NO_OVERLAY);
            matrixStack.popPose();
        } else {
            ResourceLocation identifier = PlaceholderEntity.getElytraTexture();
            matrixStack.pushPose();
            matrixStack.translate(0.0f, 0.0f, 0.125f);

            this.model.copyPropertiesTo(this.elytra);

            VertexConsumer vertexConsumer = ItemRenderer.getArmorFoilBuffer(vertexConsumerProvider, RenderType.armorCutoutNoCull(identifier), false);
            this.elytra.renderToBuffer(matrixStack, vertexConsumer, light, OverlayTexture.NO_OVERLAY);
            matrixStack.popPose();
        }


        matrixStack.popPose();
    }

    public void setAngles(float f, float g) {
        model.body.yRot = 0.0f;
        model.rightArm.z = 0.0f;
        model.rightArm.x = -5.0f;
        model.leftArm.z = 0.0f;
        model.leftArm.x = 5.0f;

        model.rightArm.xRot = Mth.cos(f * 0.6662f + 3.1415927f) * 2.0f * g * 0.5f;
        model.leftArm.xRot = Mth.cos(f * 0.6662f) * 2.0f * g * 0.5f;
        model.rightArm.zRot = 0.0f;
        model.leftArm.zRot = 0.0f;
        model.rightLeg.xRot = Mth.cos(f * 0.6662f) * 1.4f * g;
        model.leftLeg.xRot = Mth.cos(f * 0.6662f + 3.1415927f) * 1.4f * g;
        model.rightLeg.yRot = 0.0f;
        model.leftLeg.yRot = 0.0f;
        model.rightLeg.zRot = 0.0f;
        model.leftLeg.zRot = 0.0f;

        model.rightArm.yRot = 0.0f;
        model.leftArm.yRot = 0.0f;

        model.body.xRot = 0.0f;
        model.rightLeg.z = 0.1f;
        model.leftLeg.z = 0.1f;
        model.rightLeg.y = 12.0f;
        model.leftLeg.y = 12.0f;
        model.head.y = 0.0f;
        model.body.y = 0.0f;
        model.leftArm.y = 2.0f;
        model.rightArm.y = 2.0f;

        model.hat.copyFrom(model.head);
        model.leftPants.copyFrom(model.leftLeg);
        model.rightPants.copyFrom(model.rightLeg);
        model.leftSleeve.copyFrom(model.leftArm);
        model.rightSleeve.copyFrom(model.rightArm);
        model.jacket.copyFrom(model.body);
    }

    private void setModelPose() {
        Options options = Minecraft.getInstance().options;
        PlayerModel<LivingEntity> playerEntityModel = this.getModel();
        playerEntityModel.setAllVisible(true);
        playerEntityModel.hat.visible = options.isModelPartEnabled(PlayerModelPart.HAT);
        playerEntityModel.jacket.visible = options.isModelPartEnabled(PlayerModelPart.JACKET);
        playerEntityModel.leftPants.visible = options.isModelPartEnabled(PlayerModelPart.LEFT_PANTS_LEG);
        playerEntityModel.rightPants.visible = options.isModelPartEnabled(PlayerModelPart.RIGHT_PANTS_LEG);
        playerEntityModel.leftSleeve.visible = options.isModelPartEnabled(PlayerModelPart.LEFT_SLEEVE);
        playerEntityModel.rightSleeve.visible = options.isModelPartEnabled(PlayerModelPart.RIGHT_SLEEVE);
    }

    @Override
    public ResourceLocation getTextureLocation(LivingEntity entity) {
        return DefaultPlayerSkin.getDefaultTexture();
    }
}
