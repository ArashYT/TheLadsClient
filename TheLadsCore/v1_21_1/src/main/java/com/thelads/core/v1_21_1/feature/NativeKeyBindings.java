package com.thelads.core.v1_21_1.feature;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

/** Same ids, defaults and category as 26.x; saved bindings keep their keys. */
public final class NativeKeyBindings {
    private static final String CATEGORY = "key.category.theladscore.controls";
    public static final KeyMapping ZOOM = new KeyMapping("key.theladscore.zoom",
        InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_C, CATEGORY);
    public static final KeyMapping MODULES = new KeyMapping("key.theladscore.modules",
        InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, CATEGORY);
    private NativeKeyBindings() {}

    /** Client init, before Options exists: Fabric API adds both to Controls and loads their saved keys. */
    public static void register() {
        KeyBindingHelper.registerKeyBinding(ZOOM);
        KeyBindingHelper.registerKeyBinding(MODULES);
    }
}
