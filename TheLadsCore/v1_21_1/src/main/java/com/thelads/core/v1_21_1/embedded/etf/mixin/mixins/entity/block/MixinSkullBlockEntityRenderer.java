package com.thelads.core.v1_21_1.embedded.etf.mixin.mixins.entity.block;

import org.spongepowered.asm.mixin.Mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.SkullModelBase;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.SkullBlockRenderer;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.level.block.entity.SkullBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;
import com.thelads.core.v1_21_1.embedded.etf.ETF;
import com.thelads.core.v1_21_1.embedded.etf.features.ETFManager;
import com.thelads.core.v1_21_1.embedded.etf.features.player.ETFPlayerEntity;
import com.thelads.core.v1_21_1.embedded.etf.features.player.ETFPlayerFeatureRenderer;
import com.thelads.core.v1_21_1.embedded.etf.features.player.ETFPlayerTexture;
import com.thelads.core.v1_21_1.embedded.etf.features.state.ETFState;

@Mixin(SkullBlockRenderer.class)
public abstract class MixinSkullBlockEntityRenderer implements BlockEntityRenderer<SkullBlockEntity> {

    private static final String RENDER_METHOD = "render(Lnet/minecraft/world/level/block/entity/SkullBlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V";

    @Unique
    private ETFPlayerTexture entity_texture_features$thisETFPlayerTexture = null;


    @Inject(method = RENDER_METHOD,
            at = @At(value = "HEAD"))
    private void etf$markNotToChange(CallbackInfo ci) {
        ETFState.pushRenderLayerModifyState(true);
        ETFState.allowTexturePatching = true;
    }

    @Inject(method = RENDER_METHOD,
            at = @At(value = "RETURN"))
    private void etf$markAllowedToChange(CallbackInfo ci) {
        ETFState.popRenderLayerModifyState();
        ETFState.allowTexturePatching = false;
    }

            @Inject(method = RENDER_METHOD,
                    at = @At(value = "INVOKE",
                            target = "Lnet/minecraft/client/renderer/blockentity/SkullBlockRenderer;getRenderType(Lnet/minecraft/world/level/block/SkullBlock$Type;Lnet/minecraft/world/item/component/ResolvableProfile;)Lnet/minecraft/client/renderer/RenderType;"),
                    locals = LocalCapture.CAPTURE_FAILHARD)
            private void etf$alterTexture(final SkullBlockEntity skullBlockEntity, final float f, final PoseStack matrixStack, final MultiBufferSource vertexConsumerProvider, final int i, final int j, final CallbackInfo ci, float g, BlockState blockState, boolean bl, Direction direction, int k, float h, SkullBlock.Type skullType, SkullModelBase skullBlockEntityModel) {

        entity_texture_features$thisETFPlayerTexture = null;

        if (skullType == SkullBlock.Types.PLAYER && ETF.config().getConfig().skinFeaturesEnabled && ETF.config().getConfig().enableCustomTextures && ETF.config().getConfig().enableCustomBlockEntities) {
            if (skullBlockEntity.getOwnerProfile() != null) {
                ResourceLocation identifier =
                            Minecraft.getInstance().getSkinManager().getInsecureSkin(skullBlockEntity.getOwnerProfile().gameProfile()).texture();

                entity_texture_features$thisETFPlayerTexture = ETFManager.getInstance().getPlayerTexture((ETFPlayerEntity) skullBlockEntity, identifier);
                if (entity_texture_features$thisETFPlayerTexture != null) {
                    ETFState.popRenderLayerModifyState();
                    ETFState.pushRenderLayerModifyState(false);
                }
            }
        }
    }

    @Inject(method = RENDER_METHOD,
            at = @At(value = "TAIL"),
            locals = LocalCapture.CAPTURE_FAILHARD)
        private void etf$renderFeatures(SkullBlockEntity skullBlockEntity, float f, PoseStack matrixStack, MultiBufferSource vertexConsumerProvider, int i, int j, CallbackInfo ci, float g, BlockState blockState, boolean bl, Direction direction, int k, float h, SkullBlock.Type skullType, SkullModelBase skullBlockEntityModel, RenderType renderLayer) {
        if (entity_texture_features$thisETFPlayerTexture != null && ETF.config().getConfig().enableEmissiveBlockEntities) {
            //vanilla positional code copy
            matrixStack.pushPose();
            if (direction == null) {
                matrixStack.translate(0.5F, 0.0F, 0.5F);
            } else {
                matrixStack.translate(0.5F - (float) direction.getStepX() * 0.25F, 0.25F, 0.5F - (float) direction.getStepZ() * 0.25F);
            }
            matrixStack.scale(-1.0F, -1.0F, 1.0F);
            skullBlockEntityModel.setupAnim(g, h, 0.0F);
            //vanilla end

            ETFPlayerFeatureRenderer.renderSkullFeatures(matrixStack, vertexConsumerProvider, i, skullBlockEntityModel, entity_texture_features$thisETFPlayerTexture, h);

            matrixStack.popPose();
        }

    }

    @ModifyArg(method = RENDER_METHOD,
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/blockentity/SkullBlockRenderer;renderSkull(Lnet/minecraft/core/Direction;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/model/SkullModelBase;Lnet/minecraft/client/renderer/RenderType;)V")
            , index = 7)
    private RenderType etf$modifyRenderLayer(RenderType renderLayer) {
        if (entity_texture_features$thisETFPlayerTexture != null) {
            ResourceLocation skin = entity_texture_features$thisETFPlayerTexture.getBaseHeadTextureIdentifierOrNullForVanilla();
            if (skin != null) {
                return RenderType.entityTranslucent(skin);
            }
        }
        return renderLayer;
    }


}


