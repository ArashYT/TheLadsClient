package com.thelads.core.perf;

/** Enum used by EnumValuesSamples. Its own values() call must stay as it is. */
public enum SampleColor {
    RED, GREEN, BLUE;

    public static SampleColor byIndex(int i) {
        return values()[i];
    }
}
