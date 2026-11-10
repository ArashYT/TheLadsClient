package com.thelads.core.client.cosmetics.anim;

/**
 * Jiggle physics for one emote channel: a damped spring, kicked at fixed times of the loop. A kick is an impulse, so the spring
 * swings out, overshoots its rest and rings down: e^(-decay t) sin(ring t) times the kick. The emote loops, so what shows is the
 * steady state, the ring of every kick of this loop and of all the loops before it, summed in closed form (a geometric series).
 * No state and no allocation: any time samples straight, the same on every client, and the loop is seamless.
 */
final class Jiggle {
    private final float periodMs;
    private final double decay, ring, gain, phase;
    private final float[] times, kicks;

    /**
     * @param hz      how fast the spring rings
     * @param damping its damping ratio, between 0 (rings forever) and 1 (no overshoot), both excluded
     * @param times   when it is kicked (ms into the loop)
     * @param kicks   how hard: the first swing reaches about 0.8 of this at a damping of 0.15
     */
    Jiggle(float periodMs, float hz, float damping, float[] times, float[] kicks) {
        if (!(damping > 0 && damping < 1) || !(hz > 0)) throw new IllegalArgumentException("a jiggle rings: hz > 0, 0 < damping < 1");
        this.periodMs = periodMs; this.times = times; this.kicks = kicks;
        double w = 2 * Math.PI * hz / 1000;
        decay = w * damping; ring = w * Math.sqrt(1 - damping * damping);
        // Sum over n >= 0 of e^(s (t + n period)), s = -decay + i ring: e^(s t) / (1 - e^(s period)). Its imaginary part is the ring.
        double e = Math.exp(-decay * periodMs), re = 1 - e * Math.cos(ring * periodMs), im = e * Math.sin(ring * periodMs);
        gain = 1 / Math.sqrt(re * re + im * im); phase = Math.atan2(im, re);
    }

    /** The spring's displacement from rest at {@code t} ms into the loop (0 <= t < period). */
    float at(float t) {
        double sum = 0;
        for (int k = 0; k < times.length; k++) {
            float since = t - times[k];
            if (since < 0) since += periodMs;
            sum += kicks[k] * Math.exp(-decay * since) * Math.sin(ring * since + phase);
        }
        return (float) (sum * gain);
    }

    /** The spring never swings further from rest than this. */
    float bound() {
        double sum = 0;
        for (float kick : kicks) sum += Math.abs(kick);
        return (float) (sum * gain);
    }

    /** What is left of a swing {@code ms} after its kick: the envelope the ring settles under, 1 at the kick. */
    float left(float ms) { return (float) Math.exp(-decay * ms); }
}
