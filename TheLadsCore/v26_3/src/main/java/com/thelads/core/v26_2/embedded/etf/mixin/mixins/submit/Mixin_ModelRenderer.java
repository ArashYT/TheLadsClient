package com.thelads.core.v26_2.embedded.etf.mixin.mixins.submit;

import org.spongepowered.asm.mixin.Mixin;

import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.thelads.core.v26_2.embedded.etf.ETF;
import com.thelads.core.v26_2.embedded.etf.features.state.ETFState;
import com.thelads.core.v26_2.embedded.etf.features.state.ETFSubmitData;
import com.thelads.core.v26_2.embedded.etf.features.state.HoldsETFRenderState;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;

@Mixin(ModelFeatureRenderer.class)
public class Mixin_ModelRenderer {

    @Unique
    private <S> void headLogic(
            net.minecraft.client.renderer.feature.ModelFeatureRenderer.Submit<S> modelSubmit
    ) {
        var state = modelSubmit.state();
        ETFSubmitData data = ETFSubmitData.from(modelSubmit);

        // Set up the current entity context for this render
        if (state instanceof HoldsETFRenderState holds && holds.etf$getState() != null) {
            var etf = holds.etf$getState();
            etf.preSubmitActivate(data, modelSubmit);
            ETFState.mount(etf);
        } else if (data != null && data.backupState != null) { // block entity backup
            var etf = data.backupState;
            etf.preSubmitActivate(data, modelSubmit);
            ETFState.mount(etf);
        } else {
            ETFState.mountNone();
        }

        // Handle emissive/eyes lighting setup
        var light = modelSubmit.lightCoords();
        if (light == ETF.EMISSIVE_FEATURE_LIGHT_VALUE || light == ETF.EYES_FEATURE_LIGHT_VALUE) {
            ETFState.startSpecialRenderOverlayPhase();
        }

        if (data != null) {
            ETFSubmitData.DATA_OUT.forEach(entry -> entry.accept(data, modelSubmit));
        }
    }

    @Unique
    private static <S> void tailLogic(
            net.minecraft.client.renderer.feature.ModelFeatureRenderer.Submit<S> modelSubmit
    ) {
        var light = modelSubmit.lightCoords();
        if (light == ETF.EMISSIVE_FEATURE_LIGHT_VALUE || light == ETF.EYES_FEATURE_LIGHT_VALUE) {
            ETFState.endSpecialRenderOverlayPhase();
        }

        ETFState.unMount();
    }

    @Inject(method = "prepareModel", at = @At(value = "HEAD"))
    private <S> void emf$initRender(final CallbackInfo ci, @Local(argsOnly = true) net.minecraft.client.renderer.feature.ModelFeatureRenderer.Submit<S> modelSubmit) {
        headLogic(modelSubmit);
    }
    
    @Inject(method = "prepareModel", at = @At(value = "TAIL"))
    private <S> void emf$endRender(final CallbackInfo ci, @Local(argsOnly = true) net.minecraft.client.renderer.feature.ModelFeatureRenderer.Submit<S> modelSubmit) {
        tailLogic(modelSubmit);
    }

}