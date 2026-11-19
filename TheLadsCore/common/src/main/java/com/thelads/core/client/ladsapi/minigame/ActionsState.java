package com.thelads.core.client.ladsapi.minigame;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Server actions (PROTOCOL.md section 20): up to 8 keys the server names, listed in Controls under "Server" by the version hook
 * ({@link Hooks.ServerKeys#changed}). A press goes out only through the gate {@link #press}. Never written to options.txt.
 * Game thread only.
 */
public final class ActionsState {
    public static final int MAX = 8, MAX_PER_SECOND = 20;
    private static final Set<String> PADS = Set.of("a", "b", "x", "y", "lb", "rb", "lt", "rt", "ls", "rs", "dpad_up", "dpad_down",
        "dpad_left", "dpad_right");
    private static final Pattern KEY = Pattern.compile("[a-z0-9_.]{1,64}");

    /** {@code key} (an options.txt name) and {@code pad} are null when the action starts unbound there. */
    public record Action(String id, String name, String key, String pad) {}

    private List<Action> list = List.of();
    private final ArrayDeque<Long> sent = new ArrayDeque<>();

    public List<Action> list() {
        return list;
    }

    /** Drops the list and tells the version to remove its keys. */
    public void clear() {
        boolean had = !list.isEmpty();
        list = List.of();
        sent.clear();
        Hooks.ServerKeys k = Hooks.keys;
        if (had && k != null) k.changed(List.of());
    }

    /** A full-state list. A message without the array is ignored. */
    public void apply(JsonObject json) {
        JsonArray arr = Wire.arr(json, "actions");
        if (arr == null) return;
        Hooks.ServerKeys hook = Hooks.keys;
        List<Action> out = new ArrayList<>();
        Set<String> ids = new HashSet<>(), keys = new HashSet<>(), pads = new HashSet<>();
        for (JsonElement e : arr) {
            if (out.size() >= MAX) break;
            if (!e.isJsonObject()) continue;
            JsonObject o = e.getAsJsonObject();
            String id = Wire.id(o, "id", 24);
            if (id == null || !ids.add(id)) continue;
            String name = Wire.text(o, "name", 24);
            String key = Wire.str(o, "key");
            if (key != null && (!KEY.matcher(key).matches() || !keys.add(key) || hook != null && !hook.keyFree(key))) key = null;
            String pad = Wire.str(o, "pad");
            if (pad != null && (!PADS.contains(pad) || !pads.add(pad))) pad = null;
            out.add(new Action(id, name == null || name.isBlank() ? id : name, key, pad));
        }
        list = List.copyOf(out);
        if (hook != null) hook.changed(list);
    }

    /**
     * Whether a real press or release of this action may go out now: the id is in the current list and at most 20 per second.
     * Count the call as sent.
     */
    public boolean press(String id, long now) {
        if (id == null || list.stream().noneMatch(a -> a.id().equals(id))) return false;
        while (!sent.isEmpty() && now - sent.peekFirst() >= 1000) sent.pollFirst();
        if (sent.size() >= MAX_PER_SECOND) return false;
        sent.addLast(now);
        return true;
    }
}
