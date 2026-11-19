package com.thelads.ladsapi.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.thelads.ladsapi.session.Wire;
import java.util.LinkedHashSet;
import java.util.Set;

/** An interactive popup (PROTOCOL.md section 21): {@code Dialog.vote("v1", "Pick a map").button("a", "Desert").ms(20000)}. */
public final class Dialog {
    /** Gets the answer: a button id, "dismiss" (Esc or close), or "timeout" (nobody answered in time). */
    @FunctionalInterface
    public interface Callback { void onChoice(LadsPlayer player, String choice); }

    private static final int MAX_BUTTONS = 6;

    private final String style;
    private final String id;
    private final String title;
    private String body;
    private int ms = 15000;
    private final JsonArray buttons = new JsonArray();
    private final Set<String> buttonIds = new LinkedHashSet<>();

    private Dialog(String style, String id, String title) {
        this.style = style;
        this.id = id;
        this.title = title;
    }

    /** A small corner card. */
    public static Dialog toast(String id, String title) { return new Dialog("toast", id, title); }

    /** A card with buttons the player clicks or picks with a number key. */
    public static Dialog vote(String id, String title) { return new Dialog("vote", id, title); }

    /** A centered window that takes the mouse until answered (Esc always dismisses it). */
    public static Dialog modal(String id, String title) { return new Dialog("modal", id, title); }

    public Dialog body(String body) { this.body = body; return this; }

    public Dialog ms(int ms) { this.ms = ms; return this; }

    public Dialog button(String id, String label) { return button(id, label, null, null); }

    /** {@code color} "#RRGGBB" and {@code icon} (an item id) may be null. A sixth button is the last; extra ones and bad ids are left out. */
    public Dialog button(String id, String label, String color, String icon) {
        String bid = Wire.id(id, 24);
        if (bid == null || buttonIds.size() >= MAX_BUTTONS || !buttonIds.add(bid)) return this;
        JsonObject b = new JsonObject();
        b.addProperty("id", bid);
        b.addProperty("label", Wire.text(label == null ? bid : label, 24));
        if (Wire.color(color, null) != null) b.addProperty("color", Wire.color(color, null));
        if (icon != null && Wire.resource(icon, 64) != null) b.addProperty("icon", icon);
        buttons.add(b);
        return this;
    }

    public String id() { return Wire.id(id, 32); }

    public int clampedMs() { return Wire.whole(ms, 1000, 60000, 15000); }

    public Set<String> buttonIds() { return Set.copyOf(buttonIds); }

    /** The open message, or null when the id is bad. */
    public JsonObject toJson() {
        String did = id();
        if (did == null) return null;
        JsonObject o = Wire.obj("dialog", "open");
        o.addProperty("id", did);
        o.addProperty("style", style);
        o.addProperty("title", Wire.text(title == null ? "" : title, 64));
        if (body != null) o.addProperty("body", Wire.text(body, 256));
        if (buttons.size() > 0) o.add("buttons", buttons.deepCopy());
        o.addProperty("ms", clampedMs());
        return o;
    }
}
