package com.thelads.core.v26_2.feature;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public final class NativeKeyBindings {
    public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
        Identifier.fromNamespaceAndPath("theladscore", "controls"));
    public static final KeyMapping ZOOM = new KeyMapping("key.theladscore.zoom",
        InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_C, CATEGORY);
    public static final KeyMapping MODULES = new KeyMapping("key.theladscore.modules",
        InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, CATEGORY);
    /** Toggle Sprint & Sneak: unbound by default, so Sprint and Sneak toggle (NativeFeatures); bound, these toggle instead. */
    public static final KeyMapping TOGGLE_SPRINT = new KeyMapping("key.theladscore.toggle_sprint",
        InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, CATEGORY);
    public static final KeyMapping TOGGLE_SNEAK = new KeyMapping("key.theladscore.toggle_sneak",
        InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, CATEGORY);
    private NativeKeyBindings() {}
}
