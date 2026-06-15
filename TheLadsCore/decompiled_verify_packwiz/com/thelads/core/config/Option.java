/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.google.gson.JsonElement
 */
package com.thelads.core.config;

import com.google.gson.JsonElement;

public abstract class Option {
    protected final String name;

    protected Option(String name) {
        this.name = name;
    }

    public String getName() {
        return this.name;
    }

    public abstract JsonElement save();

    public abstract void load(JsonElement var1);

    public void reset() {
    }
}

