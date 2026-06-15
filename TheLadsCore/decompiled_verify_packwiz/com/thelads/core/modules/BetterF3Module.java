/*
 * Decompiled with CFR 0.152.
 */
package com.thelads.core.modules;

import com.thelads.core.config.Module;
import java.util.ArrayList;
import java.util.List;

public class BetterF3Module
extends Module {
    public BetterF3Module() {
        super("BetterF3", "Customizable debug screen");
    }

    public List<String> filterLeftText(List<String> list) {
        if (list == null) {
            return new ArrayList<String>();
        }
        ArrayList<String> mutableList = new ArrayList<String>(list);
        if (!this.isEnabled()) {
            return mutableList;
        }
        mutableList.removeIf(line -> line != null && line.startsWith("XYZ:"));
        return mutableList;
    }

    public List<String> filterRightText(List<String> list) {
        if (list == null) {
            return new ArrayList<String>();
        }
        ArrayList<String> mutableList = new ArrayList<String>(list);
        if (!this.isEnabled()) {
            return mutableList;
        }
        mutableList.removeIf(line -> line != null && line.startsWith("Java:"));
        return mutableList;
    }
}

