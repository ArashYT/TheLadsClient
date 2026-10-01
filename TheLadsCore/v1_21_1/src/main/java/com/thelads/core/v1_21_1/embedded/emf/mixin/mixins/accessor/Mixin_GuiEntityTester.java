package com.thelads.core.v1_21_1.embedded.emf.mixin.mixins.accessor;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.thelads.core.v1_21_1.embedded.emf.models.animation.state.EMFState;

import org.spongepowered.asm.mixin.gen.Accessor;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.injection.ModifyArg;
@Mixin(GameRenderer.class)
public class Mixin_GuiEntityTester {
    @ModifyArg(method = "render",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/util/profiling/ProfilerFiller;popPush(Ljava/lang/String;)V"))
    private String etf$beforeRenderToTexture(String string) {
        if (string.equals("gui")) EMFState.isInGui = true;
        return string;
    }

    @Inject(method = "render",
            at = @At("TAIL"))
    private void etf$afterRenderToTexture(final CallbackInfo ci) {
        EMFState.isInGui = false;
    }
}
