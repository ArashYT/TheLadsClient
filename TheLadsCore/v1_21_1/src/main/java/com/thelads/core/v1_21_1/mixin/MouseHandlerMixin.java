package com.thelads.core.v1_21_1.mixin;
import com.thelads.core.client.CpsTracker;
import com.thelads.core.v1_21_1.feature.NativeFeatures;
import com.thelads.core.v1_21_1.feature.NativeMenuKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Menu key, zoom and toggle keys on mouse buttons, and CPS from press events as on 26.x. */
@Mixin(MouseHandler.class)
public class MouseHandlerMixin {
    @Inject(method = "onPress(JIII)V", at = @At("HEAD"), cancellable = true, require = 1)
    private void ladsMouse(long window, int button, int action, int modifiers, CallbackInfo ci) {
        if (window != Minecraft.getInstance().getWindow().getWindow()) return;
        if (NativeMenuKey.mouse(button, action)) { ci.cancel(); return; }
        NativeFeatures.mouse(button, action);
        if (action != GLFW.GLFW_PRESS || !NativeFeatures.interactive()) return;
        // Count physical press events once, including several clicks between rendered frames.
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) CpsTracker.get().recordLeftClick();
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) CpsTracker.get().recordRightClick();
    }
    @Inject(method = "onScroll(JDD)V", at = @At("HEAD"), cancellable = true, require = 1)
    private void ladsScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        if (window == Minecraft.getInstance().getWindow().getWindow() && NativeFeatures.scroll(vertical)) ci.cancel();
    }
}
