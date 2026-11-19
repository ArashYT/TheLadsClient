package com.thelads.ladsapi.api;

import com.google.gson.JsonObject;

/** Which inputs to lock (PROTOCOL.md section 13). Esc, chat and disconnecting are never locked by the client. */
public final class InputLock {
    private boolean move, jump, sneak, sprint, look, attack, use, drop, hotbar, inventory;

    private InputLock() {}

    public static InputLock create() { return new InputLock(); }

    /** Everything a countdown needs: all inputs except looking around. */
    public static InputLock all() {
        return new InputLock().move().jump().sneak().sprint().attack().use().drop().hotbar().inventory();
    }

    public InputLock move() { move = true; return this; }
    public InputLock jump() { jump = true; return this; }
    public InputLock sneak() { sneak = true; return this; }
    public InputLock sprint() { sprint = true; return this; }
    public InputLock look() { look = true; return this; }
    public InputLock attack() { attack = true; return this; }
    public InputLock use() { use = true; return this; }
    public InputLock drop() { drop = true; return this; }
    public InputLock hotbar() { hotbar = true; return this; }
    public InputLock inventory() { inventory = true; return this; }

    /** The lock message, or null when nothing is locked. {@code ms} is clamped to 1..15000. */
    public JsonObject toJson(int ms) {
        if (!(move || jump || sneak || sprint || look || attack || use || drop || hotbar || inventory)) return null;
        JsonObject o = new JsonObject();
        o.addProperty("t", "lock");
        o.addProperty("ms", Math.max(1, Math.min(15000, ms)));
        flag(o, "move", move);
        flag(o, "jump", jump);
        flag(o, "sneak", sneak);
        flag(o, "sprint", sprint);
        flag(o, "look", look);
        flag(o, "attack", attack);
        flag(o, "use", use);
        flag(o, "drop", drop);
        flag(o, "hotbar", hotbar);
        flag(o, "inventory", inventory);
        return o;
    }

    private static void flag(JsonObject o, String k, boolean v) { if (v) o.addProperty(k, true); }
}
