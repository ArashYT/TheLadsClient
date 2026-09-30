package com.thelads.core.v1_21_11.mixin.hud;

import com.thelads.core.client.hud.AutohideFade;
import com.thelads.core.v1_21_11.feature.NativeAutohide;
import net.minecraft.client.gui.render.state.GuiTextRenderState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiTextRenderState.class)
public class AutohideTextMixin {
    @Shadow @Final @Mutable public int color;
    @Shadow @Final @Mutable public int backgroundColor;
    @Inject(method = "<init>", at = @At("RETURN"))
    private void ladsTextOpacity(CallbackInfo ci) {
        if (NativeAutohide.scopeOpacity >= 1) return;
        color = AutohideFade.tint(color, NativeAutohide.scopeOpacity);
        backgroundColor = AutohideFade.tint(backgroundColor, NativeAutohide.scopeOpacity);
    }
}
