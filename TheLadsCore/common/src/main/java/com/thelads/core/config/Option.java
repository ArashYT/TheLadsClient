package com.thelads.core.config;

import com.google.gson.JsonElement;

/**
 * A single named, persistable setting on a Module. Concrete types:
 * {@link BoolOption} (toggle), {@link DropdownOption} (pick-one list), {@link SliderOption} (numerical range), and {@link TextOption} (text input).
 */
public abstract class Option {
    protected final String name;
    /** The module this option belongs to, set by {@link Module#addOption}: LadsClientAPI rules are keyed by module + option name. */
    String moduleName = "";

    protected Option(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public String getModuleName() {
        return moduleName;
    }

    /** How far a LadsClientAPI server may touch this option. Text, actions, sounds and anything else not overriding this: never. */
    public com.thelads.core.client.ladsapi.ServerControl serverControl() {
        return com.thelads.core.client.ladsapi.ServerControl.NEVER;
    }

    public abstract JsonElement save();

    public abstract void load(JsonElement element);

    /** Restore this option to the value it was created with. */
    public void reset() {
    }
}
