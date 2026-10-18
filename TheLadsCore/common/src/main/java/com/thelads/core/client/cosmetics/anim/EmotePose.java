package com.thelads.core.client.cosmetics.anim;

import static com.thelads.core.client.cosmetics.anim.Emote.*;

/**
 * One player's emote pose this frame (EmoteAnimator reuses one per player) and how much of it shows: weight 0 = vanilla, 1 = the
 * emote. Each version reads its parts through these helpers after vanilla (and every other mod) posed the model.
 */
public final class EmotePose {
    public final float[] v = new float[CHANNELS];
    public float weight;
    public Emote emote;

    /** A part's rotation: the head adds the emote to where the player looks, every other part blends vanilla into the emote. */
    public float rot(int part, int axis, float vanilla) {
        float e = v[ch(part, axis)];
        return part == HEAD ? vanilla + e * weight : vanilla + (e - vanilla) * weight;
    }

    /** A part's pivot: vanilla's plus the emote's offset. */
    public float pos(int part, int axis, float vanilla) { return vanilla + v[ch(part, axis + X)] * weight; }

    /** The whole body's offset in pixels (axis 0..2). */
    public float root(int axis) { return v[ROOT + axis] * weight; }

    /** First person: the arm's rotation blended from hanging at the side (out of view) into the emote. */
    public float armRot(int part, int axis) {
        float rest = axis == RZ ? (part == RIGHT_ARM ? 0.06f : -0.06f) : 0;
        return rest + (v[ch(part, axis)] - rest) * weight;
    }

    /** First person: the arm's pivot (pixels), rest pivot plus the emote's offset. */
    public float armPos(int part, int axis) { return rest(part, axis) + v[ch(part, axis + X)] * weight; }

    /** First person shows emote arms: the emote moves an arm and still shows. */
    public boolean firstPersonArms() { return weight > 0 && emote != null && (emote.rightArm || emote.leftArm); }
}
