/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.google.gson.JsonElement
 *  com.google.gson.JsonPrimitive
 */
package com.thelads.core.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.thelads.core.config.Option;

public class BoolOption
extends Option {
    private boolean value;
    private final boolean defaultValue;

    public BoolOption(String name, boolean defaultValue) {
        super(name);
        this.value = defaultValue;
        this.defaultValue = defaultValue;
    }

    @Override
    public void reset() {
        this.value = this.defaultValue;
    }

    public boolean get() {
        return this.value;
    }

    public void set(boolean value) {
        this.value = value;
    }

    public void toggle() {
        this.value = !this.value;
    }

    @Override
    public JsonElement save() {
        return new JsonPrimitive(Boolean.valueOf(this.value));
    }

    @Override
    public void load(JsonElement element) {
        try {
            this.value = element.getAsBoolean();
        }
        catch (Exception exception) {
            // empty catch block
        }
    }
}

