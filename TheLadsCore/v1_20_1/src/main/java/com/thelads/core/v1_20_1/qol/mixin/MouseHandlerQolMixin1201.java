package com.thelads.core.v1_20_1.qol.mixin;

import com.thelads.core.client.TooltipScroll;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Scrollable Tooltips: a wheel turn over a tooltip too big for the screen scrolls it; the screen (and Mouse Tweaks) never sees it. */
@Mixin(MouseHandler.class)
abstract class MouseHandlerQolMixin1201 {
    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void lads$scrollTooltip(long window, double horizontal, double vertical, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (window != mc.getWindow().getWindow() || mc.screen == null || mc.getOverlay() != null) return;
        // Some systems turn Shift + wheel into a sideways scroll.
        if (TooltipScroll.scroll(vertical != 0 ? vertical : horizontal, Screen.hasShiftDown() || vertical == 0, System.nanoTime())) ci.cancel();
    }
}
