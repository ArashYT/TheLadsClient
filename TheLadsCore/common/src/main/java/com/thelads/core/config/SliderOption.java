package com.thelads.core.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

public class SliderOption extends Option {
    private final double min;
    private final double max;
    private final double step;
    private double value;
    private final double defaultValue;
    private String suffix = "";
    private boolean editable;

    public SliderOption(String name, double defaultValue, double min, double max, double step) {
        super(name);
        if (!Double.isFinite(min) || !Double.isFinite(max) || min > max || !Double.isFinite(step) || step < 0) throw new IllegalArgumentException("Invalid slider bounds");
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

    /** Text after the value ("%"); the screen shows it in the value field. */
    public SliderOption suffix(String text) { this.suffix = text == null ? "" : text; return this; }
    /** Also shows the value in a text field next to the slider, where a whole number can be typed (see {@link #parse}). */
    public SliderOption editable() { this.editable = true; return this; }
    /** A percentage: shown with "%" and typeable. */
    public SliderOption percent() { return suffix("%").editable(); }
    public boolean isEditable() { return editable; }

    /** The value as the screen shows it: no raw decimals ("65%", "1.5"). */
    public String display() { return format(value) + suffix; }
    public static String format(double v) { return String.format(java.util.Locale.ROOT, "%.2f", v).replaceAll("\\.?0+$", ""); }
    /**
     * What a typed text means: a whole number (a trailing suffix is allowed) inside the slider's range, rounded to the nearest
     * step like every other way of setting the value. Anything else (letters, decimals, out of range, empty) is not an answer.
     */
    public java.util.OptionalDouble parse(String text) {
        String t = text == null ? "" : text.trim();
        if (!suffix.isEmpty() && t.endsWith(suffix)) t = t.substring(0, t.length() - suffix.length()).trim();
        if (!t.matches("-?\\d{1,9}")) return java.util.OptionalDouble.empty();
        double typed = Double.parseDouble(t);
        return typed < min || typed > max ? java.util.OptionalDouble.empty() : java.util.OptionalDouble.of(clamp(typed));
    }

    private double clamp(double v) {
        if (!Double.isFinite(v)) return min;
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
