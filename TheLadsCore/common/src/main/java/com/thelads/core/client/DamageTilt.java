package com.thelads.core.client;

import com.thelads.core.config.BoolOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.SliderOption;

/**
 * Directional damage tilt (the OldDamageTilt module). Minecraft tilts the hurt camera by R_y(-yaw) R_z(-14° x hurt curve x strength)
 * R_y(yaw), where yaw is the hit's direction relative to where the player faces (1.8.9 attackedAtYaw, 26.x hurtDir): atan2(dz, dx)
 * towards the source, minus the player's yaw. Yaw 0 is the tilt of a hit from the left, which 1.8.9 always shows (its client never
 * learns the direction) and which both versions show here when the direction is unknown (fall damage, fire, poison).
 *
 * <p>The adapters feed one tracker from their packet handlers: each hurt, and each hit direction (26.x: the server's hurt animation
 * yaw; 1.8.9: {@link #sourceYaw} of the knockback velocity the server sends in the same tick). A direction counts for a hurt that
 * arrives within {@link #PAIR_MS} of it, in either order; the camera hooks ask {@link #cameraYaw} and {@link #strength}.
 */
public final class DamageTilt {
    public static final String MODULE = "OldDamageTilt", DIRECTIONAL = "Directional", INTENSITY = "Intensity";
    /** The local player's tracker; packets and the camera both run on the client thread. */
    public static final DamageTilt CLIENT = new DamageTilt();
    /** Horizontal knockback slower than this (blocks a tick) is no hit's push: vanilla's is 0.4 away from the attacker. */
    static final double MIN_PUSH = 0.1;
    /** Hurt and its direction are sent in the same server tick. */
    static final long PAIR_MS = 150;
    /** How long a hurt holds its tilt while no answer about its direction came (a server tick and a little). */
    static final long WAIT_MS = 60;
    /** A hurt shows for 10 ticks (500 ms); a later health update can restart it, so the direction stays a little longer. */
    static final long KEEP_MS = 1000;

    private long hurtAt = Long.MIN_VALUE / 2, directionAt = Long.MIN_VALUE / 2, answerAt = Long.MIN_VALUE / 2;
    private float direction = Float.NaN, yaw = Float.NaN;

    /** The server reported the player hurt (1.8.9 entity status 2, 26.x damage event or hurt animation). */
    public void hurt(long nowMs) {
        hurtAt = nowMs;
        yaw = nowMs - directionAt <= PAIR_MS ? direction : Float.NaN;
    }

    /**
     * The hit's direction as a relative yaw, or NaN for an answer without one (1.8.9: the velocity packet of fall or fire damage;
     * 26.x: the damage event, whose hit direction comes in the same batch or never).
     */
    public void direction(float relativeYaw, long nowMs) {
        answerAt = nowMs;
        if (Float.isNaN(relativeYaw)) return;
        direction = relativeYaw;
        directionAt = nowMs;
        if (nowMs - hurtAt <= PAIR_MS) yaw = relativeYaw;
    }

    /** The current hurt's direction, or NaN when it is unknown or no hurt shows. */
    public float yaw(long nowMs) {
        return nowMs - hurtAt <= KEEP_MS ? yaw : Float.NaN;
    }

    /** The yaw the camera turns by with the module on: the hit's direction while Directional is on and it is known, else 0. */
    public float cameraYaw(Module module, long nowMs) {
        float known = yaw(nowMs);
        return Float.isNaN(known) || !directional(module) ? 0 : known;
    }

    /**
     * The factor on the tilt with the module on: Intensity, or 0 while a fresh hurt still waits for its answer (Directional on), so
     * the camera never starts leaning the fixed way and then swings round. 1.8.9 can send the knockback a little after the hurt.
     */
    public float cameraStrength(Module module, long nowMs) {
        boolean waiting = Float.isNaN(yaw) && hurtAt - answerAt > PAIR_MS && nowMs - hurtAt < WAIT_MS && directional(module);
        return waiting ? 0 : strength(module);
    }

    /** QA: milliseconds from the last hurt to its direction (negative: the direction came first); Long.MIN_VALUE when none paired. */
    public long pairDelay() {
        return Float.isNaN(yaw) ? Long.MIN_VALUE : directionAt - hurtAt;
    }

    private static boolean directional(Module module) {
        return module.getOption(DIRECTIONAL) instanceof BoolOption on && on.get();
    }

    /**
     * A knockback velocity's source as a relative yaw: the push points away from the attacker, so the source lies opposite.
     * NaN when the push is too weak to be a hit's.
     */
    public static float sourceYaw(double motionX, double motionZ, float playerYaw) {
        if (motionX * motionX + motionZ * motionZ < MIN_PUSH * MIN_PUSH) return Float.NaN;
        return (float) Math.toDegrees(Math.atan2(-motionZ, -motionX)) - playerYaw;
    }

    /** The module's Intensity (0 to 100%) as a factor on the tilt; 1 when the option is missing. */
    public static float strength(Module module) {
        return module.getOption(INTENSITY) instanceof SliderOption intensity ? scale(intensity.getValue()) : 1;
    }

    static float scale(double percent) {
        return (float) Math.max(0, Math.min(100, percent)) / 100;
    }
}
