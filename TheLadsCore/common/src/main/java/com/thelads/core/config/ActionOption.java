package com.thelads.core.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;

/** Version-neutral link to an editor. No executable callback is serialized into user configuration. */
public final class ActionOption extends Option {
    private final String label;
    private Runnable action;
    private java.util.function.Supplier<String> liveLabel;
    private String confirm;
    public ActionOption(String name, String label) { super(name); this.label = label; }
    public String getLabel() { return liveLabel == null ? label : liveLabel.get(); }
    /** A label read on every frame (Toggle Sprint &amp; Sneak shows its current key). */
    public void setLabel(java.util.function.Supplier<String> value) { liveLabel = value; }
    public boolean isAvailable() { return action != null; }
    /** The menu asks "Are you sure you want to reset?" with this text first (reset buttons). */
    public ActionOption confirm(String message) { confirm = message; return this; }
    public String getConfirm() { return confirm; }
    public void setAction(Runnable value) { action = value; }
    public void run() { if (action != null) action.run(); }
    @Override public JsonElement save() { return JsonNull.INSTANCE; }
    @Override public void load(JsonElement ignored) {}
}
