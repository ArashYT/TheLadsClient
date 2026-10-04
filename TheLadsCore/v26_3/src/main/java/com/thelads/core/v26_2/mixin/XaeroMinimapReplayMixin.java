package com.thelads.core.v26_2.mixin;

import com.thelads.core.v26_2.feature.HudCapture;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * HUD FPS cap (HudCapture): Xaero's minimap renderer draws its map only for a state it has not prepared yet, so a minimap replayed
 * between HUD builds was missing on those frames (the flicker). A replayed minimap now blits the map texture of the last build.
 * Pseudo: skipped without Xaero's Minimap.
 */
@Pseudo
@Mixin(targets = "xaero.hud.minimap.render.MinimapPipRenderer", remap = false)
public abstract class XaeroMinimapReplayMixin extends PictureInPictureRenderer {
    @Inject(method = "prepare", at = @At("HEAD"), cancellable = true, remap = false)
    @SuppressWarnings("unchecked")
    private void lads$replayed(@Coerce PictureInPictureRenderState state, GuiRenderState gui, FeatureRenderDispatcher features, int guiScale, CallbackInfo ci) {
        if (!HudCapture.replayed(state)) return;
        HudCapture.replayBlits++;
        blitTexture(state, gui);
        ci.cancel();
    }
}
