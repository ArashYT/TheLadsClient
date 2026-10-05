package com.thelads.core.client.killbanner;

import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * A strip's frames in one colour variant, inflated and recoloured ahead of the player on a worker thread, so drawing a banner
 * frame is only a copy of finished pixels (inflating a frame and recolouring its ~50k pixels through HSV took the render thread
 * 1 to 3 ms every frame). The strip is one deflate stream: the worker decodes forward and keeps the frames from
 * {@code wanted - BEHIND} to {@code wanted + AHEAD}; asking for a frame it has passed starts the stream again, also on the worker.
 */
public final class KillBannerFeed {
    private static final int SLOTS = 12, BEHIND = 3, AHEAD = SLOTS - BEHIND - 1;
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "Lads kill banner frames");
        thread.setDaemon(true);
        return thread;
    });

    private final KillBannerStyle style;
    /** Frame f lives in slot f % SLOTS, held[slot] is the frame it holds (-1: none, or being written). */
    private final byte[][] pixels = new byte[SLOTS][];
    private final int[] held = new int[SLOTS];
    private KillBannerStrip strip;
    private int variant, next, wanted, epoch;
    private boolean running, released;
    private RuntimeException failure;

    public KillBannerFeed(KillBannerStyle style) {
        this.style = style;
        Arrays.fill(held, -1);
    }

    /** Points the feed at this strip and variant; nothing happens if it already is. */
    public synchronized void target(KillBannerStrip strip, int variant) {
        if (strip == this.strip && variant == this.variant) return;
        this.strip = strip;
        this.variant = variant;
        epoch++;
        next = 0;
        wanted = 0;
        failure = null;
        Arrays.fill(held, -1);
    }

    /** True if get(index, 0) would answer now. */
    public synchronized boolean ready(int index) {
        return strip != null && held[clamp(index) % SLOTS] == clamp(index);
    }

    /**
     * The recoloured RGBA pixels of a frame (straight alpha, row-major), waiting up to waitMillis for the worker; null if it is
     * not ready yet. The array stays the feed's: use it before the next call.
     */
    public synchronized byte[] get(int index, long waitMillis) {
        if (released || strip == null) return null;
        if (failure != null) throw new IllegalStateException("Kill banner frames failed", failure);
        index = clamp(index);
        wanted = index;
        int slot = index % SLOTS;
        if (held[slot] != index && index < next) { // passed it: the stream starts over
            epoch++;
            next = 0;
        }
        if (!running && next < strip.frames && next <= wanted + AHEAD) {
            running = true;
            WORKER.execute(this::fill);
        }
        try {
            for (long left = waitMillis * 1_000_000L, end = System.nanoTime() + left; held[slot] != index && left > 0 && !released && failure == null;
                 left = end - System.nanoTime())
                wait(left / 1_000_000L, (int) (left % 1_000_000L));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
        if (failure != null) throw new IllegalStateException("Kill banner frames failed", failure);
        return held[slot] == index ? pixels[slot] : null;
    }

    /** Stops the worker's work for this feed and lets go of its pixels. */
    public synchronized void release() {
        released = true;
        strip = null;
        Arrays.fill(held, -1);
        Arrays.fill(pixels, null);
        notifyAll();
    }

    private int clamp(int index) {
        return Math.max(0, Math.min(strip.frames - 1, index));
    }

    /** Worker: decodes (and keeps, once recoloured) frames up to AHEAD past the one wanted. */
    private void fill() {
        while (true) {
            KillBannerStrip source;
            int frame, slot = 0, mine, colour;
            byte[] out = null;
            boolean keep;
            synchronized (this) {
                if (released || strip == null || next >= strip.frames || next > wanted + AHEAD) {
                    running = false;
                    return;
                }
                source = strip;
                colour = variant;
                mine = epoch;
                frame = next;
                keep = frame >= wanted - BEHIND; // before that only the stream needs to get past
                if (keep) {
                    slot = frame % SLOTS;
                    held[slot] = -1;
                    if (pixels[slot] == null) pixels[slot] = new byte[source.width * source.height * 4];
                    out = pixels[slot];
                }
            }
            try {
                if (keep) {
                    source.frame(frame, out);
                    style.recolor(out, colour);
                } else source.frame(frame);
            } catch (Throwable broken) { // a corrupt strip, or anything else: get() reports it instead of waiting for a frame that never comes
                synchronized (this) {
                    if (mine == epoch) failure = broken instanceof RuntimeException ? (RuntimeException) broken : new IllegalStateException(broken);
                    running = false;
                    notifyAll();
                    return;
                }
            }
            synchronized (this) {
                if (mine == epoch) {
                    if (keep) held[slot] = frame;
                    next = frame + 1;
                }
                notifyAll();
            }
        }
    }
}
