package com.thelads.core.v26_2.feature;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

public final class NativeKeyBindings {
    public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
        Identifier.fromNamespaceAndPath("theladscore", "controls"));
    public static final KeyMapping ZOOM = new KeyMapping("key.theladscore.zoom",
        InputConstants.Type.KEYBOARD, InputConstants.KEY_C, CATEGORY);
    public static final KeyMapping MODULES = new KeyMapping("key.theladscore.modules",
        InputConstants.Type.KEYBOARD, InputConstants.KEY_RSHIFT, CATEGORY);
    public static final KeyMapping QUICKPLAY = new KeyMapping("key.theladscore.quickplay",
        InputConstants.Type.KEYBOARD, InputConstants.UNKNOWN.getValue(), CATEGORY);
    public static final KeyMapping PLAY_AGAIN = new KeyMapping("key.theladscore.play_again",
        InputConstants.Type.KEYBOARD, InputConstants.UNKNOWN.getValue(), CATEGORY);
    /** Toggle Sprint & Sneak, listed under Movement right after Sprint and Sneak ("Sprint (Toggle)"): unbound by default, so Sprint
     * and Sneak toggle (NativeFeatures); bound, these toggle instead. */
    public static final KeyMapping TOGGLE_SPRINT = new KeyMapping("key.theladscore.toggle_sprint",
        InputConstants.Type.KEYBOARD, InputConstants.UNKNOWN.getValue(), KeyMapping.Category.MOVEMENT);
    public static final KeyMapping TOGGLE_SNEAK = new KeyMapping("key.theladscore.toggle_sneak",
        InputConstants.Type.KEYBOARD, InputConstants.UNKNOWN.getValue(), KeyMapping.Category.MOVEMENT);
    private NativeKeyBindings() {}
}
