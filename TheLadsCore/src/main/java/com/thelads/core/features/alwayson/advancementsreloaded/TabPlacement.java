package com.thelads.core.features.alwayson.advancementsreloaded;

import com.thelads.core.features.alwayson.advancementsreloaded.config.Configuration;

public enum TabPlacement {
    ABOVE(26, 26, 5, 5),
    BELOW(26, 26, 5, 5);

    private final int width;
    private final int height;
    private final int topMargin;
    private final int leftMargin;

    TabPlacement(int width, int height, int topMargin, int leftMargin) {
        this.width = width;
        this.height = height;
        this.topMargin = topMargin;
        this.leftMargin = leftMargin;
    }

    public int getTabLimit() {
        return getDynamicLimit();
    }

    private int getDynamicLimit() {
        return switch (this) {
            case ABOVE -> Configuration.aboveWidgetLimit;
            case BELOW -> Configuration.belowWidgetLimit;
        };
    }

    public int getTabX(int index) {
        switch (this) {
            case ABOVE:
                return (this.width + 2) * index;
            case BELOW:
                if (Configuration.aboveWidgetLimit <= index) {
                    return (this.width + 2) * (index - Configuration.aboveWidgetLimit);
                }
                return (this.width + 2) * index;
        }
        throw new UnsupportedOperationException("Unknown tab type: " + this);
    }

    public int getTabY(int index) {
        return switch (this) {
            case ABOVE -> -this.height + 4;
            case BELOW -> -4;
        };
    }

    public int getWidth() { return this.width; }
    public int getHeight() { return this.height; }
    public int getTopMargin() { return this.topMargin; }
    public int getLeftMargin() { return this.leftMargin; }
}
