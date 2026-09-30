package com.thelads.core.v1_21_11.mixin.hud;

import com.thelads.core.v1_21_11.feature.FadedItem;
import com.thelads.core.v1_21_11.feature.NativeAutohide;
import net.minecraft.client.gui.render.state.GuiItemRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiItemRenderState.class)
public class AutohideItemMixin implements FadedItem {
    @Unique private float ladsAlpha = 1;
    @Inject(method = "<init>", at = @At("RETURN"))
    private void ladsCapture(CallbackInfo ci) { ladsAlpha = NativeAutohide.scopeOpacity; }
    public float ladsOpacity() { return ladsAlpha; }
}
