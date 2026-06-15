package com.thelads.core.client.renderscale;

public enum RenderScalePreset {
    CUSTOM("Custom"),
    ULTRA_PERFORMANCE("Ultra Performance"),
    BALANCED("Balanced"),
    QUALITY("Quality"),
    SUPER_SAMPLING("Super Sampling");

    private final String name;

    private RenderScalePreset(String name) {
        this.name = name;
    }

    public String toString() {
        return this.name;
    }
}
