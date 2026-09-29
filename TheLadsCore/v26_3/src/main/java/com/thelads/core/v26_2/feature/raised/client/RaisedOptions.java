// Adapted from Raised 6.0.0 source by yurisuika, LGPL-3.0-or-later.
// Pinned source 4b6a3b8718316d681c3ae6832cdd19c3174a29a4; see META-INF/lads-sources/raised.
package com.thelads.core.v26_2.feature.raised.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.thelads.core.v26_2.feature.raised.Raised;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public class RaisedOptions {

    public static final KeyMapping OPTIONS = new KeyMapping(
            "key.raised.options",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_GRAVE_ACCENT,
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath(Raised.MOD_ID, "raised"))
    );

}