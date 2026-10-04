package com.thelads.core.v26_2.mixin;

import com.thelads.core.v26_2.feature.NativeQualityOfLife;
import net.minecraft.client.renderer.state.gui.GuiTextRenderState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The Chat module's Text Shadow (on by default, as vanilla): off, chat lines are drawn without their shadow. */
@Mixin(GuiTextRenderState.class)
public class ChatTextShadowMixin {
    @Shadow @Final @Mutable private boolean dropShadow;

    @Inject(method = "<init>", at = @At("RETURN"), require = 1)
    private void lads$chatShadow(CallbackInfo ci) {
        if (NativeQualityOfLife.chatWithoutShadow) dropShadow = false;
    }
}
