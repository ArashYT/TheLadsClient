package com.thelads.core.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

public class TextOption extends Option {
    private String value;
    private final String defaultValue;

    public TextOption(String name, String defaultValue) {
        super(name);
        this.value = defaultValue != null ? defaultValue : "";
        this.defaultValue = this.value;
    }

    @Override
    public void reset() {
        this.value = defaultValue;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String v) {
        this.value = v != null ? v : "";
    }

    @Override
    public JsonElement save() {
        return new JsonPrimitive(value);
    }

    @Override
    public void load(JsonElement element) {
        try {
            setValue(element.getAsString());
        } catch (Exception ignored) {
        }
    }
}
