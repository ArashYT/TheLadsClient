package com.thelads.core.v1_8_9.feature;

public final class TabListCache189 {
    private static int version = 0;

    private TabListCache189() {}

    public static void invalidate() {
        version++;
    }

    public static int getVersion() {
        return version;
    }
}
