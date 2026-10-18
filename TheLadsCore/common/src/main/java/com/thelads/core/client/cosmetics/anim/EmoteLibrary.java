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
    public static final String WAVE = "lads:wave";
    public static final String CLAP = "lads:clap";
    public static final String FLOSS = "lads:floss";
    public static final String CHEER = "lads:cheer";
    public static final String BOW = "lads:bow";
    public static final String FACEPALM = "lads:facepalm";
    public static final String SALUTE = "lads:salute";
    public static final String SHRUG = "lads:shrug";
    /** The emote wheel's slot count (the player fills them in the Cosmetics screen: CosmeticsModule.slots). */
    public static final int SLOTS = 8;
    private static final Map<String, Emote> BY_ID = new LinkedHashMap<>();

    static {
        add(groove());
        add(wave());
        add(clap());
        add(floss());
        add(cheer());
        add(bow());
        add(facepalm());
        add(salute());
        add(shrug());
    }

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

    /** Wave: right arm raised high and out to the side, waving side to side, with gentle body sway. */
    static Emote wave() {
        Emote.Builder b = new Emote.Builder(WAVE, "Wave", 100, 4);
        // Right arm: nearly straight up (a little forward, so first person sees it), swinging out and back twice: a hand waved
        // overhead, never the forward 45-degree straight arm that reads as a different gesture.
        b.track(ch(RIGHT_ARM, RX), 0, -160, 1, -162, 2, -160, 3, -162);
        b.track(ch(RIGHT_ARM, RZ), 0, -12, 1, -42, 2, -12, 3, -42);
        // Left arm: relaxed down at the side with a slight outward flare.
        b.track(ch(LEFT_ARM, RZ), 0, -8, 2, -6);
        // Body: gentle lateral sway balancing the waving arm.
        b.track(ch(BODY, RZ), 0, -2.5f, 2, 2.5f);
        b.track(ROOT, 0, -0.4f, 2, 0.4f);
        // Head: slight tilt and turn toward the wave.
        b.track(ch(HEAD, RZ), 0, 3, 2, -3);
        b.track(ch(HEAD, RY), 0, -4, 2, 4);
        return b.build();
    }

    /** Clap: both arms forward, hands meeting in front of the chest on every beat with a small head nod. */
    static Emote clap() {
        Emote.Builder b = new Emote.Builder(CLAP, "Clap", 115, 4);
        // Both arms forward; hands meet in center on each beat, open apart on the half-beat.
        for (int k = 0; k < 4; k++) {
            float beat = k, open = k + 0.5f;
            b.key(ch(RIGHT_ARM, RX), beat, -82, Ease.OUT).key(ch(RIGHT_ARM, RX), open, -76, Ease.EASE);
            b.key(ch(LEFT_ARM, RX), beat, -82, Ease.OUT).key(ch(LEFT_ARM, RX), open, -76, Ease.EASE);
            b.key(ch(RIGHT_ARM, RY), beat, -28, Ease.OUT).key(ch(RIGHT_ARM, RY), open, -12, Ease.EASE);
            b.key(ch(LEFT_ARM, RY), beat, 28, Ease.OUT).key(ch(LEFT_ARM, RY), open, 12, Ease.EASE);
            b.key(ch(RIGHT_ARM, RZ), beat, -8, Ease.OUT).key(ch(RIGHT_ARM, RZ), open, -4, Ease.EASE);
            b.key(ch(LEFT_ARM, RZ), beat, 8, Ease.OUT).key(ch(LEFT_ARM, RZ), open, 4, Ease.EASE);
            b.key(ch(HEAD, RX), beat, 7, Ease.OUT).key(ch(HEAD, RX), open, -2, Ease.EASE);
            b.key(ROOT + 1, beat, 0.2f, Ease.OUT).key(ROOT + 1, open, -0.1f, Ease.EASE);
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

        // Front vs behind (RX), swapping every beat: the arm that crosses the body passes in front, the other swings out behind.
        b.track(ch(RIGHT_ARM, RX), 0, -45.0f, 1, 25.0f, 2, -45.0f, 3, 25.0f);
        b.track(ch(LEFT_ARM, RX),  0, 25.0f, 1, -45.0f, 2, 25.0f, 3, -45.0f);

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

    /** Cheer: both arms raised in a V, pumping on every beat, head up cheering, with a bouncy knee dip. */
    static Emote cheer() {
        Emote.Builder b = new Emote.Builder(CHEER, "Cheer", 120, 4).groundLock();
        // Arms in a V: pump high on the beat, pull down slightly between beats.
        for (int k = 0; k < 4; k++) {
            float up = k, down = k + 0.5f;
            b.key(ch(RIGHT_ARM, RX), up, -165, Ease.OUT).key(ch(RIGHT_ARM, RX), down, -132, Ease.EASE);
            b.key(ch(LEFT_ARM, RX), up, -165, Ease.OUT).key(ch(LEFT_ARM, RX), down, -132, Ease.EASE);
            b.key(ch(RIGHT_ARM, RZ), up, -30, Ease.OUT).key(ch(RIGHT_ARM, RZ), down, -22, Ease.EASE);
            b.key(ch(LEFT_ARM, RZ), up, 30, Ease.OUT).key(ch(LEFT_ARM, RZ), down, 22, Ease.EASE);
            b.key(ch(RIGHT_ARM, RY), up, 10, Ease.OUT).key(ch(RIGHT_ARM, RY), down, 8, Ease.EASE);
            b.key(ch(LEFT_ARM, RY), up, -10, Ease.OUT).key(ch(LEFT_ARM, RY), down, -8, Ease.EASE);
            // Legs dip forward on the downbeat, ground lock bounces the body.
            b.key(ch(RIGHT_LEG, RX), up, 0, Ease.OUT).key(ch(RIGHT_LEG, RX), down, -14, Ease.EASE);
            b.key(ch(LEFT_LEG, RX), up, 0, Ease.OUT).key(ch(LEFT_LEG, RX), down, -14, Ease.EASE);
            b.key(ROOT + 1, up, -0.4f, Ease.OUT).key(ROOT + 1, down, 0.6f, Ease.EASE);
        }
        b.track(ch(HEAD, RX), 0, -20, 0.5f, -16, 1, -20, 1.5f, -16, 2, -20, 2.5f, -16, 3, -20, 3.5f, -16);
        return b.build();
    }

    /** Bow: a slow polite bow pitching forward 38 degrees with right arm across the waist and left at side. */
    static Emote bow() {
        Emote.Builder b = new Emote.Builder(BOW, "Bow", 70, 4).groundLock();
        // Torso: bows down smoothly to 38 degrees at beat 2, pauses, and rises back up.
        b.key(ch(BODY, RX), 0, 0, Ease.EASE)
         .key(ch(BODY, RX), 1.5f, 36, Ease.SMOOTH)
         .key(ch(BODY, RX), 2.0f, 38, Ease.SMOOTH)
         .key(ch(BODY, RX), 2.5f, 36, Ease.SMOOTH)
         .key(ch(BODY, RX), 3.5f, 6, Ease.EASE);
        b.track(ROOT + 2, 0, 0, 2, 1.4f);
        // Right arm: moves across the waist during the bow, rests there, then returns.
        b.track(ch(RIGHT_ARM, RX), 0, 0, 1.5f, -32, 2, -35, 2.5f, -32, 3.5f, -5);
        b.track(ch(RIGHT_ARM, RY), 0, 0, 1.5f, -40, 2, -45, 2.5f, -40, 3.5f, -8);
        b.track(ch(RIGHT_ARM, RZ), 0, 0, 1.5f, -18, 2, -22, 2.5f, -18, 3.5f, -3);
        // Left arm: hangs slightly back along the hip.
        b.track(ch(LEFT_ARM, RX), 0, 0, 2, 8);
        b.track(ch(LEFT_ARM, RZ), 0, -4, 2, -6);
        // Head: stays aligned with the spine with a gentle polite nod at the bottom.
        b.track(ch(HEAD, RX), 0, 0, 2, 6);
        return b.build();
    }

    /** Facepalm: right hand held to the face with shoulders slumped and head slowly shaking in dismay. */
    static Emote facepalm() {
        Emote.Builder b = new Emote.Builder(FACEPALM, "Facepalm", 75, 4);
        // Torso: slumped forward.
        b.track(ch(BODY, RX), 0, 6, 2, 7);
        b.track(ROOT + 1, 0, 0.4f, 2, 0.5f);
        // Right arm: folded up in front of the head with hand against forehead.
        b.track(ch(RIGHT_ARM, RX), 0, -132, 2, -133);
        b.track(ch(RIGHT_ARM, RY), 0, -35, 2, -36);
        b.track(ch(RIGHT_ARM, RZ), 0, -28, 2, -29);
        // Head: tilted down and slowly shaking side to side.
        b.track(ch(HEAD, RX), 0, 6, 2, 8);
        b.track(ch(HEAD, RY), 0, 0, 1, -12, 2, 0, 3, 12);
        b.track(ch(HEAD, RZ), 0, 2, 1, -2, 2, 2, 3, -2);
        // Left arm: limp at the side.
        b.track(ch(LEFT_ARM, RX), 0, 6, 2, 8);
        b.track(ch(LEFT_ARM, RZ), 0, -5, 2, -6);
        return b.build();
    }

    /** Salute: crisp military salute with right hand to brow, elbow out, body upright holding breath. */
    static Emote salute() {
        Emote.Builder b = new Emote.Builder(SALUTE, "Salute", 80, 4);
        // Torso: upright at attention with a subtle breathing chest expansion.
        b.track(ch(BODY, RX), 0, -1.0f, 2, -1.8f);
        b.track(ROOT + 1, 0, 0, 2, -0.2f);
        // Right arm: raised with elbow out and hand touching brow.
        b.track(ch(RIGHT_ARM, RX), 0, -95, 2, -96);
        b.track(ch(RIGHT_ARM, RY), 0, -55, 2, -56);
        b.track(ch(RIGHT_ARM, RZ), 0, -45, 2, -46);
        // Left arm: pinned to the side seam.
        b.track(ch(LEFT_ARM, RX), 0, 1, 2, 1);
        b.track(ch(LEFT_ARM, RZ), 0, -3, 2, -3);
        // Head: held high and proud.
        b.track(ch(HEAD, RX), 0, -4, 2, -4);
        return b.build();
    }

    /** Shrug: both arms out low to sides palms up, shoulders lifting, and head tilting in a casual shrug. */
    static Emote shrug() {
        Emote.Builder b = new Emote.Builder(SHRUG, "Shrug", 85, 4);
        // Shoulders lift and drop: arms move out low to sides and lift upward at the peak.
        b.key(ch(BODY, RX), 0, 0, Ease.EASE)
         .key(ch(BODY, RX), 1.8f, -2, Ease.SMOOTH)
         .key(ch(BODY, RX), 3.4f, 0, Ease.EASE);
        b.track(ROOT + 1, 0, 0, 1.8f, -0.4f, 3.4f, 0);
        // Arms: lift out to the sides and elevate shoulders.
        b.key(ch(RIGHT_ARM, RX), 0, -12, Ease.EASE).key(ch(RIGHT_ARM, RX), 1.8f, -25, Ease.SMOOTH).key(ch(RIGHT_ARM, RX), 3.4f, -12, Ease.EASE);
        b.key(ch(LEFT_ARM, RX), 0, -12, Ease.EASE).key(ch(LEFT_ARM, RX), 1.8f, -25, Ease.SMOOTH).key(ch(LEFT_ARM, RX), 3.4f, -12, Ease.EASE);
        b.key(ch(RIGHT_ARM, RZ), 0, 12, Ease.EASE).key(ch(RIGHT_ARM, RZ), 1.8f, 42, Ease.SMOOTH).key(ch(RIGHT_ARM, RZ), 3.4f, 12, Ease.EASE);
        b.key(ch(LEFT_ARM, RZ), 0, -12, Ease.EASE).key(ch(LEFT_ARM, RZ), 1.8f, -42, Ease.SMOOTH).key(ch(LEFT_ARM, RZ), 3.4f, -12, Ease.EASE);
        b.key(ch(RIGHT_ARM, RY), 0, -8, Ease.EASE).key(ch(RIGHT_ARM, RY), 1.8f, -30, Ease.SMOOTH).key(ch(RIGHT_ARM, RY), 3.4f, -8, Ease.EASE);
        b.key(ch(LEFT_ARM, RY), 0, 8, Ease.EASE).key(ch(LEFT_ARM, RY), 1.8f, 30, Ease.SMOOTH).key(ch(LEFT_ARM, RY), 3.4f, 8, Ease.EASE);
        b.key(ch(RIGHT_ARM, Y), 0, 0, Ease.EASE).key(ch(RIGHT_ARM, Y), 1.8f, -2.0f, Ease.SMOOTH).key(ch(RIGHT_ARM, Y), 3.4f, 0, Ease.EASE);
        b.key(ch(LEFT_ARM, Y), 0, 0, Ease.EASE).key(ch(LEFT_ARM, Y), 1.8f, -2.0f, Ease.SMOOTH).key(ch(LEFT_ARM, Y), 3.4f, 0, Ease.EASE);
        // Head: tilts sideways with a quizzical expression.
        b.key(ch(HEAD, RZ), 0, 0, Ease.EASE).key(ch(HEAD, RZ), 1.8f, 16, Ease.SMOOTH).key(ch(HEAD, RZ), 3.4f, 0, Ease.EASE);
        b.key(ch(HEAD, RX), 0, 0, Ease.EASE).key(ch(HEAD, RX), 1.8f, -3, Ease.SMOOTH).key(ch(HEAD, RX), 3.4f, 0, Ease.EASE);
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
