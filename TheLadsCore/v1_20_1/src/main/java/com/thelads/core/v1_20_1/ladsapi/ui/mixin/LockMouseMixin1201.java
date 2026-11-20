package com.thelads.core.v1_20_1.ladsapi.ui.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import com.thelads.core.client.hud.DialogOverlay;
import com.thelads.core.client.ladsapi.minigame.LockState;
import com.thelads.core.client.ladsapi.minigame.Minigame;
import com.thelads.core.v1_20_1.ladsapi.ui.Pad1201;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Lads API: look/scroll lock, mouse clicks on server dialogs, and the "last device was the mouse" stamp. */
@Mixin(MouseHandler.class)
public abstract class LockMouseMixin1201 {
    @Shadow private double accumulatedDX;
    @Shadow private double accumulatedDY;

    @Inject(method = "turnPlayer()V", at = @At("HEAD"), cancellable = true)
    private void lads$lockLook(CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.screen == null && Minigame.locked(LockState.Lock.LOOK)) {
            accumulatedDX = 0; // else the held-back movement would snap the view when the lock ends
            accumulatedDY = 0;
            ci.cancel();
        }
    }

    @Inject(method = "onScroll(JDD)V", at = @At("HEAD"), cancellable = true)
    private void lads$lockScroll(long window, double h, double v, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.screen == null && Minigame.locked(LockState.Lock.HOTBAR)) ci.cancel();
    }

    @Inject(method = "onMove(JDD)V", at = @At("HEAD"))
    private void lads$stampMove(long window, double x, double y, CallbackInfo ci) {
        Pad1201.kbm();
    }

    @Inject(method = "onPress(JIII)V", at = @At("HEAD"), cancellable = true)
    private void lads$dialogClick(long window, int button, int action, int mods, CallbackInfo ci) {
        Pad1201.kbm();
        Minecraft mc = Minecraft.getInstance();
        if (action != InputConstants.PRESS || mc.screen == null || mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen
            || window != mc.getWindow().getWindow() || !DialogOverlay.any()) return;
        MouseHandler self = (MouseHandler) (Object) this;
        double x = self.xpos() * mc.getWindow().getGuiScaledWidth() / mc.getWindow().getScreenWidth();
        double y = self.ypos() * mc.getWindow().getGuiScaledHeight() / mc.getWindow().getScreenHeight();
        if (DialogOverlay.click(x, y)) ci.cancel();
    }
}
