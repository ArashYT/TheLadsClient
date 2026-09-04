package com.thelads.core.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/** A colour setting that can either follow a global colour or use its own. */
public class ColorOption extends Option {
    private boolean useGlobal;
    private int color; // ARGB
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
        this.useGlobal = defaultUseGlobal;
        this.color = defaultColor;
    }

    public boolean isUseGlobal() {
        return useGlobal;
    }

    public void setUseGlobal(boolean useGlobal) {
        this.useGlobal = useGlobal;
    }

    public int getColor() {
        return color;
    }

    public void setColor(int color) {
        this.color = color;
    }

    @Override
    public JsonElement save() {
        JsonObject o = new JsonObject();
        o.addProperty("useGlobal", useGlobal);
        o.addProperty("color", color);
        return o;
    }

    @Override
    public void load(JsonElement element) {
        try {
            JsonObject o = element.getAsJsonObject();
            if (o.has("useGlobal")) {
                useGlobal = o.get("useGlobal").getAsBoolean();
            }
            if (o.has("color")) {
                color = o.get("color").getAsInt();
            }
        } catch (Exception ignored) {
        }
    }
}
