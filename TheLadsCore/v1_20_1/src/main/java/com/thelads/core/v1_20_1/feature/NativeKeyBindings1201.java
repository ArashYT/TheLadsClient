package com.thelads.core.v1_20_1.feature;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

public final class NativeKeyBindings1201 {
    public static final String CATEGORY = "key.category.theladscore.controls";

    public static final KeyMapping ZOOM = KeyBindingHelper.registerKeyBinding(new KeyMapping(
        "key.theladscore.zoom",
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_C,
        CATEGORY
    ));

    public static final KeyMapping MODULES = KeyBindingHelper.registerKeyBinding(new KeyMapping(
        "key.theladscore.modules",
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_RIGHT_SHIFT,
        CATEGORY
    ));

    public static final KeyMapping QUICKPLAY = KeyBindingHelper.registerKeyBinding(new KeyMapping(
        "key.theladscore.quickplay",
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_UNKNOWN,
        CATEGORY
    ));

    public static final KeyMapping PLAY_AGAIN = KeyBindingHelper.registerKeyBinding(new KeyMapping(
        "key.theladscore.play_again",
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_UNKNOWN,
        CATEGORY
    ));

    public static final KeyMapping TOGGLE_SPRINT = KeyBindingHelper.registerKeyBinding(new KeyMapping(
        "key.theladscore.toggle_sprint",
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_UNKNOWN,
        KeyMapping.CATEGORY_MOVEMENT
    ));

    public static final KeyMapping TOGGLE_SNEAK = KeyBindingHelper.registerKeyBinding(new KeyMapping(
        "key.theladscore.toggle_sneak",
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_UNKNOWN,
        KeyMapping.CATEGORY_MOVEMENT
    ));

    private NativeKeyBindings1201() {}

    public static void init() {
        // Triggers static initialization and KeyBindingHelper registration
    }
}
