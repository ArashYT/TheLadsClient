package com.thelads.core.client.cosmetics.anim;

import com.thelads.core.client.cosmetics.anim.Emote.Ease;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.thelads.core.client.cosmetics.anim.Emote.*;

/** The built-in emotes, in wheel order (the default wheel is the first SLOTS of them). */
public final class EmoteLibrary {
    public static final String GROOVE = "lads:groove";
    /** The emote wheel's slot count (the player fills them in the Cosmetics screen: CosmeticsModule.slots). */
    public static final int SLOTS = 8;
    private static final Map<String, Emote> BY_ID = new LinkedHashMap<>();

    static { add(groove()); }

    private EmoteLibrary() {}

    private static void add(Emote emote) { BY_ID.put(emote.id, emote); }

    public static Emote get(String id) { return id == null ? null : BY_ID.get(id); }

    /** Every emote id, in library order. */
    public static List<String> ids() { return new ArrayList<>(BY_ID.keySet()); }

    /**
     * Groove: a 4-beat step-touch at 115 BPM. Step right on 1, touch on 2, step left on 3, touch on 4; the knees dip on every beat
     * (both legs swing a little forward, the ground lock lowers the body), the hips sway and twist toward the step, the arms pump up in
     * front of the chest one after the other and the head nods on the beat.
     */
    static Emote groove() {
        Emote.Builder b = new Emote.Builder(GROOVE, "Groove", 115, 4).groundLock();
        // Hips: tilt and twist toward the stepping side, lean into each dip; the whole body shifts with the weight.
        b.track(ch(BODY, RZ), 0, 5, 1, 4, 2, -5, 3, -4);
        b.track(ch(BODY, RY), 0, 8, 1, 5, 2, -8, 3, -5);
        b.track(ch(BODY, RX), 0, 6, .5f, 1.5f, 1, 6, 1.5f, 1.5f, 2, 6, 2.5f, 1.5f, 3, 6, 3.5f, 1.5f);
        b.track(ROOT, 0, -1.1f, 1, -1.3f, 2, 1.1f, 3, 1.3f);
        b.track(ROOT + 1, 0, .9f, .5f, -.3f, 1, .9f, 1.5f, -.3f, 2, .9f, 2.5f, -.3f, 3, .9f, 3.5f, -.3f); // the bounce: down on the beat
        // Head: nods down on the beat, stays level against the hip tilt and twist.
        b.track(ch(HEAD, RX), 0, 9, .5f, -4, 1, 9, 1.5f, -4, 2, 9, 2.5f, -4, 3, 9, 3.5f, -4);
        b.track(ch(HEAD, RZ), 0, -4, 1, -3, 2, 4, 3, 3);
        b.track(ch(HEAD, RY), 0, -5, 1, -3, 2, 5, 3, 3);
        // Arms: one pumps up in front of the chest on each beat (fast up into the beat, easing back down).
        pump(b, RIGHT_ARM, 0, -1);
        pump(b, LEFT_ARM, 1, 1);
        // Legs: both swing a little forward on each beat (the dip); the stepping leg kicks out sideways before landing.
        for (int leg = RIGHT_LEG; leg <= LEFT_LEG; leg++)
            b.track(ch(leg, RX), 0, -11, .5f, -2, 1, -11, 1.5f, -2, 2, -11, 2.5f, -2, 3, -11, 3.5f, -2);
        b.track(ch(RIGHT_LEG, RZ), 3.5f, 17, 0, 6, 1, 3, 2, 1, 2.5f, -1, 3, -2);
        b.track(ch(LEFT_LEG, RZ), 1.5f, -17, 2, -6, 3, -3, 0, -1, .5f, 1, 1, 2);
        return b.build();
    }

    /** An arm up at {@code upBeat} and {@code upBeat} + 2, down between; {@code side} -1 right, 1 left (mirrors yaw and roll). */
    private static void pump(Emote.Builder b, int arm, float upBeat, int side) {
        for (int k = 0; k < 2; k++) {
            float up = upBeat + 2 * k, down = up + 1;
            b.key(ch(arm, RX), up, -110, Ease.EASE).key(ch(arm, RX), down, -42, Ease.OUT);
            b.key(ch(arm, RY), up, 16 * side, Ease.EASE).key(ch(arm, RY), down, 8 * side, Ease.OUT);
            b.key(ch(arm, RZ), up, 2 * side, Ease.EASE).key(ch(arm, RZ), down, -8 * side, Ease.OUT);
        }
    }
}
