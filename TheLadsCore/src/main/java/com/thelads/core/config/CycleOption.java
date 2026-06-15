package com.thelads.core.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

/** Pick-one-of-N setting; persists the selected index. */
public class CycleOption extends Option {
    private final String[] choices;
    private int index;
    private final int defaultIndex;

    public CycleOption(String name, int defaultIndex, String... choices) {
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
        return index;
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
        return choices.length == 0 ? "" : choices[index];
    }

    public String[] getChoices() {
        return choices;
    }

    @Override
    public JsonElement save() {
        return new JsonPrimitive(index);
    }

    @Override
    public void load(JsonElement element) {
        try {
            setIndex(element.getAsInt());
        } catch (Exception ignored) {
        }
    }
}
