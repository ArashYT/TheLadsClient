package com.thelads.core.v26_2.mixin;

import com.thelads.core.v26_2.feature.HudCapture;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * HUD FPS cap (HudCapture): a picture replayed between HUD builds blits the texture this renderer last drew for it instead of
 * drawing it again, so a capped minimap neither flickers nor re-renders its terrain every frame. Any other picture renders as usual.
 */
@Mixin(PictureInPictureRenderer.class)
public class PictureInPictureReplayMixin {
    @Unique private Object lads$lastRendered;

    @Inject(method = "textureIsReadyToBlit", at = @At("HEAD"), cancellable = true, require = 1)
    private void lads$replayed(PictureInPictureRenderState state, CallbackInfoReturnable<Boolean> cir) {
        if (state == lads$lastRendered && HudCapture.replayed(state)) {
            HudCapture.replayBlits++;
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "prepare", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/gui/render/pip/PictureInPictureRenderer;renderToTexture(Lnet/minecraft/client/renderer/state/gui/pip/PictureInPictureRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;)V"),
        require = 1)
    private void lads$rendered(PictureInPictureRenderState state, net.minecraft.client.renderer.state.gui.GuiRenderState gui,
                               net.minecraft.client.renderer.feature.FeatureRenderDispatcher features, int guiScale, CallbackInfo ci) {
        lads$lastRendered = state;
    }
}
