package com.thelads.core.v26_2.qol.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import com.thelads.core.client.KeepCursor;
import com.thelads.core.client.TooltipScroll;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Scrollable Tooltips (a wheel turn over a tooltip too big for the screen scrolls it; the screen never sees it) and Keep Cursor
 * Position (releaseMouse, as a container screen opens right after one closed, puts the pointer back instead of in the middle).
 */
@Mixin(MouseHandler.class)
abstract class MouseHandlerQolMixin26 {
    @Shadow private double xpos;
    @Shadow private double ypos;

    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void lads$scrollTooltip(long window, double horizontal, double vertical, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (window != mc.getWindow().handle() || mc.gui.screen() == null || mc.gui.overlay() != null) return;
        // Some systems turn Shift + wheel into a sideways scroll.
        if (TooltipScroll.scroll(vertical != 0 ? vertical : horizontal, mc.hasShiftDown() || vertical == 0, System.nanoTime())) ci.cancel();
    }

    @WrapOperation(method = "releaseMouse", at = @At(value = "INVOKE",
        target = "Lcom/mojang/blaze3d/platform/InputConstants;releaseMouse(Lcom/mojang/blaze3d/platform/Window;DD)V"))
    private void lads$keepCursor(Window window, double x, double y, Operation<Void> original) {
        if (Minecraft.getInstance().gui.screen() instanceof AbstractContainerScreen<?> && KeepCursor.reopened(System.nanoTime())) {
            xpos = x = KeepCursor.x;
            ypos = y = KeepCursor.y;
        }
        original.call(window, x, y);
    }
}
