/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.google.gson.JsonElement
 *  com.google.gson.JsonObject
 */
package com.thelads.core.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.thelads.core.config.Option;

public class ColorOption
extends Option {
    private boolean useGlobal;
    private int color;
    private final boolean defaultUseGlobal;
    private final int defaultColor;

    public ColorOption(String name, boolean useGlobal, int color) {
        super(name);
        this.useGlobal = useGlobal;
        this.color = color;
        this.defaultUseGlobal = useGlobal;
        this.defaultColor = color;
    }

    @Override
    public void reset() {
        this.useGlobal = this.defaultUseGlobal;
        this.color = this.defaultColor;
    }

    public boolean isUseGlobal() {
        return this.useGlobal;
    }

    public void setUseGlobal(boolean useGlobal) {
        this.useGlobal = useGlobal;
    }

    public int getColor() {
        return this.color;
    }

    public void setColor(int color) {
        this.color = color;
    }

    @Override
    public JsonElement save() {
        JsonObject o = new JsonObject();
        o.addProperty("useGlobal", Boolean.valueOf(this.useGlobal));
        o.addProperty("color", (Number)this.color);
        return o;
    }

    @Override
    public void load(JsonElement element) {
        try {
            JsonObject o = element.getAsJsonObject();
            if (o.has("useGlobal")) {
                this.useGlobal = o.get("useGlobal").getAsBoolean();
            }
            if (o.has("color")) {
                this.color = o.get("color").getAsInt();
            }
        }
        catch (Exception exception) {
            // empty catch block
        }
    }
}

