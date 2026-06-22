package com.thelads.core.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

public class SliderOption extends Option {
    private final double min;
    private final double max;
    private final double step;
    private double value;
    private final double defaultValue;

    public SliderOption(String name, double defaultValue, double min, double max, double step) {
        super(name);
        this.min = min;
        this.max = max;
        this.step = step;
        this.value = clamp(defaultValue);
        this.defaultValue = clamp(defaultValue);
    }

    @Override
    public void reset() {
        this.value = defaultValue;
    }

    private double clamp(double v) {
        if (v < min) return min;
        if (v > max) return max;
        if (step > 0) {
            v = min + Math.round((v - min) / step) * step;
            if (v > max) v = max;
        }
        return v;
    }

    public double getValue() {
        return value;
    }
    
    public int getIntValue() {
        return (int) Math.round(value);
    }

    public void setValue(double v) {
        this.value = clamp(v);
    }
    
    public double getMin() {
        return min;
    }

    public double getMax() {
        return max;
    }
    
    public double getStep() {
        return step;
    }

    @Override
    public JsonElement save() {
        return new JsonPrimitive(value);
    }

    @Override
    public void load(JsonElement element) {
        try {
            setValue(element.getAsDouble());
        } catch (Exception ignored) {
        }
    }
}
