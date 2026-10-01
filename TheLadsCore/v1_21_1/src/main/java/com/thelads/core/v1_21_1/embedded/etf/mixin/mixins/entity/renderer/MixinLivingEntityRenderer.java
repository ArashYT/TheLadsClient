package com.thelads.core.v1_21_1.embedded.etf.mixin.mixins.entity.renderer;

import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.thelads.core.v1_21_1.embedded.etf.features.state.ETFEntityRenderState;
import com.thelads.core.v1_21_1.embedded.etf.features.state.ETFState;
import com.thelads.core.v1_21_1.embedded.etf.features.state.HoldsETFRenderState;
import com.thelads.core.v1_21_1.embedded.etf.utils.ETFEntity;

import net.minecraft.client.renderer.entity.RenderLayerParent;
@Mixin(LivingEntityRenderer.class)
public abstract class MixinLivingEntityRenderer<T extends LivingEntity, M extends EntityModel<T>> extends EntityRenderer<T> implements RenderLayerParent<T, M> {


    @SuppressWarnings("unused")
    protected MixinLivingEntityRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);

    }

    private static final String RENDER = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V";

    @Inject(method = RENDER, at = @At(value = "INVOKE", target = "Ljava/util/List;iterator()Ljava/util/Iterator;"))
    private void etf$markFeatures(CallbackInfo ci) {
        ETFState.pushRenderLayerModifyState(true);
        ETFState.isRenderingFeatures = true;
    }

    @Inject(method = RENDER, at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;popPose()V"))
    private void etf$markFeaturesEnd(CallbackInfo ci) {
        ETFState.isRenderingFeatures = false;
        ETFState.popRenderLayerModifyState();
    }

    @Inject(method = RENDER, at = @At(value = "INVOKE", target = "Ljava/util/List;iterator()Ljava/util/Iterator;"))
    private void emf$grabEntity(CallbackInfo ci, @Share("stateCaptureEntity") LocalRef<ETFEntityRenderState> stateCaptureEntity) {
        stateCaptureEntity.set(ETFState.state());
    }

    @Inject(method = RENDER, at = @At(value = "INVOKE", target = "Ljava/util/Iterator;next()Ljava/lang/Object;"))
    private void emf$eachFeatureLoop(CallbackInfo ci, @Share("stateCaptureEntity") LocalRef<ETFEntityRenderState> stateCaptureEntity) {
        // Assert state each call in case things got cancelled and couldn't be undone
        if (stateCaptureEntity.get() != null) {
            ETFState.stackVerify(stateCaptureEntity.get());
        }
    }


    @Inject(method = RENDER, at = @At("TAIL"))
    private void emf$postRender(CallbackInfo ci, @Share("stateCaptureEntity") LocalRef<ETFEntityRenderState> stateCaptureEntity) {
        if (stateCaptureEntity.get() != null) {
            ETFState.stackVerify(stateCaptureEntity.get());
        }
    }
}


