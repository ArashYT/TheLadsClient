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
     * Wave: a friendly wave with the right arm raised high beside the head (2 s loop, 4 beats at 120 BPM).
     * The right arm is raised close to vertical (RX ~ -170°) with a clear ~50° side-to-side sweeping amplitude
     * (2 waves per beat cycle, alternating every 0.25 beats between outward -48° and inward +2°).
     * The torso and head gently lean toward the waving side with feet planted on the ground.
     */
    static Emote wave() {
        Emote.Builder b = new Emote.Builder(WAVE, "Wave", 120, 4).groundLock();
        // Right arm: raised high beside the head (close to vertical RX ~ -170°), hand sweeping side-to-side across ~50°
        // 2 waves per beat cycle (16 half-swings across 4 beats, alternating every 0.25 beats between outward -48° and inward +2°).
        for (int k = 0; k < 16; k++) {
            float beat = k * 0.25f;
            boolean out = (k % 2 == 0);
            float rz = out ? -48.0f : 2.0f;
            float ry = out ? -14.0f : 8.0f;
            float rx = out ? -172.0f : -168.0f;
            b.key(ch(RIGHT_ARM, RX), beat, rx);
            b.key(ch(RIGHT_ARM, RZ), beat, rz);
            b.key(ch(RIGHT_ARM, RY), beat, ry);
        }
        // Left arm: relaxed at side with subtle natural motion.
        b.track(ch(LEFT_ARM, RZ), 0, -8, 2, -6);
        b.track(ch(LEFT_ARM, RX), 0, 2, 2, 6);
        // Torso: gentle rhythmic lean toward the waving side (lean right = positive RZ).
        b.track(ch(BODY, RZ), 0, 3.5f, 2, 1.5f);
        b.track(ch(BODY, RY), 0, -3.5f, 2, -1.5f);
        b.track(ROOT, 0, -0.6f, 2, -0.2f);
        b.track(ROOT + 1, 0, 0.1f, 1, -0.1f, 2, 0.1f, 3, -0.1f);
        // Head: friendly tilt toward the waving side.
        b.track(ch(HEAD, RZ), 0, -4.5f, 2, -2.5f);
        b.track(ch(HEAD, RY), 0, -4.0f, 2, -2.0f);
        b.track(ch(HEAD, RX), 0, -2.0f, 2, 1.0f);
        // Legs: grounded stance supporting the lean.
        b.track(ch(RIGHT_LEG, RZ), 0, 2.5f, 2, 1.0f);
        b.track(ch(LEFT_LEG, RZ),  0, 1.0f, 2, -1.0f);
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
     * which arm passes in front and behind the body with continuous momentum. Feet stay planted on the ground.
     */
    static Emote floss() {
        Emote.Builder b = new Emote.Builder(FLOSS, "Floss", 120, 4).groundLock();
        // Hips / Torso: decisive, energetic hip swing opposite to arms.
        b.track(ch(BODY, RZ), 0, 11.0f, 1, -11.0f, 2, 11.0f, 3, -11.0f);
        b.track(ch(BODY, RY), 0, -8.0f, 1, 8.0f, 2, -8.0f, 3, 8.0f);
        b.track(ch(BODY, RX), 0, 4.0f, 1, 4.0f, 2, 4.0f, 3, 4.0f);
        b.track(ROOT, 0, -2.4f, 1, 2.4f, 2, -2.4f, 3, 2.4f);
        // Subtle vertical dip at center of swing
        b.track(ROOT + 1, 0, -0.3f, 0.5f, 0.4f, 1, -0.3f, 1.5f, 0.4f, 2, -0.3f, 2.5f, 0.4f, 3, -0.3f, 3.5f, 0.4f);

        // Head: stays upright, looking straight ahead at the viewer.
        b.track(ch(HEAD, RZ), 0, -8.0f, 1, 8.0f, 2, -8.0f, 3, 8.0f);
        b.track(ch(HEAD, RY), 0, 7.0f, 1, -7.0f, 2, 7.0f, 3, -7.0f);
        b.track(ch(HEAD, RX), 0, 2.0f, 1, 2.0f, 2, 2.0f, 3, 2.0f);

        // Arm side-to-side swing (RZ):
        b.track(ch(RIGHT_ARM, RZ), 0, -50.0f, 1, 36.0f, 2, -50.0f, 3, 36.0f);
        b.track(ch(LEFT_ARM, RZ),  0, -36.0f, 1, 50.0f, 2, -36.0f, 3, 50.0f);

        // Front vs Behind (RX):
        b.track(ch(RIGHT_ARM, RX), 0, -50.0f, 1, -50.0f, 2, 22.0f, 3, 22.0f);
        b.track(ch(LEFT_ARM, RX),  0, 22.0f,  1, -50.0f, 2, -50.0f, 3, 22.0f);

        // Arm yaw (RY):
        b.track(ch(RIGHT_ARM, RY), 0, 18.0f, 1, -18.0f, 2, 18.0f, 3, -18.0f);
        b.track(ch(LEFT_ARM, RY),  0, 18.0f, 1, -18.0f, 2, 18.0f, 3, -18.0f);

        // Legs: wide stance, weight shifting with hip sway.
        b.track(ch(RIGHT_LEG, RZ), 0, 7.0f, 1, -3.0f, 2, 7.0f, 3, -3.0f);
        b.track(ch(LEFT_LEG, RZ),  0, 3.0f, 1, -7.0f, 2, 3.0f, 3, -7.0f);
        b.track(ch(RIGHT_LEG, RX), 0, -8.0f, 1, -2.0f, 2, -8.0f, 3, -2.0f);
        b.track(ch(LEFT_LEG, RX),  0, -2.0f, 1, -8.0f, 2, -2.0f, 3, -8.0f);

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
