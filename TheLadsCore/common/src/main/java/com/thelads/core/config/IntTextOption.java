package com.thelads.core.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

/** A whole number typed as text (the settings screen's text field), saved as a JSON number: anything else leaves the value as it was. */
public class IntTextOption extends TextOption {
    private final int min, max;

    public IntTextOption(String name, int defaultValue, int min, int max) {
        super(name, Integer.toString(defaultValue));
        this.min = min;
        this.max = max;
    }

    public int get() { return Integer.parseInt(getValue()); }
    public int getMin() { return min; }
    public int getMax() { return max; }

    /** A whole number from min to max, spaces around it allowed. */
    public boolean valid(String text) {
        try {
            int value = Integer.parseInt(text == null ? "" : text.trim());
            return value >= min && value <= max;
        } catch (NumberFormatException invalid) {
            return false;
        }
    }

    @Override
    public void setValue(String v) {
        if (valid(v)) super.setValue(Integer.toString(Integer.parseInt(v.trim())));
    }

    @Override
    public JsonElement save() { return new JsonPrimitive(get()); }
}
