package com.thelads.core.v1_21_11.embedded.etf.mixin.mixins;


import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.thelads.core.v1_21_11.embedded.etf.features.state.ETFState;

@Mixin(GameRenderer.class)
public class MixinGameRenderer {

    @Inject(method = "render",
            at = @At(value = "HEAD"))
    private void emf$injectCounter(final CallbackInfo ci) {
        ETFState.stackVerifyEmpty();
    }
}