package com.thelads.core.client.hud;

import com.thelads.core.client.bridge.LadsGameBridge;
import com.thelads.core.client.bridge.LadsGraphics;

/**
 * Small HUD widget: displays currently loaded Iris/OptiFine shaderpack.
 * Visual-only.
 */
public class ShaderPackHudElement extends TextHudElement {
    public static final String MODULE_NAME = "ShaderPack";

    public ShaderPackHudElement() {
        super(75);
        useOrganizedDefaults();
    }

    @Override
    protected String updateText(LadsGraphics graphics) {
        LadsGameBridge bridge = LadsGameBridge.get();
        String pack = bridge != null ? bridge.getActiveShaderPackName() : null;
        if (pack == null || pack.isEmpty() || pack.equalsIgnoreCase("None")) {
            pack = editor ? "Complementary" : "Off";
        }
        return "Shader: " + pack;
    }
}
