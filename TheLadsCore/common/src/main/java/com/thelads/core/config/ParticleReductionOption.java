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
 * map is replaced whole on a change and read without locking.
 */
public final class ParticleReductionOption extends Option {
    private volatile Map<String, Integer> reduced = Map.of();
    private Supplier<List<String>> catalog = List::of;

    public ParticleReductionOption(String name) { super(name); }

    /** The share of this type that still spawns, 0 to 100; types never edited are 100. */
    public int percent(String id) {
        Integer value = reduced.get(id);
        return value == null ? 100 : value;
    }

    public void set(String id, int percent) {
        if (id == null || id.isEmpty()) return;
        id = normalise(id);
        int value = Math.max(0, Math.min(100, percent));
        Map<String, Integer> next = new LinkedHashMap<>(reduced);
        if (value == 100) next.remove(id); else next.put(id, value);
        reduced = Collections.unmodifiableMap(next);
    }

    /** How many types are below 100%. */
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
            try {
                int value = Math.max(0, Math.min(100, entry.getValue().getAsInt()));
                if (value < 100 && !entry.getKey().isEmpty()) next.put(normalise(entry.getKey()), value);
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
