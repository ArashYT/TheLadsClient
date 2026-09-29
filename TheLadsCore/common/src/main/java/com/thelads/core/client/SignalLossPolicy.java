// SPDX-License-Identifier: Apache-2.0
// Timing/animation behavior adapted from SignalLoss 1.2.1+26.2, Copyright Hexandcube.
// Lads changes: connection-scoped state, monotonic frame time, pause baseline, stable recovery text.
package com.thelads.core.client;

/** Pure connection-silence policy. Packet observations remain authoritative; lag is not a diagnosis. */
public final class SignalLossPolicy {
    public record Settings(int timeoutMs, int minimumMs, int lingerMs) {
        public Settings { timeoutMs = Math.max(0, timeoutMs); minimumMs = Math.max(0, minimumMs); lingerMs = Math.max(0, lingerMs); }
    }
    public record Frame(float progress, double seconds, boolean interrupted, boolean lingering) {}
    private Object session;
    private boolean initialized, warning, recovering;
    private long joined, lastFrame, started, recovery, baseline;
    private double displayedSeconds;
    private float progress;

    public void reset() { session = null; initialized = warning = recovering = false; progress = 0; displayedSeconds = 0; }
    public void joined(Object connection, long now) { reset(); initialized = true; session = connection; joined = lastFrame = baseline = now; }
    public Frame update(Object connection, long now, long received, boolean eligible, boolean paused, boolean reducedMotion, Settings settings) {
        if (!eligible || connection == null) { reset(); return new Frame(0, 0, false, false); }
        if (!initialized || session != connection) {
            joined(connection, now);
        }
        if (paused) {
            baseline = lastFrame = now; warning = recovering = false; progress = 0; displayedSeconds = 0;
            return new Frame(0, 0, false, false);
        }
        // baseline is the most recent join/pause; a old packet cannot create an immediate resume warning.
        long silence = Math.min(now - baseline, received == 0 ? now - baseline : now - received);
        silence = Math.max(0, silence);
        boolean over = silence / 1_000_000L > settings.timeoutMs;
        boolean grace = now - joined < 5_000_000_000L;
        boolean visible = false;
        if (over && !grace) {
            if (!warning) started = now;
            warning = true; recovering = false; displayedSeconds = silence / 1_000_000_000.0; visible = true;
        } else if (warning) {
            if (!recovering) { recovering = true; recovery = now; }
            visible = (now - started) / 1_000_000L < settings.minimumMs || (now - recovery) / 1_000_000L < settings.lingerMs;
            if (!visible) warning = recovering = false;
        }
        double elapsed = Math.max(0, (now - lastFrame) / 1_000_000_000.0); lastFrame = now;
        progress = reducedMotion ? (visible ? 1 : 0) : (float) Math.clamp(progress + (visible ? 1 : -1) * elapsed * 4, 0, 1);
        return new Frame(progress, displayedSeconds, warning && !recovering, recovering);
    }
}
