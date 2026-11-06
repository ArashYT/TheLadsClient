package com.thelads.core.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

public class DropdownOption extends Option {
    private String[] choices;
    private int index;
    private final int defaultIndex;

    public DropdownOption(String name, int defaultIndex, String... choices) {
        super(name);
        this.choices = choices;
        this.index = clamp(defaultIndex);
        this.defaultIndex = clamp(defaultIndex);
    }

    @Override
    public void reset() {
        this.index = defaultIndex;
    }

    private int clamp(int i) {
        if (choices.length == 0) return 0;
        return ((i % choices.length) + choices.length) % choices.length;
    }

    public int getIndex() {
        return com.thelads.core.client.ladsapi.ServerPolicy.applyDropdown(getModuleName(), getName(), index, choices);
    }

    public void setIndex(int i) {
        this.index = clamp(i);
    }

    public void cycle() {
        setIndex(index + 1);
    }

    public void cycleBack() {
        setIndex(index - 1);
    }

    public String getValue() {
        int i = getIndex();
        return choices.length == 0 ? "" : choices[i];
    }

    public String[] getChoices() {
        return choices;
    }

    /** Picks a choice by name; one the list lacks (a saved pick from a list that loads later) is kept as an extra last choice. */
    public void select(String value) {
        int at = java.util.Arrays.asList(choices).indexOf(value);
        if (at < 0) {
            choices = java.util.Arrays.copyOf(choices, choices.length + 1);
            at = choices.length - 1;
            choices[at] = value;
        }
        index = at;
    }

    /** Replaces the choices (a list that loads later), keeping the player's pick. */
    public void setChoices(String... next) {
        String keep = choices.length == 0 ? null : choices[index];
        choices = next.clone();
        index = 0;
        if (keep != null) select(keep);
    }

    @Override
    public JsonElement save() {
        return new JsonPrimitive(index);
    }

    @Override
    public void load(JsonElement element) {
        try {
            if (element != null && element.isJsonPrimitive()) {
                JsonPrimitive p = element.getAsJsonPrimitive();
                if (p.isString()) {
                    String str = p.getAsString();
                    for (int i = 0; i < choices.length; i++) {
                        if (choices[i].equalsIgnoreCase(str)) {
                            setIndex(i);
                            return;
                        }
                    }
                }
            }
            setIndex(element.getAsInt());
        } catch (Exception ignored) {
        }
    }

    @Override
    public com.thelads.core.client.ladsapi.ServerControl serverControl() {
        return com.thelads.core.client.ladsapi.ServerControl.FULL;
    }

    /** The player's own choice, never a server rule: the settings screen edits and config saves use this. */
    public final int getOwnIndex() {
        return index;
    }
}
