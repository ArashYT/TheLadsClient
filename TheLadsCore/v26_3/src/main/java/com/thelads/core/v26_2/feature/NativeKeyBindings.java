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
    private NativeKeyBindings() {}
}
