// GLFW implementation for Minecraft 1.20.1. Dynamic FPS policy remains MIT-derived (see licenses/DynamicFPS-LICENSE.txt).
package com.thelads.core.v1_20_1.perf.dynamicfps.feature.state;

import com.thelads.core.v1_20_1.perf.dynamicfps.DynamicFPSMod;
import org.lwjgl.glfw.GLFW;

/**
 * Watches the game window's focus, hover and minimised flags. They are polled once per loop iteration (the loop keeps running
 * while rendering is skipped), which needs no callback chaining with other mods that set their own GLFW callbacks.
 */
public final class WindowObserver {
    private final long address;
    private boolean isFocused, isHovered, isIconified;

    public WindowObserver(long address) {
        this.address = address;
        isFocused = attribute(GLFW.GLFW_FOCUSED);
        isHovered = attribute(GLFW.GLFW_HOVERED);
        isIconified = attribute(GLFW.GLFW_ICONIFIED);
    }

    /**
     * QA only (null = ask GLFW): a QA window is usually the OS-active window, and GLFW keeps reporting focus for it even when it is
     * hidden, so the harness cannot lose focus the way an alt-tab does. These stand in for the two GLFW reads, the rest of the engine
     * (state machine, limiter, sound fades) runs unchanged.
     */
    public static volatile Boolean qaFocused, qaHovered;

    private boolean attribute(int attribute) {
        if (attribute == GLFW.GLFW_FOCUSED && qaFocused != null) return qaFocused;
        if (attribute == GLFW.GLFW_HOVERED && qaHovered != null) return qaHovered;
        return GLFW.glfwGetWindowAttrib(address, attribute) == GLFW.GLFW_TRUE;
    }

    public long address() { return address; }
    public boolean isFocused() { return isFocused; }
    public boolean isHovered() { return isHovered; }
    public boolean isIconified() { return isIconified; }

    public void poll() {
        boolean focused = attribute(GLFW.GLFW_FOCUSED);
        boolean hovered = attribute(GLFW.GLFW_HOVERED);
        boolean iconified = attribute(GLFW.GLFW_ICONIFIED);
        if (focused == isFocused && hovered == isHovered && iconified == isIconified) return;
        boolean gainedFocus = focused && !isFocused;
        isFocused = focused;
        isHovered = hovered;
        isIconified = iconified;
        if (gainedFocus) ClickIgnoreHandler.onFocus();
        DynamicFPSMod.onStatusChanged(true);
    }
}
