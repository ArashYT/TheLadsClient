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

public class CycleOption
extends Option {
    private final String[] choices;
    private int index;
    private final int defaultIndex;

    public CycleOption(String name, int defaultIndex, String ... choices) {
        super(name);
        this.choices = choices;
        this.index = this.clamp(defaultIndex);
        this.defaultIndex = this.clamp(defaultIndex);
    }

    @Override
    public void reset() {
        this.index = this.defaultIndex;
    }

    private int clamp(int i) {
        if (this.choices.length == 0) {
            return 0;
        }
        return (i % this.choices.length + this.choices.length) % this.choices.length;
    }

    public int getIndex() {
        return this.index;
    }

    public void setIndex(int i) {
        this.index = this.clamp(i);
    }

    public void cycle() {
        this.setIndex(this.index + 1);
    }

    public void cycleBack() {
        this.setIndex(this.index - 1);
    }

    public String getValue() {
        return this.choices.length == 0 ? "" : this.choices[this.index];
    }

    public String[] getChoices() {
        return this.choices;
    }

    @Override
    public JsonElement save() {
        return new JsonPrimitive((Number)this.index);
    }

    @Override
    public void load(JsonElement element) {
        try {
            this.setIndex(element.getAsInt());
        }
        catch (Exception exception) {
            // empty catch block
        }
    }
}

