package com.thelads.core.v1_21_1.mixin;

import com.mojang.blaze3d.platform.Monitor;
import com.mojang.blaze3d.platform.ScreenManager;
import com.mojang.blaze3d.platform.Window;
import com.thelads.core.client.BorderlessWindow;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** BorderlessFullscreen, as on 26.2: fullscreen is an undecorated desktop window (no GLFW monitor) covering the current monitor. */
@Mixin(Window.class)
public abstract class BorderlessWindowMixin {
    @Shadow @Final private long window;
    @Shadow @Final private ScreenManager screenManager;
    @Shadow private boolean fullscreen;
    @Shadow private int windowedX, windowedY, windowedWidth, windowedHeight, x, y, width, height;
    @Shadow protected abstract void refreshFramebufferSize();
    @Unique private boolean ladsBorderless, ladsWasMaximized;
    @Unique private long ladsLastMonitorCheck;

    @Inject(method = "setMode", at = @At("HEAD"), cancellable = true, require = 1)
    private void ladsMode(CallbackInfo ci) {
        if (fullscreen && (ladsBorderless || BorderlessWindow.enabled())) {
            Monitor monitor = screenManager.findBestMonitor((Window) (Object) this);
            if (monitor == null) return;
            if (!ladsBorderless && GLFW.glfwGetWindowMonitor(window) == 0) {
                // From a desktop window: remember it. A window created fullscreen keeps the size vanilla saved at startup.
                ladsWasMaximized = GLFW.glfwGetWindowAttrib(window, GLFW.GLFW_MAXIMIZED) == GLFW.GLFW_TRUE;
                if (ladsWasMaximized) GLFW.glfwRestoreWindow(window);
                int[] px = {0}, py = {0}, pw = {0}, ph = {0};
                GLFW.glfwGetWindowPos(window, px, py);
                GLFW.glfwGetWindowSize(window, pw, ph);
                windowedX = px[0]; windowedY = py[0]; windowedWidth = pw[0]; windowedHeight = ph[0];
            }
            ladsBorderless = true;
            ladsCover(monitor.getMonitor());
            ci.cancel();
        } else if (!fullscreen && ladsBorderless) {
            ladsBorderless = false;
            GLFW.glfwSetWindowAttrib(window, GLFW.GLFW_DECORATED, GLFW.GLFW_TRUE);
            GLFW.glfwSetWindowMonitor(window, 0, windowedX, windowedY, windowedWidth, windowedHeight, GLFW.GLFW_DONT_CARE);
            x = windowedX; y = windowedY; width = windowedWidth; height = windowedHeight;
            if (ladsWasMaximized) GLFW.glfwMaximizeWindow(window);
            refreshFramebufferSize();
            ci.cancel();
        }
    }

    @Unique
    private void ladsCover(long monitor) {
        var mode = GLFW.glfwGetVideoMode(monitor);
        if (mode == null) return;
        int[] mx = {0}, my = {0};
        GLFW.glfwGetMonitorPos(monitor, mx, my);
        GLFW.glfwSetWindowAttrib(window, GLFW.GLFW_DECORATED, GLFW.GLFW_FALSE);
        GLFW.glfwSetWindowMonitor(window, 0, mx[0], my[0], mode.width(), mode.height() + BorderlessWindow.EXTRA_HEIGHT, GLFW.GLFW_DONT_CARE);
        x = mx[0]; y = my[0]; width = mode.width(); height = mode.height() + BorderlessWindow.EXTRA_HEIGHT;
        refreshFramebufferSize();
    }

    /** Follows a resolution change of the monitor while borderless (updateDisplay runs every frame). */
    @Inject(method = "updateDisplay", at = @At("TAIL"), require = 1)
    private void ladsTrackMonitor(CallbackInfo ci) {
        if (!ladsBorderless || !fullscreen || System.nanoTime() - ladsLastMonitorCheck < 500_000_000L) return;
        ladsLastMonitorCheck = System.nanoTime();
        Monitor monitor = screenManager.findBestMonitor((Window) (Object) this);
        if (monitor == null) return;
        var mode = GLFW.glfwGetVideoMode(monitor.getMonitor());
        if (mode == null) return;
        int[] mx = {0}, my = {0};
        GLFW.glfwGetMonitorPos(monitor.getMonitor(), mx, my);
        if (x != mx[0] || y != my[0] || width != mode.width() || height != mode.height() + BorderlessWindow.EXTRA_HEIGHT) ladsCover(monitor.getMonitor());
    }
}
