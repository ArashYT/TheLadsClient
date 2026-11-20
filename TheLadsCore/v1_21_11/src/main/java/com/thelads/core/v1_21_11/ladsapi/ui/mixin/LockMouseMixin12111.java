package com.thelads.core.v1_21_11.ladsapi.ui.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import com.thelads.core.client.hud.DialogOverlay;
import com.thelads.core.client.ladsapi.minigame.LockState;
import com.thelads.core.client.ladsapi.minigame.Minigame;
import com.thelads.core.v1_21_11.ladsapi.ui.Pad12111;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Lads API: look/scroll lock, mouse clicks on server dialogs, and the "last device was the mouse" stamp. */
@Mixin(MouseHandler.class)
public abstract class LockMouseMixin12111 {
    @Shadow private double accumulatedDX;
    @Shadow private double accumulatedDY;

    @Inject(method = "turnPlayer(D)V", at = @At("HEAD"), cancellable = true)
    private void lads$lockLook(double dt, CallbackInfo ci) {
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

    @Inject(method = "onMove(JDDDD)V", at = @At("HEAD"))
    private void lads$stampMove(long window, double x, double y, double dx, double dy, CallbackInfo ci) {
        Pad12111.kbm();
    }

    @Inject(method = "onButton(JLnet/minecraft/client/input/MouseButtonInfo;I)V", at = @At("HEAD"), cancellable = true)
    private void lads$dialogClick(long window, MouseButtonInfo info, int action, CallbackInfo ci) {
        Pad12111.kbm();
        Minecraft mc = Minecraft.getInstance();
        if (action != InputConstants.PRESS || mc.screen == null || mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen || window != mc.getWindow().handle() || !DialogOverlay.any()) return;
        MouseHandler self = (MouseHandler) (Object) this;
        if (DialogOverlay.click(self.getScaledXPos(mc.getWindow()), self.getScaledYPos(mc.getWindow()))) ci.cancel();
    }
}
