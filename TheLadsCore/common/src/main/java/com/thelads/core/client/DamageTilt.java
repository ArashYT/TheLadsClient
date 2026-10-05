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
    /** A hurt shows for 10 ticks (500 ms); a later health update can restart it, so the direction stays a little longer. */
    static final long KEEP_MS = 1000;

    private long hurtAt = Long.MIN_VALUE / 2, directionAt = Long.MIN_VALUE / 2;
    private float direction = Float.NaN, yaw = Float.NaN;

    /** The server reported the player hurt (1.8.9 entity status 2, 26.x damage event or hurt animation). */
    public void hurt(long nowMs) {
        hurtAt = nowMs;
        yaw = nowMs - directionAt <= PAIR_MS ? direction : Float.NaN;
    }

    /** The hit's direction as a relative yaw (NaN: none, ignored). */
    public void direction(float relativeYaw, long nowMs) {
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
        return Float.isNaN(known) || !(module.getOption(DIRECTIONAL) instanceof BoolOption on && on.get()) ? 0 : known;
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
