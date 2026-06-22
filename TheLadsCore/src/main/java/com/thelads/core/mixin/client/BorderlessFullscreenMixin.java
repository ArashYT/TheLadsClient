package com.thelads.core.mixin.client;

import com.mojang.blaze3d.platform.Window;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Window.class)
public abstract class BorderlessFullscreenMixin {

    @Redirect(method = "setMode", at = @At(value = "INVOKE", target = "Lorg/lwjgl/glfw/GLFW;glfwSetWindowMonitor(JIIIII)V"), require = 0)
    private void redirectSetMonitor(long window, long monitor, int xpos, int ypos, int width, int height, int refreshRate) {
        if (monitor != 0L) {
            // True Borderless Fullscreen
            GLFW.glfwSetWindowAttrib(window, GLFW.GLFW_DECORATED, GLFW.GLFW_FALSE);
            GLFW.glfwSetWindowAttrib(window, GLFW.GLFW_AUTO_ICONIFY, GLFW.GLFW_FALSE);
            
            // Get monitor position to place the window correctly (e.g. for multi-monitor setups)
            int[] mx = new int[1];
            int[] my = new int[1];
            GLFW.glfwGetMonitorPos(monitor, mx, my);
            
            GLFW.glfwSetWindowMonitor(window, 0L, mx[0], my[0], width, height, GLFW.GLFW_DONT_CARE);
        } else {
            // Normal Windowed
            GLFW.glfwSetWindowAttrib(window, GLFW.GLFW_DECORATED, GLFW.GLFW_TRUE);
            GLFW.glfwSetWindowMonitor(window, 0L, xpos, ypos, width, height, GLFW.GLFW_DONT_CARE);
        }
    }
}
