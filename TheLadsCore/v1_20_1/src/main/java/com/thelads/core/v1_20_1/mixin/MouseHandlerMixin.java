package com.thelads.core.v1_20_1.mixin;

import com.thelads.core.client.CpsTracker;
import com.thelads.core.v1_20_1.feature.NativeFeatures1201;
import com.thelads.core.v1_20_1.feature.NativeMenuKey1201;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.player.LocalPlayer;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public class MouseHandlerMixin {
    @Inject(method = "onPress(JIII)V", at = @At("HEAD"), cancellable = true, require = 1)
    private void ladsMouse(long window, int button, int action, int mods, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (window != mc.getWindow().getWindow()) return;
        if (!mc.mouseHandler.isMouseGrabbed() && mc.screen != null && action == GLFW.GLFW_PRESS && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            double mx = mc.mouseHandler.xpos() * (double) mc.getWindow().getGuiScaledWidth() / (double) mc.getWindow().getScreenWidth();
            double my = mc.mouseHandler.ypos() * (double) mc.getWindow().getGuiScaledHeight() / (double) mc.getWindow().getScreenHeight();
            if (com.thelads.core.v1_20_1.social.SocialHudToast1201.onClick(mx, my)) {
                ci.cancel();
                return;
            }
        }
        if (NativeMenuKey1201.mouse(button, action, mods)) {
            ci.cancel();
            return;
        }
        NativeFeatures1201.mouse(button, action);
        if (action != GLFW.GLFW_PRESS || !NativeFeatures1201.interactive()) return;
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) CpsTracker.get().recordLeftClick();
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) CpsTracker.get().recordRightClick();
    }

    @Inject(method = "onScroll(JDD)V", at = @At("HEAD"), cancellable = true, require = 1)
    private void ladsScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        if (window == Minecraft.getInstance().getWindow().getWindow() && NativeFeatures1201.scroll(vertical)) {
            ci.cancel();
        }
    }

    @Redirect(method = "turnPlayer()V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"), require = 1)
    private void ladsZoomTurn(LocalPlayer player, double yaw, double pitch) {
        double scale = NativeFeatures1201.zoomSensitivity();
        if (com.thelads.core.v1_20_1.game.PvpView1201.turn(yaw * scale, pitch * scale)) return; // Freelook turns its camera, not the player
        player.turn(yaw * scale, pitch * scale);
    }
}
