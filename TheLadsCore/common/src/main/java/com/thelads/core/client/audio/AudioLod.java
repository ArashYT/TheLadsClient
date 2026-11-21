package com.thelads.core.client.audio;

/**
 * Audio level of detail. OpenAL linear attenuation (vanilla: reference distance 1, max distance = range) gives
 * gain(d) = g * (1 - (d - 1) / (range - 1)); a sound whose gain would be under 2 % is inaudible, so its source is never started.
 */
public final class AudioLod {
    public static final double AUDIBLE = 0.02;

    private AudioLod() {}

    /** Distance beyond which a sound of final gain {@code gain} and attenuation range {@code range} is under 2 %; infinity if it is never checked. */
    public static double cutoff(double gain, double range) {
        if (gain <= AUDIBLE || range <= 1.0) return Double.POSITIVE_INFINITY;
        return 1.0 + (range - 1.0) * (1.0 - AUDIBLE / gain);
    }

    /** True when the sound is farther than {@code factor} times its inaudible distance (factor 1 = exactly the 2 % point). */
    public static boolean cull(double dist, double gain, double range, double factor) {
        return dist > cutoff(gain, range) * factor;
    }
}
