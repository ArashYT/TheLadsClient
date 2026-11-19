package com.thelads.ladsapi.session;

import com.google.gson.JsonObject;
import com.thelads.ladsapi.api.Dialog;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * The minigame state one session has told its client (PROTOCOL.md section 8), in memory only. Every send goes through the
 * client's cap (no cap: nothing, returns false), full-state messages are not sent twice, and everything is sent again with
 * its remaining time after a later hello ({@link #resend()}).
 */
public final class Features {
    /** Turns a kept message into what to send now, {@code elapsedMs} after it was first sent; null drops it. */
    public interface Adjust { JsonObject apply(JsonObject json, long elapsedMs); }

    private static final class Entry {
        final JsonObject json;
        final long sentMs;
        final long durMs;
        final Adjust adjust;

        Entry(JsonObject json, long sentMs, long durMs, Adjust adjust) {
            this.json = json;
            this.sentMs = sentMs;
            this.durMs = durMs;
            this.adjust = adjust;
        }
    }

    private static final class Dlg {
        final Dialog.Callback callback;
        final int token;
        final Set<String> buttons;

        Dlg(Dialog.Callback callback, int token, Set<String> buttons) {
            this.callback = callback;
            this.token = token;
            this.buttons = buttons;
        }
    }

    public static final int MAX_BYTES = 524_288;
    public static final int MAX_DIALOGS = 4;

    private final LadsSession session;
    private final Map<String, Entry> state = new LinkedHashMap<>();
    private final Map<String, Dlg> dialogs = new HashMap<>();
    private int dialogToken;
    private Set<String> actionIds = Set.of();
    private String runCourse;
    private long runStartMs;

    Features(LadsSession session) {
        this.session = session;
    }

    /** Whether this session's client named the cap (and so may be sent that feature). */
    public boolean ok(String cap) {
        return session.isLads() && session.hasCap(cap);
    }

    private long now() { return session.getManager().nowMs(); }

    private boolean transmit(JsonObject json) {
        byte[] bytes = json.toString().getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_BYTES) return false;
        session.getHandle().send(bytes);
        return true;
    }

    /** A message that is not state (rumble, camera, a one-off). */
    public synchronized boolean send(String cap, JsonObject json) {
        return ok(cap) && json != null && transmit(json);
    }

    /** Keeps {@code json} under {@code key} and sends it, unless the client has exactly this already. */
    public synchronized boolean keep(String cap, String key, JsonObject json, long durMs, boolean dedupe, Adjust adjust) {
        return keep(cap, key, json, null, durMs, dedupe, adjust);
    }

    /** As above; {@code wire} (when not null) is what goes out now, {@code json} is what is kept and sent again. */
    public synchronized boolean keep(String cap, String key, JsonObject json, JsonObject wire, long durMs, boolean dedupe, Adjust adjust) {
        if (!ok(cap) || json == null) return false;
        long now = now();
        Entry old = state.get(key);
        if (dedupe && wire == null && old != null && old.json.equals(json) && (old.durMs <= 0 || (now - old.sentMs) * 2 < old.durMs)) {
            return true;
        }
        JsonObject out = wire != null ? wire : json;
        if (!transmit(out)) return false;
        state.put(key, new Entry(json, now, durMs, adjust));
        return true;
    }

    public synchronized boolean has(String key) { return state.containsKey(key); }

    /** How many kept entries have a key starting with {@code prefix}. */
    public synchronized int count(String prefix) {
        int n = 0;
        for (String k : state.keySet()) if (k.startsWith(prefix)) n++;
        return n;
    }

    /** The kept keys starting with {@code prefix}, oldest first. */
    public synchronized java.util.List<String> keys(String prefix) {
        java.util.List<String> out = new java.util.ArrayList<>();
        for (String k : state.keySet()) if (k.startsWith(prefix)) out.add(k);
        return out;
    }

    /** The JSON kept under a key, or null. */
    public synchronized JsonObject kept(String key) {
        Entry e = state.get(key);
        return e == null ? null : e.json;
    }

    /** Replaces the kept JSON of a key without sending it (a volume change that was sent as its own message). */
    public synchronized void rekeep(String key, JsonObject json) {
        Entry e = state.get(key);
        if (e != null) state.put(key, new Entry(json, e.sentMs, e.durMs, e.adjust));
    }

    /** Forgets {@code key}; sends {@code msg} when there was one. True when the client may be sent the feature. */
    public synchronized boolean remove(String cap, String key, JsonObject msg) {
        if (!ok(cap)) return false;
        if (state.remove(key) != null && msg != null) transmit(msg);
        return true;
    }

    /** Forgets every key starting with {@code prefix}; sends {@code msg} once when there was any. */
    public synchronized boolean removeAll(String cap, String prefix, JsonObject msg) {
        if (!ok(cap)) return false;
        boolean any = state.keySet().removeIf(k -> k.startsWith(prefix));
        if (any && msg != null) transmit(msg);
        return true;
    }

    /** A new hello made the client drop everything: it gets the rest of every kept state again. */
    public synchronized void resend() {
        if (!session.isLads()) return;
        long now = now();
        Iterator<Map.Entry<String, Entry>> it = state.entrySet().iterator();
        while (it.hasNext()) {
            Entry e = it.next().getValue();
            long el = Math.max(0, now - e.sentMs);
            if (e.durMs > 0 && el >= e.durMs) {
                it.remove();
                continue;
            }
            JsonObject out = e.adjust == null ? e.json : e.adjust.apply(e.json, el);
            if (out == null) {
                it.remove();
                continue;
            }
            transmit(out);
        }
    }

    /** Counts the keys down by the elapsed time (not below 0) and leaves out the one-shot flash. */
    public static Adjust countdown(String... keys) {
        return (json, el) -> {
            JsonObject c = json.deepCopy();
            for (String k : keys) {
                if (c.has(k) && c.get(k).isJsonPrimitive()) c.addProperty(k, Math.max(0, c.get(k).getAsLong() - el));
            }
            c.remove("flash");
            return c;
        };
    }

    /** Counts a key up by the elapsed time. */
    public static Adjust countup(String key) {
        return (json, el) -> {
            JsonObject c = json.deepCopy();
            if (c.has(key)) c.addProperty(key, c.get(key).getAsLong() + el);
            return c;
        };
    }

    // ---- actions, dialogs, run clock ----

    public synchronized void setActionIds(Set<String> ids) { actionIds = ids == null ? Set.of() : Set.copyOf(ids); }

    public synchronized boolean hasAction(String id) { return actionIds.contains(id); }

    /** Opens a dialog: false without the cap, or when {@link #MAX_DIALOGS} others are open. It ends by itself after {@code ms}. */
    public synchronized boolean dialog(String id, JsonObject json, int ms, Set<String> buttons, Dialog.Callback callback) {
        if (!ok("dialog")) return false;
        if (!dialogs.containsKey(id) && dialogs.size() >= MAX_DIALOGS) return false;
        if (!keep("dialog", "dlg:" + id, json, ms, false, countdown("ms"))) return false;
        int token = ++dialogToken;
        dialogs.put(id, new Dlg(callback, token, buttons));
        session.getManager().getPlatform().runLater(() -> expire(id, token), Math.max(1, ms / 50));
        return true;
    }

    private void expire(String id, int token) {
        Dlg d;
        synchronized (this) {
            d = dialogs.get(id);
            if (d == null || d.token != token) return;
            dialogs.remove(id);
            state.remove("dlg:" + id);
        }
        if (session.getManager().getSessions().get(session.getHandle().uuid()) == session) {
            session.getManager().dispatchDialog(session, id, "timeout", d.callback);
        }
    }

    /** A client answer: returns the dialog's callback holder (and closes it) when {@code choice} is valid for an open dialog. */
    public synchronized Dialog.Callback takeAnswer(String id, String choice) {
        Dlg d = dialogs.get(id);
        if (d == null || choice == null || !("dismiss".equals(choice) || d.buttons.contains(choice))) return null;
        dialogs.remove(id);
        state.remove("dlg:" + id);
        return d.callback == null ? (p, c) -> { } : d.callback;
    }

    public synchronized boolean closeDialog(String id) {
        if (!ok("dialog")) return false;
        JsonObject msg = Wire.obj("dialog", "close");
        msg.addProperty("id", id);
        dialogs.remove(id);
        return remove("dialog", "dlg:" + id, msg);
    }

    public synchronized void closeAllDialogs() {
        for (String id : java.util.List.copyOf(dialogs.keySet())) closeDialog(id);
    }

    public synchronized void runClock(String course, long startMs) {
        runCourse = course;
        runStartMs = startMs;
    }

    /** Milliseconds of the running course on this server's clock, or -1 when {@code course} is not running. */
    public synchronized long runTime(String course) {
        return course != null && course.equals(runCourse) ? Math.max(0, now() - runStartMs) : -1;
    }

    public synchronized void endRunClock() { runCourse = null; }
}
