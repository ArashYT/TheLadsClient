package com.thelads.core.v26_2.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.thelads.core.v26_2.feature.HudCapture;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * HUD FPS cap (HudCapture): a picture the replay re-added is not prepared again but blits the texture its renderer last showed it
 * with, whatever the renderer does in prepare: vanilla renders a picture every frame, some mods only once per state (Xaero's
 * minimap: a replayed minimap was missing between HUD builds until 1.7.0). A picture its renderer did not show last is prepared as usual.
 */
@Mixin(GuiRenderer.class)
public class GuiRendererReplayMixin {
    @WrapOperation(method = "preparePictureInPictureState", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/gui/render/pip/PictureInPictureRenderer;prepare(Lnet/minecraft/client/renderer/state/gui/pip/PictureInPictureRenderState;Lnet/minecraft/client/renderer/state/gui/GuiRenderState;Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher;I)V"),
        require = 1)
    private void lads$replay(PictureInPictureRenderer<?> renderer, PictureInPictureRenderState state, GuiRenderState gui,
                             FeatureRenderDispatcher features, int guiScale, Operation<Void> original) {
        if (HudCapture.replayed(state) && ((HudCapture.ReplayablePicture) renderer).lads$blitAgain(state, gui)) HudCapture.replayBlits++;
        else original.call(renderer, state, gui, features, guiScale);
    }
}
