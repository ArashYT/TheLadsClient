package com.thelads.core.config;

public class StringOption extends Option {
    private String value;
    private final String defaultValue;

    public StringOption(String name, String defaultValue) {
        super(name);
        this.value = defaultValue;
        this.defaultValue = defaultValue;
    }

    public String get() { return value; }
    public void set(String value) { this.value = value; }

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
                value = o.get("value").getAsString();
            }
        } catch (Exception ignored) {}
    }
}
