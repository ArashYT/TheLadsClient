package com.thelads.core.v1_21_1.embedded.etf.mixin.mixins.entity.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import com.thelads.core.v1_21_1.embedded.etf.ETF;
import com.thelads.core.v1_21_1.embedded.etf.features.ETFManager;
import com.thelads.core.v1_21_1.embedded.etf.features.player.ETFPlayerEntity;
import com.thelads.core.v1_21_1.embedded.etf.features.player.ETFPlayerFeatureRenderer;
import com.thelads.core.v1_21_1.embedded.etf.features.player.ETFPlayerSkinHolder;
import com.thelads.core.v1_21_1.embedded.etf.features.player.ETFPlayerTexture;
import com.thelads.core.v1_21_1.embedded.etf.features.state.ETFEntityRenderState;
import com.thelads.core.v1_21_1.embedded.etf.features.state.ETFState;
import com.thelads.core.v1_21_1.embedded.etf.features.state.HoldsETFRenderState;

import net.minecraft.client.renderer.entity.player.PlayerRenderer;

@Mixin(PlayerRenderer.class)
public abstract class MixinPlayerEntityRenderer extends LivingEntityRenderer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> implements ETFPlayerSkinHolder {



    @Unique
    private ETFPlayerTexture etf$ETFPlayerTexture = null;

    @SuppressWarnings("unused")
    public MixinPlayerEntityRenderer(EntityRendererProvider.Context ctx,
                                                    PlayerModel<AbstractClientPlayer>
                                                 model, float shadowRadius) {
        super(ctx, model, shadowRadius);
    }

    @Inject(method = "<init>",
            at = @At(value = "TAIL"))
    private void etf$addFeatures(EntityRendererProvider.Context ctx, boolean slim, CallbackInfo ci) {
//        PlayerRenderer self = (PlayerRenderer) ((Object) this);
        this.addLayer(new ETFPlayerFeatureRenderer<>(this));
    }

    /*
     * For some reason cancelling in this way is the only way to get this working
     * */
            @Inject(method = "renderHand",
                at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/PlayerModel;setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V",
                        shift = At.Shift.AFTER), cancellable = true)
        private void etf$redirectNicely(PoseStack matrices, net.minecraft.client.renderer.MultiBufferSource vertexConsumers, int light, AbstractClientPlayer player, ModelPart arm, ModelPart sleeve, CallbackInfo ci) {
        if (ETF.config().getConfig().skinFeaturesEnabled) {
            ETFPlayerTexture thisETFPlayerTexture = ETFManager.getInstance().getPlayerTexture(player,
                        player.getSkin().texture()
            );
            if (thisETFPlayerTexture != null && thisETFPlayerTexture.hasFeatures) {
                ResourceLocation etfTexture = thisETFPlayerTexture.getBaseTextureIdentifierOrNullForVanilla(player);
                if (etfTexture != null) {
                    ETFState.pushRenderLayerModifyState(false);
                    arm.xRot = 0.0F;
                    sleeve.xRot = 0.0F;
    
                    VertexConsumer vc1 = vertexConsumers.getBuffer(RenderType.entityTranslucent(etfTexture));
                    etf$renderOnce(matrices, vc1, light, player,
                            arm, sleeve
                        );
    
                    ETFState.startSpecialRenderOverlayPhase();
                    ResourceLocation emissive = thisETFPlayerTexture.getBaseTextureEmissiveIdentifierOrNullForNone();
                    if (emissive != null) {
                        VertexConsumer vc2 = vertexConsumers.getBuffer(RenderType.entityTranslucent(emissive));
                        etf$renderOnce(matrices, vc2, ETF.EMISSIVE_FEATURE_LIGHT_VALUE, player,
                                    arm, sleeve
                            );
                    }
                    if (thisETFPlayerTexture.baseEnchantIdentifier != null) {
                        VertexConsumer vc3 = ItemRenderer.getArmorFoilBuffer(vertexConsumers,
                                RenderType.armorCutoutNoCull(thisETFPlayerTexture.baseEnchantIdentifier),
                                true);
                        etf$renderOnce(matrices, vc3, light, player,
                                arm, sleeve
                            );
                    }
                    ETFState.endSpecialRenderOverlayPhase();
    
                    ETFState.popRenderLayerModifyState();
                    //don't further render vanilla arms
                    ci.cancel();
                }
            }
        }
    
    }


    @Unique
    private void etf$renderOnce(PoseStack matrixStack, VertexConsumer consumer, int light, AbstractClientPlayer player,
                                ModelPart arm, ModelPart sleeve
    ) {
        arm.render(matrixStack, consumer, light, OverlayTexture.NO_OVERLAY);
        sleeve.render(matrixStack, consumer, light, OverlayTexture.NO_OVERLAY);
    }

    @Inject(method = "getTextureLocation(Lnet/minecraft/client/player/AbstractClientPlayer;)Lnet/minecraft/resources/ResourceLocation;",
            at = @At(value = "RETURN"), cancellable = true)
    private void etf$getTexture(AbstractClientPlayer player, CallbackInfoReturnable<ResourceLocation> cir) {
        var state = ETFEntityRenderState.forEntity((ETFPlayerEntity) player);
        if (ETF.config().getConfig().skinFeaturesEnabled) {
            if (state == null || !state.isPlayer()) return;
            etf$ETFPlayerTexture = ETFManager.getInstance().getPlayerTexture(player, cir.getReturnValue());
            if (etf$ETFPlayerTexture != null && etf$ETFPlayerTexture.hasFeatures) {
                ResourceLocation texture = etf$ETFPlayerTexture.getBaseTextureIdentifierOrNullForVanilla(state);
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