package com.thelads.smp.features.arise.nb10;

import net.minecraft.util.StringRepresentable;

/**
 * Supported decorative hanging connection types.
 */
public enum DecorationType implements StringRepresentable {
    LIGHTS("lights", 14),
    PENNANT("pennant", 0),
    GARLAND("garland", 0),
    TINSEL("tinsel", 0);

    private final String name;
    private final int defaultLightLevel;

    DecorationType(String name, int defaultLightLevel) {
        this.name = name;
        this.defaultLightLevel = defaultLightLevel;
    }

    public int getDefaultLightLevel() {
        return defaultLightLevel;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    public static DecorationType byName(String name) {
        for (DecorationType type : values()) {
            if (type.name.equalsIgnoreCase(name)) {
                return type;
            }
        }
        return LIGHTS;
    }
}
