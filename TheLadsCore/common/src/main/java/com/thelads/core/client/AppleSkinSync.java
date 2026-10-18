package com.thelads.core.client;

import java.nio.ByteBuffer;

/**
 * What a server running AppleSkin's server side sends its players, so the AppleSkin module can show exact values there. The
 * channels and their layout are AppleSkin's (an interoperability fact): {@code appleskin:saturation} and
 * {@code appleskin:exhaustion} carry one big-endian float, {@code appleskin:natural_regeneration} one boolean byte (the game rule).
 * The adapters read a payload's bytes and decode them here; a value that cannot be the player's is dropped. The channels are never
 * announced (no minecraft:register): AppleSkin's server side sends to every player without asking, so the adapters pick the
 * payloads off vanilla's unknown-payload path and no other server learns the client reads them.
 */
public final class AppleSkinSync {
    public static final String NAMESPACE = "appleskin";
    public static final String SATURATION = "saturation", EXHAUSTION = "exhaustion", NATURAL_REGENERATION = "natural_regeneration";
    /** Saturation never exceeds the hunger level (20); vanilla caps exhaustion at 40. */
    public static final float MAX_SATURATION = 20, MAX_EXHAUSTION = 40;
    private AppleSkinSync() {}

    /** The channel constant a payload id names ({@code appleskin:saturation} -> {@link #SATURATION}), or null when it is not one. */
    public static String channel(String namespace, String path) {
        if (!NAMESPACE.equals(namespace)) return null;
        for (String channel : new String[] {SATURATION, EXHAUSTION, NATURAL_REGENERATION}) if (channel.equals(path)) return channel;
        return null;
    }

    /** A saturation or exhaustion payload: its float, or NaN when it is too short or out of 0..{@code max}. Extra bytes are ignored. */
    public static float decodeFloat(byte[] payload, float max) {
        if (payload == null || payload.length < Float.BYTES) return Float.NaN;
        float value = ByteBuffer.wrap(payload).getFloat();
        return value >= 0 && value <= max ? value : Float.NaN; // NaN fails both comparisons
    }

    /** A natural_regeneration payload: the game rule, or null when the payload is empty. */
    public static Boolean decodeBoolean(byte[] payload) {
        return payload == null || payload.length == 0 ? null : payload[0] != 0;
    }

    /** The bytes a server sends for a float value (QA injects these). */
    public static byte[] encodeFloat(float value) {
        return ByteBuffer.allocate(Float.BYTES).putFloat(value).array();
    }
}
