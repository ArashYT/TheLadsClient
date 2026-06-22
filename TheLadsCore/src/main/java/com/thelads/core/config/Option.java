package com.thelads.core.config;

import com.google.gson.JsonElement;

/**
 * A single named, persistable setting on a Module. Concrete types:
 * {@link BoolOption} (toggle), {@link DropdownOption} (pick-one list), {@link SliderOption} (numerical range), and {@link TextOption} (text input).
 */
public abstract class Option {
    protected final String name;

    protected Option(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public abstract JsonElement save();

    public abstract void load(JsonElement element);

    /** Restore this option to the value it was created with. */
    public void reset() {
    }
}
