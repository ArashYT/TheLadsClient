package com.thelads.core.client.hud;

import com.thelads.core.client.bridge.LadsGraphics;

/** Shader Pack (OneClient's Shaders Indicator): the shader pack in use (Iris on 1.20.1 and 26.3, OptiFine on 1.8.9), or Off. */
public class ShaderPackHudElement extends TextHudElement {
    private String text = "", name;

    public ShaderPackHudElement() {
        super(60);
    }

    @Override
    protected String updateText(LadsGraphics g) {
        String n = HudExtras.shaderName;
        if (!n.equals(name)) {
            text = "Shaders: " + (n.isEmpty() ? "Off" : n);
            name = n;
        }
        return text;
    }
}
