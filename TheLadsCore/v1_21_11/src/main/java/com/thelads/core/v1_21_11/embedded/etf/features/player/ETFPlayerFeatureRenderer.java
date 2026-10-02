package com.thelads.core.v1_21_11.embedded.etf.features.player;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.*;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;

import net.minecraft.client.model.object.skull.SkullModelBase;
import net.minecraft.client.model.player.PlayerModel;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import com.thelads.core.v1_21_11.embedded.etf.ETF;
import com.thelads.core.v1_21_11.embedded.etf.config.ETFConfig;
import com.thelads.core.v1_21_11.embedded.etf.config.screens.skin.ETFConfigScreenSkinTool;
import com.thelads.core.v1_21_11.embedded.etf.features.ETFManager;
import com.thelads.core.v1_21_11.embedded.etf.features.state.ETFState;
import com.thelads.core.v1_21_11.embedded.etf.utils.ETFUtils2;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;

public class ETFPlayerFeatureRenderer<T extends AvatarRenderState, M extends PlayerModel> extends RenderLayer<T, M> {
    protected static final ModelPart villagerNose = getModelData(new CubeDeformation(0)).getRoot().getChild("nose").bake(64, 64);
    protected static final ModelPart textureNose = getModelData(new CubeDeformation(0)).getRoot().getChild("textureNose").bake(8, 8);
    protected static final ModelPart jacket = getModelData(new CubeDeformation(0)).getRoot().getChild("jacket").bake(64, 64);
    protected static final ModelPart fatJacket = getModelData(new CubeDeformation(0)).getRoot().getChild("fatJacket").bake(64, 64);
    static private final Identifier VILLAGER_TEXTURE = ETFUtils2.res("textures/entity/villager/villager.png");
    protected final ETFPlayerSkinHolder skinHolder;


    //public boolean sneaking;
    public ETFPlayerFeatureRenderer(RenderLayerParent<T, M> context) {
        super(context);
        this.skinHolder = context instanceof ETFPlayerSkinHolder holder ? holder : null;
    }

    public static MeshDefinition getModelData(CubeDeformation dilation) {
        MeshDefinition modelData = new MeshDefinition();
        PartDefinition modelPartData = modelData.getRoot();
        modelPartData.addOrReplaceChild("jacket", CubeListBuilder.create().texOffs(16, 32).addBox(-4.0F, 12.5F, -2.0F, 8.0F, 12.0F, 4.0F, dilation.extend(0.25F)), PartPose.ZERO);
        modelPartData.addOrReplaceChild("fatJacket", CubeListBuilder.create().texOffs(16, 32).addBox(-4.0F, 12.5F, -2.0F, 8.0F, 12.0F, 4.0F, dilation.extend(0.25F).extend(0.5F)), PartPose.ZERO);
        modelPartData.addOrReplaceChild("nose", CubeListBuilder.create().texOffs(24, 0).addBox(-1.0F, -3.0F, -6.0F, 2.0F, 4.0F, 2.0F), PartPose.offset(0.0F, -2.0F, 0.0F));
        modelPartData.addOrReplaceChild("textureNose", CubeListBuilder.create().texOffs(0, 0).addBox(0.0F, -8.0F, -8.0F, 0.0F, 8.0F, 4.0F), PartPose.offset(0.0F, -2.0F, 0.0F));
        return modelData;
    }

    public static void renderSkullFeatures(PoseStack matrixStack,
                                           SubmitNodeCollector vertexConsumerProvider,
                                           int light, SkullModelBase skullModel, ETFPlayerTexture playerTexture, float yaw) {
        ETFState.pushRenderLayerModifyState(false);
        ETFState.startSpecialRenderOverlayPhase();

        if (playerTexture.hasVillagerNose || playerTexture.texturedNoseIdentifier != null) {
            villagerNose.yRot = yaw * 0.017453292F;
            villagerNose.xRot = 0;
            villagerNose.y = 0;
            textureNose.yRot = yaw * 0.017453292F;
            textureNose.xRot = 0;
            textureNose.y = 0;
            renderNose(matrixStack, vertexConsumerProvider, light, playerTexture);
        }
//        ETFPlayerFeatureRenderer.renderEmmisive(matrixStack, vertexConsumerProvider, playerTexture, skullModel);
        ETFPlayerFeatureRenderer.renderEnchanted(matrixStack, vertexConsumerProvider, light, playerTexture, skullModel);

        ETFState.endSpecialRenderOverlayPhase();
        ETFState.popRenderLayerModifyState();
    }

//    private static void renderEmmisive(MatrixStack matrixStack, VertexConsumerProvider vertexConsumerProvider, ETFPlayerTexture playerTexture, Model model) {
//        if (playerTexture.hasEmissives && playerTexture.etfTextureOfFinalBaseSkin != null) {
//            playerTexture.etfTextureOfFinalBaseSkin.renderEmissive(matrixStack, vertexConsumerProvider, model);
//        }
//    }

    private static void renderEnchanted(PoseStack matrixStack,
                                        SubmitNodeCollector submit,
                                        int light, ETFPlayerTexture playerTexture, Model model) {
        if (playerTexture.hasEnchant && playerTexture.baseEnchantIdentifier != null && playerTexture.etfTextureOfFinalBaseSkin != null) {
            ETFUtils2.submitEnchantedModelPart(matrixStack, submit, light, model.root(),playerTexture.baseEnchantIdentifier);
        }
    }

