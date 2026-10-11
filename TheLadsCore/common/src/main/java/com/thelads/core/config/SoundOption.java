package com.thelads.core.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

/**
 * A sound the player uploaded (CustomSounds): the file's name in the shared Lads sounds folder, "" for the built-in sound. The
 * settings draw it as a row with Upload, Play and Clear; the upload completes off the game thread, hence volatile.
 */
public final class SoundOption extends Option {
    private volatile String file = "";

    public SoundOption(String name) { super(name); }

    public String get() { return file; }
    public void set(String value) { file = value == null ? "" : value; }
    public boolean isSet() { return !file.isEmpty(); }

    @Override public void reset() { file = ""; }
    @Override public JsonElement save() { return new JsonPrimitive(file); }
    @Override public void load(JsonElement element) {
        try { set(element.getAsString()); } catch (Exception ignored) {}
    }
}
