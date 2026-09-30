package com.thelads.core.modules;

import com.thelads.core.config.Module;

public class HudModule extends Module {
    private final com.thelads.core.config.ColorOption textColor = addOption(new com.thelads.core.config.ColorOption("Text Color", true, 0xFFFFFFFF));

    public HudModule(String name, String description) {
        super(name, description);
    }

    public boolean isUseGlobalColor() { return textColor.isUseGlobal(); }
    public void setUseGlobalColor(boolean useGlobalColor) { textColor.setUseGlobal(useGlobalColor); }

    public int getCustomColor() { return textColor.getColor(); }
    public void setCustomColor(int customColor) { textColor.setColor(customColor); }
}
