package com.thelads.core.v1_21_11.embedded.emf.mixin.mixins.rendering;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.thelads.core.v1_21_11.embedded.emf.models.animation.state.EMFEntityRenderState;
import com.thelads.core.v1_21_11.embedded.emf.models.animation.state.EMFState;
import com.thelads.core.v1_21_11.embedded.etf.features.state.ETFState;

import net.minecraft.client.renderer.entity.state.EntityRenderState;

@Mixin(EntityRenderDispatcher.class)
public abstract class MixinEntityRenderDispatcher {

    private static final String RENDER_ETF =
            "submit"
            ;


    @Inject(method = RENDER_ETF, at = @At(value = "RETURN"))
    private <S extends net.minecraft.client.renderer.entity.state.EntityRenderState> void emf$endOfRender(
            final CallbackInfo ci
            , @Local(argsOnly = true) S state
    ) {
        EMFEntityRenderState emfState = EMFEntityRenderState.from(state);
        // todo likely extremely broken in 1.21.9
        if (EMFState.announceModels) {
            EMFState.anounceModels(emfState);
        }
    }

    //region shadow modification

    private static final String SHADOW_RENDER_ETF =
            "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitShadow(Lcom/mojang/blaze3d/vertex/PoseStack;FLjava/util/List;)V"
            ;

    @Inject(method = RENDER_ETF, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/EntityRenderer;submit(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/CameraRenderState;)V",
            shift = At.Shift.AFTER))
    private <S extends net.minecraft.client.renderer.entity.state.EntityRenderState> void
    postSubmitTweaks(CallbackInfo ci, @Local EntityRenderer entityRenderer, @Local(argsOnly = true) S ogState) {

        var state = EMFEntityRenderState.from(ogState);
        if (state == null) return;

        ETFState.stackVerify(state);

        if (state.needsToModifyShadow()) {
            var x = ogState.x;
            var z = ogState.z;
            boolean useShadowX = !Float.isNaN(state.shadowX());
            boolean useShadowZ = !Float.isNaN(state.shadowZ());
            if (useShadowX) ogState.x += state.shadowX();
            if (useShadowZ) ogState.z += state.shadowZ();
            // Size and opacity are set from MixinEntityRenderer

            // Recalculate shadow that has been modified, this now needs to run before the actual shadow call so that its
            // surrounding condition check can pass or fail based on the state values
            //noinspection unchecked
            entityRenderer.extractShadow(ogState, Minecraft.getInstance(), EMFState.state().world());

            if (useShadowX) ogState.x = x;
            if (useShadowZ) ogState.z = z;
        }
    }

    @Inject(method = RENDER_ETF, at = @At(value = "INVOKE", target = SHADOW_RENDER_ETF))
    private <S extends net.minecraft.client.renderer.entity.state.EntityRenderState>
    void preShadow(CallbackInfo ci, @Local PoseStack poseStack, @Local(argsOnly = true) S ogState) {
        var state = EMFEntityRenderState.from(ogState);
        if (state == null || (Float.isNaN(state.shadowX()) && Float.isNaN(state.shadowZ()))) return;

        poseStack.translate(Float.isNaN(state.shadowX()) ? 0 : state.shadowX(), 0, Float.isNaN(state.shadowZ()) ? 0 : state.shadowZ());
    }

    @Inject(method = RENDER_ETF, at = @At(value = "INVOKE", target = SHADOW_RENDER_ETF, shift = At.Shift.AFTER))
    private <S extends net.minecraft.client.renderer.entity.state.EntityRenderState>
    void postShadow(CallbackInfo ci, @Local PoseStack poseStack, @Local(argsOnly = true) S ogState) {
        var state = EMFEntityRenderState.from(ogState);
        if (state == null || (Float.isNaN(state.shadowX()) && Float.isNaN(state.shadowZ()))) return;

        poseStack.translate(Float.isNaN(state.shadowX()) ? 0 : -state.shadowX(), 0, Float.isNaN(state.shadowZ()) ? 0 : -state.shadowZ());
    }

    //endregion

    //region flame modification


    //endregion
}
