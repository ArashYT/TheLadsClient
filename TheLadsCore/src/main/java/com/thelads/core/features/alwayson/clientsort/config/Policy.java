/*
 * Decompiled with CFR 0.152.
 */
package com.thelads.core.features.alwayson.clientsort.config;

import com.thelads.core.features.alwayson.clientsort.ClientSort;
import com.thelads.core.features.alwayson.clientsort.ClientSortClient;

public enum Policy {
    KEYBIND_BUTTON(true, true),
    KEYBIND(true, false),
    NONE(false, false);

    public final boolean keybind;
    public final boolean button;

    private Policy(boolean keybind, boolean button) {
        this.keybind = keybind;
        this.button = button;
    }

    public String toSimpleString() {
        return switch (this.ordinal()) {
            default -> throw new MatchException(null, null);
            case 0 -> "2";
            case 1 -> "1";
            case 2 -> "0";
        };
    }

    public static Policy fromSimpleString(String str) {
        return switch (str) {
            case "2" -> KEYBIND_BUTTON;
            case "1" -> KEYBIND;
            case "0" -> NONE;
            default -> throw new IllegalArgumentException();
        };
    }
}
