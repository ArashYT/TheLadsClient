package com.thelads.core.client.cosmetics.anim;

import com.thelads.core.client.cosmetics.anim.Emote.Ease;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

import static com.thelads.core.client.cosmetics.anim.Emote.*;

/** The built-in emotes and the emote wheel's slots. */
public final class EmoteLibrary {
    public static final String GROOVE = "lads:groove";
    public static final String WAVE = "lads:wave";
    public static final String CLAP = "lads:clap";
    public static final String FLOSS = "lads:floss";
    /** The wheel's eight slots, clockwise from the top; null = empty. */
    public static final String[] WHEEL = {GROOVE, WAVE, CLAP, FLOSS, null, null, null, null};
    private static final Map<String, Emote> BY_ID = new LinkedHashMap<>();

    static {
        add(groove());
        add(wave());
        add(clap());
        add(floss());
    }

    private EmoteLibrary() {}

    private static void add(Emote emote) { BY_ID.put(emote.id, emote); }

    public static Emote get(String id) { return id == null ? null : BY_ID.get(id); }

    public static Collection<Emote> all() { return BY_ID.values(); }

    /** Ownership check: all built-in emotes are owned by everyone for now. */
    public static boolean owns(String id) { return id != null && BY_ID.containsKey(id); }

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

    /**
     * Wave: a friendly wave overhead with the right arm (2 s loop, 4 beats at 120 BPM).
     * The right arm is raised high overhead and slightly forward (visible in first-person view) and waves side-to-side twice;
     * the torso gently sways to balance the arm with a pleasant head tilt. Feet stay planted on the ground.
     */
    static Emote wave() {
        Emote.Builder b = new Emote.Builder(WAVE, "Wave", 120, 4).groundLock();
        // Right arm: raised high overhead and slightly forward so first-person sees it, waving back and forth.
        b.track(ch(RIGHT_ARM, RX), 0, -152, 1, -156, 2, -152, 3, -156);
        b.track(ch(RIGHT_ARM, RZ), 0, -16, 1, -44, 2, -16, 3, -44);
        b.track(ch(RIGHT_ARM, RY), 0, -14, 1, -28, 2, -14, 3, -28);
        // Left arm: relaxed down at the side with subtle natural motion.
        b.track(ch(LEFT_ARM, RZ), 0, -8, 2, -6);
        b.track(ch(LEFT_ARM, RX), 0, 2, 2, 6);
        // Torso: gentle rhythmic sway balancing the waving arm.
        b.track(ch(BODY, RZ), 0, -2.5f, 2, 2.5f);
        b.track(ch(BODY, RY), 0, -3.0f, 2, 3.0f);
        b.track(ROOT, 0, -0.4f, 2, 0.4f);
        b.track(ROOT + 1, 0, 0.2f, 1, -0.1f, 2, 0.2f, 3, -0.1f);
        // Head: friendly slight tilt and turn toward the wave.
        b.track(ch(HEAD, RZ), 0, 3, 2, -3);
        b.track(ch(HEAD, RY), 0, -4, 2, 4);
        b.track(ch(HEAD, RX), 0, -2, 2, 3);
        // Legs: gentle weight shift to ground the sway.
        b.track(ch(RIGHT_LEG, RZ), 0, 2, 2, -1);
        b.track(ch(LEFT_LEG, RZ), 0, 1, 2, -2);
        return b.build();
    }

    /**
     * Clap: rhythmic clapping in front of the chest at 120 BPM (4 beats, 4 claps).
     * Both hands meet at the center on each beat and bounce apart on the half-beat.
     * The head nods in time with each clap, and the torso bounces slightly while feet stay grounded.
     */
    static Emote clap() {
        Emote.Builder b = new Emote.Builder(CLAP, "Clap", 120, 4).groundLock();
        for (int k = 0; k < 4; k++) {
            float beat = k, open = k + 0.5f;
            b.key(ch(RIGHT_ARM, RX), beat, -88, Ease.OUT).key(ch(RIGHT_ARM, RX), open, -82, Ease.EASE);
            b.key(ch(LEFT_ARM, RX), beat, -88, Ease.OUT).key(ch(LEFT_ARM, RX), open, -82, Ease.EASE);
            b.key(ch(RIGHT_ARM, RY), beat, -32, Ease.OUT).key(ch(RIGHT_ARM, RY), open, -14, Ease.EASE);
            b.key(ch(LEFT_ARM, RY), beat, 32, Ease.OUT).key(ch(LEFT_ARM, RY), open, 14, Ease.EASE);
            b.key(ch(RIGHT_ARM, RZ), beat, -6, Ease.OUT).key(ch(RIGHT_ARM, RZ), open, -2, Ease.EASE);
            b.key(ch(LEFT_ARM, RZ), beat, 6, Ease.OUT).key(ch(LEFT_ARM, RZ), open, 2, Ease.EASE);
            b.key(ch(HEAD, RX), beat, 7, Ease.OUT).key(ch(HEAD, RX), open, -2, Ease.EASE);
            b.key(ch(BODY, RX), beat, 3, Ease.OUT).key(ch(BODY, RX), open, 0, Ease.EASE);
            b.key(ROOT + 1, beat, 0.25f, Ease.OUT).key(ROOT + 1, open, -0.1f, Ease.EASE);
            b.key(ch(RIGHT_LEG, RX), beat, -3, Ease.OUT).key(ch(RIGHT_LEG, RX), open, -0.5f, Ease.EASE);
            b.key(ch(LEFT_LEG, RX), beat, -3, Ease.OUT).key(ch(LEFT_LEG, RX), open, -0.5f, Ease.EASE);
        }
        return b.build();
    }

