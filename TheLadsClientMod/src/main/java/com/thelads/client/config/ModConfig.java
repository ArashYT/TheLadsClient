package com.thelads.client.config;

public class ModConfig {
    private boolean capesEnabled = true;
    private boolean uiScalingEnabled = false;

    public boolean isCapesEnabled() {
        return capesEnabled;
    }

    public void setCapesEnabled(boolean capesEnabled) {
        this.capesEnabled = capesEnabled;
    }

    public boolean isUiScalingEnabled() {
        return uiScalingEnabled;
    }

    public void setUiScalingEnabled(boolean uiScalingEnabled) {
        this.uiScalingEnabled = uiScalingEnabled;
    }
}
