package com.thelads.core.modules;

import com.thelads.core.config.Module;

public class HudModule extends Module {
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
