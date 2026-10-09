package com.thelads.core.v1_20_1.feature;

import com.thelads.core.config.DropdownOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.SliderOption;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

public final class NativeDynamicFps1201 {
    private static boolean backgrounded;
    private static long backgroundSince;

    private NativeDynamicFps1201() {}

    public static int limit(int vanillaLimit) {
        Module module = ModuleManager.getInstance().getModule("DynamicFPS");
        if (module == null || !module.isEnabled()) {
            backgrounded = false;
            return vanillaLimit;
        }

        int mode = 1; // 0 = Aggressive, 1 = Balanced, 2 = Off
        int unfocusedFps = 60;
        int hiddenFps = 30;

        for (var opt : module.getOptions()) {
            if (opt.getName().equals("Mode") && opt instanceof DropdownOption drop) {
                mode = drop.getIndex();
            } else if (opt.getName().equals("Unfocused FPS") && opt instanceof SliderOption slider) {
                unfocusedFps = (int) slider.getValue();
            } else if (opt.getName().equals("Hidden FPS") && opt instanceof SliderOption slider) {
                hiddenFps = (int) slider.getValue();
            }
        }

        if (mode == 2) {
            backgrounded = false;
            return vanillaLimit;
        }

        Minecraft mc = Minecraft.getInstance();
        boolean focused = mc.isWindowActive();
        long windowHandle = mc.getWindow().getWindow();
        boolean hidden = GLFW.glfwGetWindowAttrib(windowHandle, GLFW.GLFW_ICONIFIED) == GLFW.GLFW_TRUE;

        int foreground = Math.max(1, vanillaLimit);
        long now = System.nanoTime();

        if (focused && !hidden) {
            backgrounded = false;
            return foreground;
        }

        if (!backgrounded || now < backgroundSince) {
            backgrounded = true;
            backgroundSince = now;
        }

        if (hidden) {
            return Math.min(foreground, Math.max(1, hiddenFps));
        }

        if (mode == 1 && (now - backgroundSince < 3_000_000_000L)) {
            return foreground;
        }

        return Math.min(foreground, Math.max(1, unfocusedFps));
    }
}
