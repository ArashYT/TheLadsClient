package com.thelads.core.modules;

import com.thelads.core.config.Module;

public class HudModule extends Module {
    private final com.thelads.core.config.ColorOption textColor = addOption(new com.thelads.core.config.ColorOption("Text Color", true, 0xFFFFFFFF));
    private final com.thelads.core.config.BoolOption ignoreFpsCap = addOption(new com.thelads.core.config.BoolOption("Ignore HUD FPS Cap", false));

    public HudModule(String name, String description) {
        super(name, description);
    }

    public boolean isIgnoreFpsCap() {
        return ignoreFpsCap.get() || com.thelads.core.config.HudSettings.getInstance().isModuleExempt(getName());
    }

    public void setIgnoreFpsCap(boolean value) {
        ignoreFpsCap.set(value);
        com.thelads.core.config.HudSettings.getInstance().setModuleExempt(getName(), value);
    }

    public boolean isUseGlobalColor() { return textColor.isUseGlobal(); }
    public void setUseGlobalColor(boolean useGlobalColor) { textColor.setUseGlobal(useGlobalColor); }

    public int getCustomColor() { return textColor.getColor(); }
    public void setCustomColor(int customColor) { textColor.setColor(customColor); }
}
