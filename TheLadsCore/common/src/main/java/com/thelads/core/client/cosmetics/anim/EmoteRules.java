package com.thelads.core.client.cosmetics.anim;

import com.thelads.core.client.cosmetics.Emotes;
import com.thelads.core.modules.CosmeticsModule;
import java.util.UUID;

/**
 * When the local player's emote stops (blends out): moving, jumping, sneaking, attacking, using an item, taking damage, dying,
 * riding/swimming/flying/sleeping, changing dimension, or being moved more than MOVE blocks from where the emote started.
 * Also monitors idle ticks to automatically trigger AFK auto-emote when enabled in CosmeticsModule.
 * Each version fills {@link #in} once per client tick and calls {@link #tick}.
 */
public final class EmoteRules {
    static final double MOVE = 0.3;

    /** This tick's local player state (one shared instance: no allocation). */
    public static final class Input {
        public boolean moving, jumping, sneaking, attacking, usingItem, dead, busy;
        public int hurtTime;
        public Object level;
        public double x, y, z;
    }

    public static final Input in = new Input();
    private static Object lastLevel;
    private static int lastHurtTime;
    private static long anchoredStart = Long.MIN_VALUE;
    private static double ax, ay, az;
    private static int idleTicks;

    private EmoteRules() {}

    public static int idleTicks() { return idleTicks; }
    public static void resetIdle() { idleTicks = 0; }

    /** Stops the local player's emote when a rule fires; triggers AFK auto-emote if idle; returns reason or null. */
    public static String tick(UUID self) {
        Input i = in;
        boolean levelChanged = lastLevel != null && i.level != lastLevel, hurt = i.hurtTime > lastHurtTime;
        lastLevel = i.level;
        lastHurtTime = i.hurtTime;

        boolean activeInput = i.moving || i.jumping || i.sneaking || i.attacking || i.usingItem || hurt || i.dead || i.busy || levelChanged;
        if (activeInput) {
            idleTicks = 0;
        } else {
            idleTicks++;
        }

        Emotes.Playing playing = self == null ? null : Emotes.get(self);
        if (playing == null || playing.stopping()) {
            if (!activeInput && self != null && !i.dead && !i.busy) {
                CosmeticsModule mod = CosmeticsModule.get();
                if (mod.afkEmote.get()) {
                    int timeoutTicks = (int) (mod.afkTimeout.getValue() * 20);
                    if (idleTicks >= timeoutTicks) {
                        Emotes.playLocal(self, EmoteLibrary.SHRUG);
                        idleTicks = 0;
                    }
                }
            }
            return null;
        }

        if (anchoredStart != playing.startMs()) {
            anchoredStart = playing.startMs();
            ax = i.x; ay = i.y; az = i.z;
        }
        double dx = i.x - ax, dy = i.y - ay, dz = i.z - az;
        String reason = i.moving ? "moving" : i.jumping ? "jumping" : i.sneaking ? "sneaking" : i.attacking ? "attacking"
            : i.usingItem ? "using an item" : hurt ? "hurt" : i.dead ? "dead" : i.busy ? "busy" : levelChanged ? "dimension"
            : dx * dx + dy * dy + dz * dz > MOVE * MOVE ? "moved" : null;
        if (reason != null) {
            Emotes.stopLocal(self);
            idleTicks = 0;
        }
        return reason;
    }
}
