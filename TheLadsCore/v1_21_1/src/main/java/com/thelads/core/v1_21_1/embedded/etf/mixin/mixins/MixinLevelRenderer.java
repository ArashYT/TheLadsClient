package com.thelads.core.v1_21_1.embedded.etf.mixin.mixins;


import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.thelads.core.v1_21_1.embedded.etf.features.state.ETFState;

@Mixin(value = LevelRenderer.class, priority = 1010) // Compat to let sodium 1.21.2-8 mixin apply firsy
public class MixinLevelRenderer {
    @Inject(method = "renderLevel", at = @At(value = "INVOKE", target = "Ljava/util/Iterator;next()Ljava/lang/Object;"), slice =
        @org.spongepowered.asm.mixin.injection.Slice(from = @At(value = "CONSTANT", args = "stringValue=entities"), to = @At(value = "CONSTANT", args = "stringValue=destroyProgress")))
    private void emf$verify(final CallbackInfo ci) {
        // These are the top level, stack should be clean
        ETFState.stackVerifyEmpty();
    }

    @Inject(method = "renderLevel", at = @At(value = "CONSTANT", args = "stringValue=destroyProgress"))
    private void emf$verifyEnd(final CallbackInfo ci) {
        // These are the top level, stack should be clean
        ETFState.stackVerifyEmpty();
    }
}