package com.thelads.core.config;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import java.util.ArrayList;
import java.util.List;

/**
 * An ordered list of uploaded sounds (file names in the shared Lads sounds folder, as {@link SoundOption}): Combo Sounds' sounds
 * after Combo 5, the library's favourites. Changed on the game thread only.
 */
public class SoundListOption extends Option {
    private final List<String> files = new ArrayList<>();

    public SoundListOption(String name) { super(name); }

    public List<String> get() { return List.copyOf(files); }
    public int size() { return files.size(); }
    public boolean contains(String file) { return files.contains(file); }
    public void set(List<String> values) { files.clear(); for (String file : values) add(file); }
    public void add(String file) { if (file != null && !file.isEmpty()) files.add(file); }
    public void remove(int index) { if (index >= 0 && index < files.size()) files.remove(index); }
    /** Every entry of {@code file} (a deleted sound). */
    public void removeAll(String file) { files.removeIf(file::equals); }
    /** On if off, off if on (favourites). */
    public void toggle(String file) { if (files.contains(file)) removeAll(file); else add(file); }
    /** Moves entry {@code index} one place up ({@code -1}) or down ({@code 1}). */
    public void move(int index, int by) {
        int to = index + by;
        if (index < 0 || index >= files.size() || to < 0 || to >= files.size()) return;
        files.add(to, files.remove(index));
    }

    @Override public void reset() { files.clear(); }
    @Override public JsonElement save() {
        JsonArray array = new JsonArray();
        files.forEach(array::add);
        return array;
    }
    @Override public void load(JsonElement element) {
        files.clear();
        try {
            for (JsonElement file : element.getAsJsonArray()) add(file.getAsString());
        } catch (Exception ignored) {}
    }
}
