package com.thelads.core.v26_2.mixin;

import com.thelads.core.v26_2.feature.HudCapture;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * HUD FPS cap (HudCapture, GuiRendererReplayMixin): every picture renderer remembers the state it last blitted, which its texture
 * shows (vanilla's prepare blits after rendering or when its texture is ready; mods' renderers prepare through it), so a replayed
 * picture blits that texture again instead of being prepared: no flicker, and a capped minimap does not re-render its terrain.
 */
@Mixin(PictureInPictureRenderer.class)
public abstract class PictureInPictureReplayMixin implements HudCapture.ReplayablePicture {
    @Unique private Object lads$shown;

    @Shadow protected abstract void blitTexture(PictureInPictureRenderState state, GuiRenderState gui);

    @Inject(method = "blitTexture", at = @At("HEAD"), require = 1)
    private void lads$shown(PictureInPictureRenderState state, GuiRenderState gui, CallbackInfo ci) {
        lads$shown = state;
    }

    @Override
    public boolean lads$blitAgain(PictureInPictureRenderState state, GuiRenderState gui) {
        if (state != lads$shown) return false;
        blitTexture(state, gui);
        return true;
    }
}
