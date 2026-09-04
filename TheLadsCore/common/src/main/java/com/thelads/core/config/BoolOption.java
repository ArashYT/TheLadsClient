package com.thelads.core.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

public class BoolOption extends Option {
    private boolean value;
    private final boolean defaultValue;

    public BoolOption(String name, boolean defaultValue) {
        super(name);
        this.value = defaultValue;
        this.defaultValue = defaultValue;
    }

    @Override
    public void reset() {
        this.value = defaultValue;
    }

    public boolean get() {
        return value;
    }

    public void set(boolean value) {
        this.value = value;
    }

    public void toggle() {
        this.value = !this.value;
    }

    @Override
    public JsonElement save() {
        return new JsonPrimitive(value);
    }

    @Override
    public void load(JsonElement element) {
        try {
            value = element.getAsBoolean();
        } catch (Exception ignored) {
        }
    }
}