    private static void renderNose(PoseStack matrixStack,
                                   SubmitNodeCollector submit,
                                   int light, ETFPlayerTexture playerTexture) {
        if (playerTexture.hasVillagerNose) {
//            villagerNose.copyTransform(model.head);
            if (playerTexture.noseType == ETFConfigScreenSkinTool.NoseType.VILLAGER_TEXTURED || playerTexture.noseType == ETFConfigScreenSkinTool.NoseType.VILLAGER_TEXTURED_REMOVE) {

                var type =
                        net.minecraft.client.renderer.rendertype.RenderTypes
                                .entityTranslucent(playerTexture.etfTextureOfFinalBaseSkin.getTextureIdentifier(null));
                submit.submitModelPart(villagerNose, matrixStack, type,  light, OverlayTexture.NO_OVERLAY, null);
                var emissive = playerTexture.etfTextureOfFinalBaseSkin.getEmissiveRenderLayer(null);
                if (emissive != null) {
                    submit.submitModelPart(villagerNose, matrixStack, emissive,  ETF.EMISSIVE_FEATURE_LIGHT_VALUE, OverlayTexture.NO_OVERLAY, null);
                }
            } else {
                submit.submitModelPart(villagerNose, matrixStack,
                        net.minecraft.client.renderer.rendertype.RenderTypes
                                .entitySolid(VILLAGER_TEXTURE),  light, OverlayTexture.NO_OVERLAY, null);
            }
        } else if (playerTexture.texturedNoseIdentifier != null) {
//            textureNose.copyTransform(model.head);
            ETFUtils2.submitModelPart(matrixStack, submit, light,
                    textureNose,
                    playerTexture.texturedNoseIdentifier,
                    playerTexture.texturedNoseIdentifierEmissive,
                    playerTexture.texturedNoseIdentifierEnchanted
            );
        }
    }




    @Override
        public void submit(final PoseStack matrices, final SubmitNodeCollector submit, final int light, final T entityRenderState, final float f, final float g) {
        if (ETF.config().getConfig().skinFeaturesEnabled && skinHolder != null) {
            ETFState.pushRenderLayerModifyState(false);

            ETFPlayerTexture playerTexture = skinHolder.etf$getETFPlayerTexture();
            if (playerTexture != null && playerTexture.hasFeatures) {

                renderFeatures(matrices, submit,
                        entityRenderState,
                        light, getParentModel(), playerTexture);
            }

            ETFState.popRenderLayerModifyState();
        }
    }

    public void renderFeatures(PoseStack matrixStack,
                               SubmitNodeCollector vertexConsumerProvider,
                               T entityRenderState,
                               int light, M model, ETFPlayerTexture playerTexture) {
        if (playerTexture.canUseFeaturesForThisPlayer()) {
            ETFState.startSpecialRenderOverlayPhase();
            matrixStack.pushPose();
            getParentModel().root().translateAndRotate(matrixStack);
            if (playerTexture.hasVillagerNose || playerTexture.texturedNoseIdentifier != null) {
                matrixStack.pushPose();
                var head = model.head.getInitialPose();
                villagerNose.loadPose(head);
                textureNose.loadPose(head);
                model.head.translateAndRotate(matrixStack);
                renderNose(matrixStack, vertexConsumerProvider, light, playerTexture);
                matrixStack.popPose();
            }
            matrixStack.pushPose();
            renderCoat(matrixStack, vertexConsumerProvider,
                    entityRenderState,
                    light, playerTexture, model);
            matrixStack.popPose();

//            ETFPlayerFeatureRenderer.renderEmmisive(matrixStack, vertexConsumerProvider, playerTexture, model);
            //ETFPlayerFeatureRenderer.renderEnchanted(matrixStack, vertexConsumerProvider, light, playerTexture, model);

            matrixStack.popPose();
            ETFState.endSpecialRenderOverlayPhase();
        }
    }

    private void renderCoat(PoseStack matrixStack,
                            SubmitNodeCollector submit,
                            T entityRenderState,
                            int light, ETFPlayerTexture playerTexture, M model) {
        ItemStack armour = playerTexture.player.etf$getInventory()
                .getItem(EquipmentSlot.LEGS.getIndex(36));
        if (playerTexture.coatIdentifier != null &&
                entityRenderState.showJacket &&
                !(armour.is(ItemTags.LEG_ARMOR)) //todo check all versions for the else, might have just been written before i learned about tags
        ) {
            //String coat = ETFPlayerSkinUtils.SKIN_NAMESPACE + id + "_coat.png";
            var part = playerTexture.hasFatCoat ? fatJacket : jacket;
            part.loadPose(model.body.getInitialPose());
            model.body.translateAndRotate(matrixStack);

            ETFUtils2.submitModelPart(matrixStack, submit, light,
                    part,
                    playerTexture.coatIdentifier,
                    playerTexture.coatEmissiveIdentifier,
                    playerTexture.coatEnchantedIdentifier
            );
        }
    }

}
