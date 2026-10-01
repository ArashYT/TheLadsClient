package com.thelads.core.v1_21_1.embedded.etf.features.player;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.*;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;


import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import com.thelads.core.v1_21_1.embedded.etf.ETF;
import com.thelads.core.v1_21_1.embedded.etf.config.ETFConfig;
import com.thelads.core.v1_21_1.embedded.etf.config.screens.skin.ETFConfigScreenSkinTool;
import com.thelads.core.v1_21_1.embedded.etf.features.ETFManager;
import com.thelads.core.v1_21_1.embedded.etf.features.state.ETFState;
import com.thelads.core.v1_21_1.embedded.etf.utils.ETFUtils2;

import net.minecraft.world.entity.player.Player;

public class ETFPlayerFeatureRenderer<T extends Player, M extends PlayerModel<T>> extends RenderLayer<T, M> {
    protected static final ModelPart villagerNose = getModelData(new CubeDeformation(0)).getRoot().getChild("nose").bake(64, 64);
    protected static final ModelPart textureNose = getModelData(new CubeDeformation(0)).getRoot().getChild("textureNose").bake(8, 8);
    protected static final ModelPart jacket = getModelData(new CubeDeformation(0)).getRoot().getChild("jacket").bake(64, 64);
    protected static final ModelPart fatJacket = getModelData(new CubeDeformation(0)).getRoot().getChild("fatJacket").bake(64, 64);
    static private final ResourceLocation VILLAGER_TEXTURE = ETFUtils2.res("textures/entity/villager/villager.png");
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
                                           MultiBufferSource vertexConsumerProvider,
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
                                        MultiBufferSource vertexConsumerProvider,
                                        int light, ETFPlayerTexture playerTexture, Model model) {
        if (playerTexture.hasEnchant && playerTexture.baseEnchantIdentifier != null && playerTexture.etfTextureOfFinalBaseSkin != null) {
            VertexConsumer enchantVert = ItemRenderer.getArmorFoilBuffer(vertexConsumerProvider, RenderType.armorCutoutNoCull(
                    switch (playerTexture.etfTextureOfFinalBaseSkin.currentTextureState) {
                        case BLINK, BLINK_PATCHED, APPLY_BLINK -> playerTexture.baseEnchantBlinkIdentifier;
                        case BLINK2, BLINK2_PATCHED, APPLY_BLINK2 -> playerTexture.baseEnchantBlink2Identifier;
                        default -> playerTexture.baseEnchantIdentifier;
                    }),
                    //#if MC < 12100
                    //$$ false,
                    //#endif
                    true);
           model.renderToBuffer(matrixStack, enchantVert, light, OverlayTexture.NO_OVERLAY
                    //#if MC < 12100
                    //$$ , 1F, 1F, 1F, 1F
                    //#endif
                    );
        }
    }

    private static void renderNose(PoseStack matrixStack,
                                   MultiBufferSource vertexConsumerProvider,
                                   int light, ETFPlayerTexture playerTexture) {
        if (playerTexture.hasVillagerNose) {
//            villagerNose.copyTransform(model.head);
            if (playerTexture.noseType == ETFConfigScreenSkinTool.NoseType.VILLAGER_TEXTURED || playerTexture.noseType == ETFConfigScreenSkinTool.NoseType.VILLAGER_TEXTURED_REMOVE) {

                var type =
                        RenderType
                                .entityTranslucent(playerTexture.etfTextureOfFinalBaseSkin.getTextureIdentifier(null));
                VertexConsumer villagerVert = vertexConsumerProvider.getBuffer(type);
                villagerNose.render(matrixStack, villagerVert, light, OverlayTexture.NO_OVERLAY
                        //#if MC < 12100
                        //$$ , 1F, 1F, 1F, 1F
                        //#endif
                );
                playerTexture.etfTextureOfFinalBaseSkin.renderEmissive(matrixStack, vertexConsumerProvider, villagerNose);
            } else {
                VertexConsumer villagerVert = vertexConsumerProvider.getBuffer(RenderType.entitySolid(VILLAGER_TEXTURE));
                villagerNose.render(matrixStack, villagerVert, light, OverlayTexture.NO_OVERLAY
                        //#if MC < 12100
                        //$$ , 1F, 1F, 1F, 1F
                        //#endif
                );
            }
        } else if (playerTexture.texturedNoseIdentifier != null) {
//            textureNose.copyTransform(model.head);
            VertexConsumer noseVertex = vertexConsumerProvider.getBuffer(RenderType.entityTranslucent(playerTexture.texturedNoseIdentifier));
            textureNose.render(matrixStack, noseVertex, light, OverlayTexture.NO_OVERLAY
                    //#if MC < 12100
                    //$$ , 1F, 1F, 1F, 1F
                    //#endif
            );
            if (playerTexture.texturedNoseIdentifierEmissive != null) {
//                textureNose.copyTransform(model.head);
                VertexConsumer noseVertex_e;
                if (ETFManager.getEmissiveMode() == ETFConfig.EmissiveRenderModes.BRIGHT) {
                    noseVertex_e = vertexConsumerProvider.getBuffer(RenderType.beaconBeam(playerTexture.texturedNoseIdentifierEmissive, true));
                } else {
                    noseVertex_e = vertexConsumerProvider.getBuffer(RenderType.entityTranslucent(playerTexture.texturedNoseIdentifierEmissive));
                }
                textureNose.render(matrixStack, noseVertex_e, ETF.EMISSIVE_FEATURE_LIGHT_VALUE, OverlayTexture.NO_OVERLAY
                        //#if MC < 12100
                        //$$ , 1F, 1F, 1F, 1F
                        //#endif
                );
            }
            if (playerTexture.texturedNoseIdentifierEnchanted != null) {
//                textureNose.copyTransform(model.head);
                VertexConsumer noseVertex_ench = ItemRenderer.getArmorFoilBuffer(vertexConsumerProvider, RenderType.armorCutoutNoCull(playerTexture.texturedNoseIdentifierEnchanted),
                        true);
                textureNose.render(matrixStack, noseVertex_ench, light, OverlayTexture.NO_OVERLAY
                        //#if MC < 12100
                        //$$ , 1F, 1F, 1F, 1F
                        //#endif
                );
            }
        }
    }




        @Override
        public void render(PoseStack matrices, MultiBufferSource submit, int light, T entity, float limbAngle, float limbDistance, float tickDelta, float animationProgress, float headYaw, float headPitch) {
        if (ETF.config().getConfig().skinFeaturesEnabled && skinHolder != null) {
            ETFState.pushRenderLayerModifyState(false);

            ETFPlayerTexture playerTexture = skinHolder.etf$getETFPlayerTexture();
            if (playerTexture != null && playerTexture.hasFeatures) {

                renderFeatures(matrices, submit,
                        light, getParentModel(), playerTexture);
            }

            ETFState.popRenderLayerModifyState();
        }
    }

    public void renderFeatures(PoseStack matrixStack,
                               MultiBufferSource vertexConsumerProvider,
                               int light, M model, ETFPlayerTexture playerTexture) {
        if (playerTexture.canUseFeaturesForThisPlayer()) {
            ETFState.startSpecialRenderOverlayPhase();
            matrixStack.pushPose();
            if (playerTexture.hasVillagerNose || playerTexture.texturedNoseIdentifier != null) {
                matrixStack.pushPose();
                villagerNose.copyFrom(model.head);
                textureNose.copyFrom(model.head);
                renderNose(matrixStack, vertexConsumerProvider, light, playerTexture);
                matrixStack.popPose();
            }
            matrixStack.pushPose();
            renderCoat(matrixStack, vertexConsumerProvider,
                    light, playerTexture, model);
            matrixStack.popPose();

//            ETFPlayerFeatureRenderer.renderEmmisive(matrixStack, vertexConsumerProvider, playerTexture, model);
            //ETFPlayerFeatureRenderer.renderEnchanted(matrixStack, vertexConsumerProvider, light, playerTexture, model);

            matrixStack.popPose();
            ETFState.endSpecialRenderOverlayPhase();
        }
    }

    private void renderCoat(PoseStack matrixStack,
                            MultiBufferSource vertexConsumerProvider,
                            int light, ETFPlayerTexture playerTexture, M model) {
        ItemStack armour = playerTexture.player.etf$getInventory()
                .getArmor(1);
        if (playerTexture.coatIdentifier != null &&
                playerTexture.player.etf$isPartVisible(PlayerModelPart.JACKET) &&
                !(armour.is(ItemTags.LEG_ARMOR)) //todo check all versions for the else, might have just been written before i learned about tags
        ) {
            //String coat = ETFPlayerSkinUtils.SKIN_NAMESPACE + id + "_coat.png";
            if (playerTexture.hasFatCoat) {
                fatJacket.copyFrom(model.jacket);
            } else {
                jacket.copyFrom(model.jacket);
            }
            //perform texture features
            VertexConsumer coatVert = vertexConsumerProvider.getBuffer(RenderType.entityTranslucent(playerTexture.coatIdentifier));
            matrixStack.pushPose();
            if (playerTexture.hasFatCoat) {
                fatJacket.render(matrixStack, coatVert, light, OverlayTexture.NO_OVERLAY
                        //#if MC < 12100
                        //$$ , 1F, 1F, 1F, 1F
                        //#endif
                );
            } else {
                jacket.render(matrixStack, coatVert, light, OverlayTexture.NO_OVERLAY
                        //#if MC < 12100
                        //$$ , 1F, 1F, 1F, 1F
                        //#endif
                );
            }
            if (playerTexture.coatEnchantedIdentifier != null) {
                VertexConsumer enchantVert = ItemRenderer.getArmorFoilBuffer(vertexConsumerProvider, RenderType.armorCutoutNoCull(playerTexture.coatEnchantedIdentifier),
                        //#if MC < 12100
                        //$$ false,
                        //#endif
                        true);
                if (playerTexture.hasFatCoat) {
                    fatJacket.render(matrixStack, enchantVert, light, OverlayTexture.NO_OVERLAY
                            //#if MC < 12100
                            //$$ , 1F, 1F, 1F, 1F
                            //#endif
                    );
                } else {
                    jacket.render(matrixStack, enchantVert, light, OverlayTexture.NO_OVERLAY
                            //#if MC < 12100
                            //$$ , 1F, 1F, 1F, 1F
                            //#endif
                    );
                }
            }
            if (playerTexture.coatEmissiveIdentifier != null) {
                VertexConsumer emissiveVert;// = vertexConsumerProvider.getBuffer(RenderLayer.getBeaconBeam(emissive, true));
                if (ETFManager.getEmissiveMode() == ETFConfig.EmissiveRenderModes.BRIGHT) {
                    emissiveVert = vertexConsumerProvider.getBuffer(RenderType.beaconBeam(playerTexture.coatEmissiveIdentifier, true));
                } else {
                    emissiveVert = vertexConsumerProvider.getBuffer(RenderType.entityTranslucent(playerTexture.coatEmissiveIdentifier));
                }
                if (playerTexture.hasFatCoat) {
                    fatJacket.render(matrixStack, emissiveVert, ETF.EMISSIVE_FEATURE_LIGHT_VALUE, OverlayTexture.NO_OVERLAY
                            //#if MC < 12100
                            //$$ , 1F, 1F, 1F, 1F
                            //#endif
                    );
                } else {
                    jacket.render(matrixStack, emissiveVert, ETF.EMISSIVE_FEATURE_LIGHT_VALUE, OverlayTexture.NO_OVERLAY
                            //#if MC < 12100
                            //$$ , 1F, 1F, 1F, 1F
                            //#endif
                    );
                }
            }
            matrixStack.popPose();
        }
    }

}