    /**
     * Floss: classic arm swing side to side with hip counter-motion at 120 BPM (4 beats).
     * On each beat, both arms swing to one side while hips thrust to the opposite side, alternating
     * which arm passes in front and behind the body. Feet stay planted on the ground.
     */
    static Emote floss() {
        Emote.Builder b = new Emote.Builder(FLOSS, "Floss", 120, 4).groundLock();
        // Hips / Torso: sway side to side opposite to arm direction, with a bounce at center.
        b.track(ch(BODY, RZ), 0, -7, .5f, 0, 1, 7, 1.5f, 0, 2, -7, 2.5f, 0, 3, 7, 3.5f, 0);
        b.track(ch(BODY, RY), 0, 8, .5f, 0, 1, -8, 1.5f, 0, 2, 8, 2.5f, 0, 3, -8, 3.5f, 0);
        b.track(ROOT, 0, -1.6f, .5f, 0, 1, 1.6f, 1.5f, 0, 2, -1.6f, 2.5f, 0, 3, 1.6f, 3.5f, 0);
        b.track(ROOT + 1, 0, -0.2f, .5f, 0.4f, 1, -0.2f, 1.5f, 0.4f, 2, -0.2f, 2.5f, 0.4f, 3, -0.2f, 3.5f, 0.4f);

        // Head: counter-balances the torso sway so the eyes stay oriented forward.
        b.track(ch(HEAD, RZ), 0, 5, .5f, 0, 1, -5, 1.5f, 0, 2, 5, 2.5f, 0, 3, -5, 3.5f, 0);
        b.track(ch(HEAD, RY), 0, -6, .5f, 0, 1, 6, 1.5f, 0, 2, -6, 2.5f, 0, 3, 6, 3.5f, 0);
        b.track(ch(HEAD, RX), 0, 4, .5f, 0, 1, 4, 1.5f, 0, 2, 4, 2.5f, 0, 3, 4, 3.5f, 0);

        // Arms: both swing side to side opposite to hips.
        // Beat 0: arms LEFT (R in front, L behind).
        // Beat 1: arms RIGHT (L in front, R behind).
        // Beat 2: arms LEFT (L in front, R behind).
        // Beat 3: arms RIGHT (R in front, L behind).
        b.track(ch(RIGHT_ARM, RX), 0, -32, .5f, 0, 1, 22, 1.5f, 0, 2, 22, 2.5f, 0, 3, -32, 3.5f, 0);
        b.track(ch(RIGHT_ARM, RY), 0, 28, .5f, 0, 1, -24, 1.5f, 0, 2, 24, 2.5f, 0, 3, -28, 3.5f, 0);
        b.track(ch(RIGHT_ARM, RZ), 0, -44, .5f, 4, 1, 34, 1.5f, 4, 2, -34, 2.5f, 4, 3, 44, 3.5f, 4);

        b.track(ch(LEFT_ARM, RX), 0, 22, .5f, 0, 1, -32, 1.5f, 0, 2, -32, 2.5f, 0, 3, 22, 3.5f, 0);
        b.track(ch(LEFT_ARM, RY), 0, 24, .5f, 0, 1, -28, 1.5f, 0, 2, 28, 2.5f, 0, 3, -24, 3.5f, 0);
        b.track(ch(LEFT_ARM, RZ), 0, -34, .5f, -4, 1, 44, 1.5f, -4, 2, -44, 2.5f, -4, 3, 34, 3.5f, -4);

        // Legs: weight shifting and knee action to ground the movement.
        b.track(ch(RIGHT_LEG, RX), 0, -8, .5f, -4, 1, -2, 1.5f, -4, 2, -8, 2.5f, -4, 3, -2, 3.5f, -4);
        b.track(ch(RIGHT_LEG, RZ), 0, 4, .5f, 0, 1, -2, 1.5f, 0, 2, 4, 2.5f, 0, 3, -2, 3.5f, 0);
        b.track(ch(LEFT_LEG, RX), 0, -2, .5f, -4, 1, -8, 1.5f, -4, 2, -2, 2.5f, -4, 3, -8, 3.5f, -4);
        b.track(ch(LEFT_LEG, RZ), 0, 2, .5f, 0, 1, -4, 1.5f, 0, 2, 2, 2.5f, 0, 3, -4, 3.5f, 0);

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
