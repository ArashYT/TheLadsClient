package com.thelads.core.v26_2.embedded.etf.mixin.mixins.submit;

import org.spongepowered.asm.mixin.Mixin;

import net.minecraft.client.gui.render.pip.GuiEntityRenderer;
import net.minecraft.client.renderer.state.gui.pip.GuiEntityRenderState;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.thelads.core.v26_2.embedded.etf.ETF;
import com.thelads.core.v26_2.embedded.etf.features.state.ETFState;
import com.thelads.core.v26_2.embedded.etf.features.state.HoldsETFRenderState;
import com.llamalad7.mixinextras.sugar.Local;

@Mixin(net.minecraft.client.gui.render.pip.PictureInPictureRenderer.class)
public class Mixin_GuiEntityRenderer {

    @Inject(method = "prepare",
            at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher;renderAllFeatures(Lcom/mojang/renderpearl/api/commands/RenderPass;Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher$PreparedFrame;)V"
    ))
    private <T extends net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState> void emf$initRender2(final CallbackInfo ci, @Local(argsOnly = true) T guiEntityRenderState) {
        // things get reset by the render dispatcher, re-assert before the actual render
        if (guiEntityRenderState instanceof GuiEntityRenderState gui) {
            assertEmfState(gui);
        }
    }
    
    @Inject(method = "prepare", at = @At(value = "TAIL"))
    private <T extends net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState> void emf$endRender(final CallbackInfo ci, @Local(argsOnly = true) T guiEntityRenderState) {
        if (guiEntityRenderState instanceof GuiEntityRenderState gui) {
            end(gui);
        }
    }

    @Unique
    private static void assertEmfState(final GuiEntityRenderState guiEntityRenderState) {
        var state = guiEntityRenderState.renderState();
        if (state instanceof HoldsETFRenderState holds && holds.etf$getState() != null) {
            var emf = holds.etf$getState();
            ETFState.mount(emf);
        } else {
            ETFState.mountNone();
        }

        var light = state.lightCoords;
        if (light == ETF.EMISSIVE_FEATURE_LIGHT_VALUE || light == ETF.EYES_FEATURE_LIGHT_VALUE) {
            ETFState.startSpecialRenderOverlayPhase();
        }
    }


    @Unique
    private static void end(GuiEntityRenderState guiEntityRenderState) {
        var light = guiEntityRenderState.renderState().lightCoords;
        if (light == ETF.EMISSIVE_FEATURE_LIGHT_VALUE || light == ETF.EYES_FEATURE_LIGHT_VALUE) {
            ETFState.endSpecialRenderOverlayPhase();
        }
        ETFState.unMount();
    }

}