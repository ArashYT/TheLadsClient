package com.thelads.core.v1_20_1.perf.borderless.mixin;

import com.mojang.blaze3d.platform.Monitor;
import com.mojang.blaze3d.platform.ScreenManager;
import com.mojang.blaze3d.platform.Window;
import com.thelads.core.client.BorderlessWindow;
import com.thelads.core.v1_20_1.perf.borderless.BorderlessAccess1201;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWVidMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * BorderlessFullscreen on 1.20.1: "fullscreen" becomes an undecorated ordinary desktop window that covers the monitor the
 * game is on, one row taller than the display so Windows never promotes it to exclusive/independent flip (which OBS window
 * capture and Discord screen share cannot see). Vanilla's {@code setMode} is where fullscreen is entered and left, so the
 * module takes that over; {@code BorderlessController1201} makes it run again when the module is switched. A game started
 * fullscreen (the launcher's "Fullscreen on launch" passes --fullscreen) is created as a desktop window, never on the monitor,
 * so it never switches the display mode on its way to borderless, as on 26.3.
 */
@Mixin(Window.class)
public abstract class BorderlessWindowMixin implements BorderlessAccess1201 {
    @Unique private static final Logger LADS_LOGGER = LoggerFactory.getLogger("TheLadsCore-Borderless");

    @Shadow @Final private long window;
    @Shadow @Final private ScreenManager screenManager;
    @Shadow private boolean fullscreen;
    @Shadow private boolean actuallyFullscreen;
    @Shadow private int windowedX, windowedY, windowedWidth, windowedHeight, x, y, width, height;
    @Shadow protected abstract void refreshFramebufferSize();

    @Unique private boolean ladsBorderless, ladsWasMaximized, ladsCreatedForBorderless;
    @Unique private long ladsLastMonitorCheck;

    @Override
    public boolean lads$borderlessActive() {
        return ladsBorderless;
    }

    @Override
    public void lads$reapplyFullscreenMode() {
        // updateDisplay runs the mode switch when these two disagree.
        if (fullscreen) actuallyFullscreen = false;
    }

    /**
     * Vanilla creates a fullscreen window on the monitor (exclusive, at the video mode nearest the windowed size, e.g. 800x600)
     * and only then lets setMode run. With the module on it starts as a desktop window instead and setMode covers the monitor.
     * Forge (Zero SMP) creates its window in FML's early display, which this does not reach (require 0); that window is a desktop one.
     */
    @ModifyArg(method = "<init>", at = @At(value = "INVOKE", target = "Lorg/lwjgl/glfw/GLFW;glfwCreateWindow(IILjava/lang/CharSequence;JJ)J", remap = false),
        index = 3, require = 0)
    private long ladsDesktopWindow(long monitor) {
        if (monitor == 0 || !BorderlessWindow.enabled()) return monitor;
        ladsCreatedForBorderless = true;
        return 0L;
    }

    @Inject(method = "setMode", at = @At("HEAD"), cancellable = true, require = 1)
    private void ladsMode(CallbackInfo ci) {
        if (fullscreen && BorderlessWindow.enabled()) {
            Monitor monitor = screenManager.findBestMonitor((Window) (Object) this);
            if (monitor == null) return; // vanilla reports it and leaves fullscreen
            if (!ladsBorderless) ladsRememberWindow();
            ladsBorderless = true;
            ladsCover(monitor.getMonitor());
            ci.cancel();
        } else if (ladsBorderless) {
            ladsBorderless = false;
            GLFW.glfwSetWindowAttrib(window, GLFW.GLFW_DECORATED, GLFW.GLFW_TRUE);
            GLFW.glfwSetWindowMonitor(window, 0, windowedX, windowedY, windowedWidth, windowedHeight, GLFW.GLFW_DONT_CARE);
            x = windowedX; y = windowedY; width = windowedWidth; height = windowedHeight;
            if (ladsWasMaximized) GLFW.glfwMaximizeWindow(window);
            LADS_LOGGER.info("BorderlessFullscreen: left the borderless window ({}x{} at {},{})", width, height, x, y);
            if (!fullscreen) {
                refreshFramebufferSize();
                ci.cancel();
            }
            // Fullscreen with the module switched off: vanilla's exclusive mode continues from the restored window.
        }
    }

    /** The windowed size and position to return to. When the game started in vanilla fullscreen the saved ones stand. */
    @Unique
    private void ladsRememberWindow() {
        if (ladsCreatedForBorderless) { // the constructor already set vanilla's centred windowed place
            ladsCreatedForBorderless = false;
            return;
        }
        if (GLFW.glfwGetWindowMonitor(window) != 0) return;
        ladsWasMaximized = GLFW.glfwGetWindowAttrib(window, GLFW.GLFW_MAXIMIZED) == GLFW.GLFW_TRUE;
        if (ladsWasMaximized) GLFW.glfwRestoreWindow(window);
        int[] px = {0}, py = {0}, pw = {0}, ph = {0};
        GLFW.glfwGetWindowPos(window, px, py);
        GLFW.glfwGetWindowSize(window, pw, ph);
        windowedX = px[0]; windowedY = py[0]; windowedWidth = pw[0]; windowedHeight = ph[0];
    }

    @Unique
    private void ladsCover(long monitor) {
        // Out of exclusive fullscreen first: until then the monitor runs the mode exclusive fullscreen picked, not its desktop mode.
        if (GLFW.glfwGetWindowMonitor(window) != 0) GLFW.glfwSetWindowMonitor(window, 0, x, y, width, height, GLFW.GLFW_DONT_CARE);
        GLFWVidMode mode = GLFW.glfwGetVideoMode(monitor);
        if (mode == null) return;
        int[] mx = {0}, my = {0};
        GLFW.glfwGetMonitorPos(monitor, mx, my);
        int coverHeight = mode.height() + BorderlessWindow.EXTRA_HEIGHT;
        GLFW.glfwSetWindowAttrib(window, GLFW.GLFW_DECORATED, GLFW.GLFW_FALSE);
        GLFW.glfwSetWindowMonitor(window, 0, mx[0], my[0], mode.width(), coverHeight, GLFW.GLFW_DONT_CARE);
        x = mx[0]; y = my[0]; width = mode.width(); height = coverHeight;
        refreshFramebufferSize();
        LADS_LOGGER.info("BorderlessFullscreen: window now {}x{} at {},{} (monitor {}x{}, +{} row)", width, height, x, y, mode.width(), mode.height(), BorderlessWindow.EXTRA_HEIGHT);
    }

    /** Follows the window to another monitor (it is dragged there with a shortcut, or the display layout changes). */
    @Inject(method = "updateDisplay", at = @At("TAIL"), require = 1)
    private void ladsTrackMonitor(CallbackInfo ci) {
        if (!ladsBorderless || !fullscreen || System.nanoTime() - ladsLastMonitorCheck < 500_000_000L) return;
        ladsLastMonitorCheck = System.nanoTime();
        Monitor monitor = screenManager.findBestMonitor((Window) (Object) this);
        if (monitor == null) return;
        GLFWVidMode mode = GLFW.glfwGetVideoMode(monitor.getMonitor());
        if (mode == null) return;
        int[] mx = {0}, my = {0};
        GLFW.glfwGetMonitorPos(monitor.getMonitor(), mx, my);
        if (x != mx[0] || y != my[0] || width != mode.width() || height != mode.height() + BorderlessWindow.EXTRA_HEIGHT) {
            ladsCover(monitor.getMonitor());
        }
    }
}
