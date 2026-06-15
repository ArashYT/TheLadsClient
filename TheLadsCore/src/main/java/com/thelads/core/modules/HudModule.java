package com.thelads.core.modules;

import com.thelads.core.config.Module;

/**
 * Generic toggle module for an on-screen HUD element. Holds no behaviour of its
 * own — the matching {@link com.thelads.core.client.hud.HudElement} reads this
 * module's enabled state to decide whether to render. Like every Module it
 * starts disabled, so all HUD overlays are off until enabled in the settings
 * screen (Right Shift).
 */
public class HudModule extends Module {
    // When true the element draws in the shared global colour; when false it
    // uses its own customColor. Toggled from the settings screen.
    private boolean useGlobalColor = true;
    private int customColor = 0xFFFFFFFF; // ARGB

    public HudModule(String name, String description) {
        super(name, description);
    }

    public boolean isUseGlobalColor() { return useGlobalColor; }
    public void setUseGlobalColor(boolean useGlobalColor) { this.useGlobalColor = useGlobalColor; }

    public int getCustomColor() { return customColor; }
    public void setCustomColor(int customColor) { this.customColor = customColor; }
}
