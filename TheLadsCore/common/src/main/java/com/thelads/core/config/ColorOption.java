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
        com.thelads.core.client.ladsapi.ServerPolicy p = com.thelads.core.client.ladsapi.ServerPolicy.active();
        if (p != null && !p.refusesChanges()) {
            com.thelads.core.client.ladsapi.OptionRule rule = p.option(getModuleName(), getName());
            if (rule != null && rule.color != null) return false;
        }
        return useGlobal;
    }

    public void setUseGlobal(boolean useGlobal) {
        this.useGlobal = useGlobal;
    }

    public int getColor() {
        return com.thelads.core.client.ladsapi.ServerPolicy.applyColor(getModuleName(), getName(), color);
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

    @Override
    public com.thelads.core.client.ladsapi.ServerControl serverControl() {
        return com.thelads.core.client.ladsapi.ServerControl.FULL;
    }

    /** The player's own color, never a server rule: the settings screen edits and config saves use this. */
    public final int getOwnColor() {
        return color;
    }
}
