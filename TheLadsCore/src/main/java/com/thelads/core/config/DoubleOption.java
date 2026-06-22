package com.thelads.core.config;

public class DoubleOption extends Option {
    private double value;
    private final double min;
    private final double max;
    private final double defaultValue;

    public DoubleOption(String name, double defaultValue, double min, double max) {
        super(name);
        this.value = defaultValue;
        this.defaultValue = defaultValue;
        this.min = min;
        this.max = max;
    }

    public double get() { return value; }
    public void set(double value) { this.value = Math.max(min, Math.min(max, value)); }
    public double getMin() { return min; }
    public double getMax() { return max; }

    @Override
    public void reset() {
        this.value = defaultValue;
    }

    @Override
    public com.google.gson.JsonElement save() {
        com.google.gson.JsonObject o = new com.google.gson.JsonObject();
        o.addProperty("value", value);
        return o;
    }

    @Override
    public void load(com.google.gson.JsonElement element) {
        try {
            com.google.gson.JsonObject o = element.getAsJsonObject();
            if (o.has("value")) {
                value = o.get("value").getAsDouble();
            }
        } catch (Exception ignored) {}
    }
}
