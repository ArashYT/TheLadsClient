package com.thelads.core.client;

import java.util.Objects;
import java.util.function.LongSupplier;

/** Timing of the Chat module's message animation: only the newest message slides in from the left and fades in. */
public final class ChatAnimation {
    public static final long DURATION_NANOS = 250_000_000L;
    private final LongSupplier clock;
    private Object message;
    private long startedAt;

    public ChatAnimation() { this(System::nanoTime); }
    public ChatAnimation(LongSupplier clock) { this.clock = clock; }

    /** A new message reached the chat display; {@code key} identifies its lines (the message, or its tick on 1.21.x). */
    public void start(Object key) { message = key; startedAt = clock.getAsLong(); }

    public boolean running() {
        if (message != null && clock.getAsLong() - startedAt >= DURATION_NANOS) message = null;
        return message != null;
    }

    /** 0..1 for a line of the animating message, 1 for every other line. */
    public float progress(Object key) {
        if (message == null || !Objects.equals(message, key)) return 1f;
        long elapsed = clock.getAsLong() - startedAt;
        return elapsed >= DURATION_NANOS ? 1f : Math.max(0f, elapsed / (float) DURATION_NANOS);
    }

    /** Horizontal offset in chat pixels (starts 16 to the left, eases out to 0). */
    public static float slide(float progress) { return (1f - fade(progress)) * -16f; }
    public static float fade(float progress) { return (float) Math.sin(progress * Math.PI / 2.0); }
}
