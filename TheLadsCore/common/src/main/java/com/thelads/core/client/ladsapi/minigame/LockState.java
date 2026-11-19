package com.thelads.core.client.ladsapi.minigame;

import com.google.gson.JsonObject;
import java.util.EnumSet;
import java.util.Set;

/**
 * Input lock (PROTOCOL.md section 13): only removes input the player could give. There is no entry for Esc, the pause menu, chat,
 * commands, the Lads GUI key or disconnecting, so they can never be locked. Version hooks ask {@code Minigame.locked(Lock.X)}.
 */
public final class LockState {
    public enum Lock { MOVE, JUMP, SNEAK, SPRINT, LOOK, ATTACK, USE, DROP, HOTBAR, INVENTORY }

    private Set<Lock> set = EnumSet.noneOf(Lock.class);
    private long until;

    public void clear() {
        set = EnumSet.noneOf(Lock.class);
        until = 0;
    }

    public void apply(JsonObject json, long now) {
        Double ms = Wire.num(json, "ms");
        Set<Lock> next = EnumSet.noneOf(Lock.class);
        for (Lock l : Lock.values()) {
            if (Wire.flag(json, l.name().toLowerCase(java.util.Locale.ROOT))) next.add(l);
        }
        if (ms == null || next.isEmpty()) {
            clear();
            return;
        }
        set = next;
        until = now + Math.round(Math.max(1, Math.min(15000, ms)));
    }

    public boolean locked(Lock l, long now) {
        return now < until && set.contains(l);
    }

    public boolean any(long now) {
        return now < until && !set.isEmpty();
    }
}
