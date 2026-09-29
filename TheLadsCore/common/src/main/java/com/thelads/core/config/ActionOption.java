package com.thelads.core.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;

/** Version-neutral link to an editor. No executable callback is serialized into user configuration. */
public final class ActionOption extends Option {
    private final String label;
    private Runnable action;
    public ActionOption(String name, String label) { super(name); this.label = label; }
    public String getLabel() { return label; }
    public boolean isAvailable() { return action != null; }
    public void setAction(Runnable value) { action = value; }
    public void run() { if (action != null) action.run(); }
    @Override public JsonElement save() { return JsonNull.INSTANCE; }
    @Override public void load(JsonElement ignored) {}
}
