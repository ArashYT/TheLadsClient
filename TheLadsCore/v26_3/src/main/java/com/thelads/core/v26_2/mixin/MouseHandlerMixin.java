package com.thelads.core.v26_2.mixin;
import com.thelads.core.client.CpsTracker;
import com.thelads.core.v26_2.feature.NativeFeatures;
import com.thelads.core.v26_2.feature.NativeMenuKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import com.mojang.blaze3d.platform.InputConstants;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public class MouseHandlerMixin {
    @Inject(method = "onButton(JLnet/minecraft/client/input/MouseButtonInfo;I)V", at = @At("HEAD"), cancellable = true, require = 1)
    private void ladsMouse(long window, MouseButtonInfo info, int action, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (window != mc.getWindow().handle()) return;
        MouseButtonEvent event = new MouseButtonEvent(mc.mouseHandler.getScaledXPos(mc.getWindow()),
            mc.mouseHandler.getScaledYPos(mc.getWindow()), info);
        if (NativeMenuKey.mouse(event, action)) { ci.cancel(); return; }
        NativeFeatures.mouse(event, action);
        com.thelads.core.v26_2.feature.dynamicfps.feature.state.IdleHandler.onActivity();
        if (com.thelads.core.v26_2.feature.dynamicfps.feature.state.ClickIgnoreHandler.shouldIgnoreClick()) { ci.cancel(); return; }
        if (action != InputConstants.PRESS || !NativeFeatures.interactive()) return;
        // Count physical press events once, including multiple clicks between rendered frames.
        if (info.button() == InputConstants.MOUSE_BUTTON_LEFT) CpsTracker.get().recordLeftClick();
        if (info.button() == InputConstants.MOUSE_BUTTON_RIGHT) CpsTracker.get().recordRightClick();
    }
    @Inject(method = "onScroll(JDD)V", at = @At("HEAD"), cancellable = true, require = 1)
    private void ladsScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        if (window == Minecraft.getInstance().getWindow().handle() && NativeFeatures.scroll(vertical)) ci.cancel();
    }
    @Inject(method = "onMove", at = @At("HEAD"), require = 1)
    private void lads$activity(long window, double x, double y, double dx, double dy, CallbackInfo ci) {
        com.thelads.core.v26_2.feature.dynamicfps.feature.state.IdleHandler.onActivity();
    }
    /** Zoom: the camera turns slower while zoomed (ZoomModule.sensitivity), smooth camera included. */
    @WrapOperation(method = "turnPlayer(D)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"), require = 1)
    private void ladsZoomTurn(LocalPlayer player, double yaw, double pitch, Operation<Void> original) {
        double scale = NativeFeatures.zoomSensitivity();
        original.call(player, yaw * scale, pitch * scale);
    }
}
