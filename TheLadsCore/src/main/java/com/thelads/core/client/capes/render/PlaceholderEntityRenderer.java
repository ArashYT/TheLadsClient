package com.thelads.core.client.capes.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.CapeLayer;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.client.renderer.entity.layers.WingsLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.item.equipment.Equippable;

public final class PlaceholderEntityRenderer
        extends LivingEntityRenderer<LivingEntity, AvatarRenderState, PlayerModel> {

    private final PlaceholderEntityRenderState placeholderState;

    public PlaceholderEntityRenderer(EntityRendererProvider.Context ctx, boolean slim) {
        super(ctx, new PlayerModel(ctx.bakeLayer(slim
                ? new ModelLayerLocation(Identifier.withDefaultNamespace("player_slim"), "main")
                : ModelLayers.PLAYER), slim), 0.5f);
        this.placeholderState = this.createRenderState();
        this.addLayer(new CapeLayer(this, ctx.getModelSet(), ctx.getEquipmentAssets()));
        this.addLayer(new CustomHeadLayer(this, ctx.getModelSet(), ctx.getPlayerSkinRenderCache()));
        this.addLayer(new WingsLayer(this, ctx.getModelSet(), ctx.getEquipmentRenderer()));
    }

    public PlaceholderEntityRenderState getPlaceholderState() {
        return this.placeholderState;
    }

    @Override
    public void submit(AvatarRenderState state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState camera) {
        for (ModelPart part : this.model.allParts()) {
            part.visible = PlaceholderEntity.INSTANCE.getShowBody();
        }
        super.submit(state, poseStack, submitNodeCollector, camera);
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

    public PlaceholderEntityRenderState getAndUpdatePlaceholderRenderState(PlaceholderEntity entity) {
        PlaceholderEntityRenderState entityRenderState = this.placeholderState;
        this.updateRenderState(entity, entityRenderState);
        return entityRenderState;
    }

    public void updateRenderState(PlaceholderEntity placeholderEntity, AvatarRenderState playerEntityRenderState) {
        playerEntityRenderState.bodyRot = placeholderEntity.getYaw();
        playerEntityRenderState.walkAnimationPos = placeholderEntity.getLimbAngle();
        playerEntityRenderState.walkAnimationSpeed = placeholderEntity.getLimbDistance();

        Options options = Minecraft.getInstance().options;
        playerEntityRenderState.leftArmPose = HumanoidModel.ArmPose.EMPTY;
        playerEntityRenderState.rightArmPose = HumanoidModel.ArmPose.EMPTY;
        playerEntityRenderState.skin = placeholderEntity.getSkinTextures();

        playerEntityRenderState.showHat = options.isModelPartEnabled(PlayerModelPart.HAT);
        playerEntityRenderState.showJacket = options.isModelPartEnabled(PlayerModelPart.JACKET);
        playerEntityRenderState.showLeftPants = options.isModelPartEnabled(PlayerModelPart.LEFT_PANTS_LEG);
        playerEntityRenderState.showRightPants = options.isModelPartEnabled(PlayerModelPart.RIGHT_PANTS_LEG);
        playerEntityRenderState.showLeftSleeve = options.isModelPartEnabled(PlayerModelPart.LEFT_SLEEVE);
        playerEntityRenderState.showRightSleeve = options.isModelPartEnabled(PlayerModelPart.RIGHT_SLEEVE);
        playerEntityRenderState.showCape = true;

        DataComponentMap dataComponentMap = DataComponentMap.builder()
                .set(DataComponents.GLIDER, net.minecraft.util.Unit.INSTANCE)
                .set(DataComponents.EQUIPPABLE, Equippable.builder(EquipmentSlot.CHEST).setAsset(EquipmentAssets.ELYTRA).build())
                .build();
        ItemStack elytra = new ItemStack(new Holder.Direct<>(Items.ELYTRA, dataComponentMap));
        playerEntityRenderState.chestEquipment = placeholderEntity.getShowElytra() ? elytra : ItemStack.EMPTY;

        playerEntityRenderState.elytraRotZ = -0.2617994f;
        playerEntityRenderState.elytraRotX = 0.2617994f;
    }
}
