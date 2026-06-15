/*
 * Decompiled with CFR 0.152.
 */
package com.thelads.core.modules;

import com.thelads.core.config.Module;

public class HudModule
extends Module {
    private boolean useGlobalColor = true;
    private int customColor = -1;

    public HudModule(String name, String description) {
        super(name, description);
    }

    public boolean isUseGlobalColor() {
        return this.useGlobalColor;
    }

    public void setUseGlobalColor(boolean useGlobalColor) {
        this.useGlobalColor = useGlobalColor;
    }

    public int getCustomColor() {
        return this.customColor;
    }

    public void setCustomColor(int customColor) {
        this.customColor = customColor;
    }
}

