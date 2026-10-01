package com.thelads.core.v1_21_11.embedded.etf.mixin.mixins.entity.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import com.thelads.core.v1_21_11.embedded.etf.ETF;
import com.thelads.core.v1_21_11.embedded.etf.features.ETFManager;
import com.thelads.core.v1_21_11.embedded.etf.features.player.ETFPlayerEntity;
import com.thelads.core.v1_21_11.embedded.etf.features.player.ETFPlayerFeatureRenderer;
import com.thelads.core.v1_21_11.embedded.etf.features.player.ETFPlayerSkinHolder;
import com.thelads.core.v1_21_11.embedded.etf.features.player.ETFPlayerTexture;
import com.thelads.core.v1_21_11.embedded.etf.features.state.ETFEntityRenderState;
import com.thelads.core.v1_21_11.embedded.etf.features.state.ETFState;
import com.thelads.core.v1_21_11.embedded.etf.features.state.HoldsETFRenderState;


import net.minecraft.client.entity.ClientAvatarEntity;
import net.minecraft.world.entity.Avatar;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import com.thelads.core.v1_21_11.embedded.etf.utils.ETFUtils2;

@Mixin(AvatarRenderer.class)
public abstract class MixinPlayerEntityRenderer<AvatarlikeEntity extends Avatar & ClientAvatarEntity> extends LivingEntityRenderer<AvatarlikeEntity, AvatarRenderState, PlayerModel> implements ETFPlayerSkinHolder {





    @Unique
    private ETFPlayerTexture etf$ETFPlayerTexture = null;

    @SuppressWarnings("unused")
    public MixinPlayerEntityRenderer(EntityRendererProvider.Context ctx,
                                                 PlayerModel
                                                 model, float shadowRadius) {
        super(ctx, model, shadowRadius);
    }

    @Inject(method = "<init>",
            at = @At(value = "TAIL"))
    private void etf$addFeatures(EntityRendererProvider.Context ctx, boolean slim, CallbackInfo ci) {
//        PlayerRenderer self = (PlayerRenderer) ((Object) this);
        this.addLayer(new ETFPlayerFeatureRenderer<>(this));
    }

    @Inject(method = "renderHand",
            at = @At(value = "TAIL"))
    private void etf$renderHandFeatures(final PoseStack poseStack, final SubmitNodeCollector submitNodeCollector, final int i, final Identifier resourceLocation, final ModelPart modelPart, final boolean bl, final CallbackInfo ci) {
        for (var skin : ETFManager.getInstance().PLAYER_TEXTURE_MAP.values()) { // todo streamline this: player texture rework
            if (skin.getOriginal().equals(resourceLocation)) {
                var emissive = skin.getBaseTextureEmissiveIdentifierOrNullForNone();
                if (emissive != null) {
                    ETFUtils2.submitEmissiveModelPart(poseStack, submitNodeCollector, modelPart, emissive);
                }
                var enchant = skin.baseEnchantIdentifier;
                if (enchant != null) {
                    ETFUtils2.submitEnchantedModelPart(poseStack, submitNodeCollector, i, modelPart, enchant);
                }
                break;
            }
        }
    }


    @Unique
    private void etf$renderOnce(PoseStack matrixStack, VertexConsumer consumer, int light, AbstractClientPlayer player,
                                ModelPart armAndSleeve
    ) {
        armAndSleeve.render(matrixStack, consumer, light, OverlayTexture.NO_OVERLAY);
    }

    @Inject(method = "getTextureLocation(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)Lnet/minecraft/resources/Identifier;", at = @At(value = "RETURN"), cancellable = true)
    private void etf$getTexture(final AvatarRenderState playerRenderState, final CallbackInfoReturnable<Identifier> cir) {
        var state = ((HoldsETFRenderState) playerRenderState).etf$getState();
        if (ETF.config().getConfig().skinFeaturesEnabled) {
            if (state == null || !state.isPlayer()) return;
            var player = (ETFPlayerEntity) state.entity();
            etf$ETFPlayerTexture = ETFManager.getInstance().getPlayerTexture(player, cir.getReturnValue());
            if (etf$ETFPlayerTexture != null && etf$ETFPlayerTexture.hasFeatures) {
                Identifier texture = etf$ETFPlayerTexture.getBaseTextureIdentifierOrNullForVanilla(state);
                if (texture != null) {
                    cir.setReturnValue(texture);
                }
            }
        } else {
            etf$ETFPlayerTexture = null;
        }
    }

    @Override
    public @Nullable ETFPlayerTexture etf$getETFPlayerTexture() {
        return etf$ETFPlayerTexture;
    }
}