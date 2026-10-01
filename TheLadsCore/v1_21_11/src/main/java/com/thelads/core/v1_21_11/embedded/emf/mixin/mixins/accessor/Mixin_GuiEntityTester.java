package com.thelads.core.v1_21_11.embedded.emf.mixin.mixins.accessor;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.thelads.core.v1_21_11.embedded.emf.models.animation.state.EMFState;

import net.minecraft.client.gui.render.GuiRenderer;

@Mixin(GuiRenderer.class)
public class Mixin_GuiEntityTester {
    @Inject(method = "render",
        at = @At("HEAD"))
    private void etf$beforeRenderToTexture(final CallbackInfo ci) {
        EMFState.isInGui = true;
    }

    @Inject(method = "render",
            at = @At("TAIL"))
    private void etf$afterRenderToTexture(final CallbackInfo ci) {
        EMFState.isInGui = false;
    }
}
