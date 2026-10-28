package com.thelads.core.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * How much of each particle type still spawns: 100 (vanilla) down to 0 (never). Only types below 100 are kept and saved, as
 * {"flame": 40, "smoke": 0}; ids are written without "minecraft:". The settings screen edits it in a searchable list whose
 * catalogue the game version supplies (its particle registry); the spawn hook reads {@link #percent} on every particle, so the
 * map is replaced whole on a change and read without locking. The same per-type editor serves Particle Core's other per-type
 * values (sizes, fade): a range with its own vanilla value, or {@link #colors} (an RGB per type, 0 = vanilla).
 */
public final class ParticleReductionOption extends Option {
    private volatile Map<String, Integer> reduced = Map.of();
    private Supplier<List<String>> catalog = List::of;
    public final int min, max, vanilla;
    public final boolean color;
    /** The editor's help line. */
    public final String hint;

    public ParticleReductionOption(String name) { this(name, 0, 100, 100, false, "Drag a bar: 100% is vanilla, 0% never spawns. Right-click a row to reset it."); }

    public ParticleReductionOption(String name, int min, int max, int vanilla, String hint) { this(name, min, max, vanilla, false, hint); }

    private ParticleReductionOption(String name, int min, int max, int vanilla, boolean color, String hint) {
        super(name);
        this.min = min; this.max = max; this.vanilla = vanilla; this.color = color; this.hint = hint;
    }

    /** One opaque RGB per type (stored as 0xFFRRGGBB, so black is not "unset"); 0 leaves the type's own colors. */
    public static ParticleReductionOption colors(String name, String hint) {
        return new ParticleReductionOption(name, Integer.MIN_VALUE, Integer.MAX_VALUE, 0, true, hint);
    }

    /** The share of this type that still spawns, 0 to 100; types never edited are 100. */
    public int percent(String id) { return value(id); }

    /** This type's value; types never edited are {@link #vanilla}. */
    public int value(String id) {
        Integer value = reduced.get(id);
        return value == null ? vanilla : value;
    }

    public void set(String id, int percent) {
        if (id == null || id.isEmpty()) return;
        id = normalise(id);
        int value = clamp(percent);
        Map<String, Integer> next = new LinkedHashMap<>(reduced);
        if (value == vanilla) next.remove(id); else next.put(id, value);
        reduced = Collections.unmodifiableMap(next);
    }

    private int clamp(int value) {
        return color ? (value == 0 ? 0 : value | 0xFF000000) : Math.max(min, Math.min(max, value));
    }

    /** How many types differ from vanilla. */
    public int changed() { return reduced.size(); }

    public Map<String, Integer> all() { return reduced; }

    /** The ids the editor lists: the game's particle types. */
    public void setCatalog(Supplier<List<String>> source) { catalog = source == null ? List::of : source; }

    /** The catalogue plus any saved id it lacks (another version's, or a mod no longer installed), so every change can be undone. */
    public List<String> ids() {
        List<String> result = new ArrayList<>();
        try {
            for (String id : catalog.get()) {
                String normal = normalise(id);
                if (!result.contains(normal)) result.add(normal);
            }
        } catch (RuntimeException ignored) {}
        for (String id : reduced.keySet()) if (!result.contains(id)) result.add(id);
        return result;
    }

    @Override public void reset() { reduced = Map.of(); }

    @Override
    public JsonElement save() {
        JsonObject object = new JsonObject();
        reduced.forEach(object::addProperty);
        return object;
    }

    @Override
    public void load(JsonElement element) {
        if (!(element instanceof JsonObject object)) return;
        Map<String, Integer> next = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            if (next.size() >= 1024) break; // more types than any pack has: a shared code could carry thousands (the editor lists them all)
            try {
                int value = clamp(entry.getValue().getAsInt());
                if (value != vanilla && !entry.getKey().isEmpty()) next.put(normalise(entry.getKey()), value);
            } catch (RuntimeException ignored) {}
        }
        reduced = Collections.unmodifiableMap(next);
    }

    /** Ids are stored lower-case without the vanilla namespace: "minecraft:Flame" and "flame" are one type. */
    public static String normalise(String id) {
        String value = id.trim().toLowerCase(java.util.Locale.ROOT);
        return value.startsWith("minecraft:") ? value.substring(10) : value;
    }
}
