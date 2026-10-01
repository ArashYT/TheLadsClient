// Ported from Capes 1.5.10+1.21.11 by Cael (LGPL-2.1-only) from Kotlin to Java; modified by The Lads: remapped to Mojang mappings and repackaged into Lads Core.
package com.thelads.core.v1_21_11.embedded.capes.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.CapeLayer;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.client.renderer.entity.layers.WingsLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class PlaceholderEntityRenderer extends LivingEntityRenderer<LivingEntity, AvatarRenderState, PlayerModel> {

    private final PlaceholderEntityRenderState placeholderState = createRenderState();

    public PlaceholderEntityRenderer(EntityRendererProvider.Context ctx, boolean slim) {
        super(
                ctx,
                new PlayerModel(ctx.bakeLayer(
                        // I'm supposed to use EntityModelLayers.PLAYER_SLIM here, but it seems there is some mapping conflict that doesn't let me use it rn
                        slim ? new ModelLayerLocation(Identifier.withDefaultNamespace("player_slim"), "main")
                                : ModelLayers.PLAYER), slim),
                0.5f
        );
        this.addLayer(new CapeLayer(this, ctx.getModelSet(), ctx.getEquipmentAssets()));
        this.addLayer(new CustomHeadLayer<>(this, ctx.getModelSet(), ctx.getPlayerSkinRenderCache()));
        this.addLayer(
                new WingsLayer<>(
                        this,
                        ctx.getModelSet(),
                        ctx.getEquipmentRenderer()
                )
        );
    }

    @Override
    public void submit(AvatarRenderState livingEntityRenderState, PoseStack matrixStack, SubmitNodeCollector orderedRenderCommandQueue, CameraRenderState cameraRenderState) {
        this.model.allParts().forEach(part -> part.visible = PlaceholderEntity.showBody);
        super.submit(livingEntityRenderState, matrixStack, orderedRenderCommandQueue, cameraRenderState);
    }

    @Override
    public Identifier getTextureLocation(AvatarRenderState playerEntityRenderState) {
        return playerEntityRenderState.skin.body().texturePath();
    }

    @Override
    protected void scale(AvatarRenderState playerEntityRenderState, PoseStack matrixStack) {
        matrixStack.scale(0.9375f, 0.9375f, 0.9375f);
    }

    @Override
    public PlaceholderEntityRenderState createRenderState() {
        return new PlaceholderEntityRenderState();
    }

    public PlaceholderEntityRenderState getAndUpdatePlaceholderRenderState() {
        PlaceholderEntityRenderState entityRenderState = placeholderState;
        updateRenderState(entityRenderState);
        return entityRenderState;
    }

    /** Upstream also takes the PlaceholderEntity object; its state is static here. */
    public void updateRenderState(AvatarRenderState playerEntityRenderState) {
        playerEntityRenderState.bodyRot = PlaceholderEntity.yaw;

        playerEntityRenderState.walkAnimationPos = PlaceholderEntity.limbAngle;
        playerEntityRenderState.walkAnimationSpeed = PlaceholderEntity.limbDistance;

        Options options = Minecraft.getInstance().options;
        playerEntityRenderState.leftArmPose = HumanoidModel.ArmPose.EMPTY;
        playerEntityRenderState.rightArmPose = HumanoidModel.ArmPose.EMPTY;
        playerEntityRenderState.skin = PlaceholderEntity.getSkinTextures();
        playerEntityRenderState.showHat = options.isModelPartEnabled(PlayerModelPart.HAT);
        playerEntityRenderState.showJacket = options.isModelPartEnabled(PlayerModelPart.JACKET);
        playerEntityRenderState.showLeftPants = options.isModelPartEnabled(PlayerModelPart.LEFT_PANTS_LEG);
        playerEntityRenderState.showRightPants = options.isModelPartEnabled(PlayerModelPart.RIGHT_PANTS_LEG);
        playerEntityRenderState.showLeftSleeve = options.isModelPartEnabled(PlayerModelPart.LEFT_SLEEVE);
        playerEntityRenderState.showRightSleeve = options.isModelPartEnabled(PlayerModelPart.RIGHT_SLEEVE);
        playerEntityRenderState.showCape = true;

        playerEntityRenderState.chestEquipment = PlaceholderEntity.showElytra ? new ItemStack(Items.ELYTRA) : ItemStack.EMPTY;
        playerEntityRenderState.elytraRotZ = -(float) (Math.PI / 12);
        playerEntityRenderState.elytraRotX = (float) (Math.PI / 12);

//        playerEntityRenderState.name = placeholderEntity.gameProfile.name
    }
}
