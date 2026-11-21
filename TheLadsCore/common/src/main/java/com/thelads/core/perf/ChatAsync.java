package com.thelads.core.perf;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Chat off the main thread: shared state for the version mixins. Chat lines are wrapped on the
 * {@code Lads-Chat} worker (see {@link OrderedAsync}) and the finished message is added on the main thread, in
 * arrival order. Everything that touches Minecraft state stays on the main thread; the worker only ever reads
 * the width cache below. Kill switch: {@code -Dlads.asyncChat=false}. No Minecraft types here.
 */
public final class ChatAsync {
    private static final boolean KILLED = "false".equalsIgnoreCase(System.getProperty("lads.asyncChat"));

    /** Mirrors the "Async Chat" option in the Performance module. */
    public static volatile boolean enabled = true;

    /** Main thread only: true while a queued message is being added, so the hooks run the vanilla code. */
    public static boolean applying;
    /** Main thread only: the prepared wrap for the message being added (null otherwise). */
    public static Object current;

    private static volatile OrderedAsync queue;
    private static final ConcurrentHashMap<Object, ConcurrentHashMap<Integer, Float>> NORMAL = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Object, ConcurrentHashMap<Integer, Float>> BOLD = new ConcurrentHashMap<>();

    private ChatAsync() {}

    public static boolean on() {
        return enabled && !KILLED;
    }

    public static boolean killed() {
        return KILLED;
    }

    public static OrderedAsync queue(java.util.function.Consumer<Throwable> onError) {
        OrderedAsync q = queue;
        if (q == null) {
            synchronized (ChatAsync.class) {
                q = queue;
                if (q == null) queue = q = new OrderedAsync("Lads-Chat", onError);
            }
        }
        return q;
    }

    /** The queue if it was ever created, else null. */
    public static OrderedAsync queueIfAny() {
        return queue;
    }

    /** Width of a code point in a font (the style's font id plus bold), or null if never measured on the main thread. */
    public static Float width(Object font, boolean bold, int codepoint) {
        ConcurrentHashMap<Integer, Float> m = (bold ? BOLD : NORMAL).get(font);
        return m == null ? null : m.get(codepoint);
    }

    public static void putWidth(Object font, boolean bold, int codepoint, float width) {
        (bold ? BOLD : NORMAL).computeIfAbsent(font, k -> new ConcurrentHashMap<>()).put(codepoint, width);
    }

    /** Fonts were reloaded or swapped: forget widths and make queued results stale (they are re-wrapped on the main thread). */
    public static void fontsChanged() {
        NORMAL.clear();
        BOLD.clear();
        OrderedAsync q = queue;
        if (q != null) q.invalidate();
    }

    /** Disconnect / level change / rescale: queued results are stale, the messages themselves are kept. */
    public static void invalidate() {
        OrderedAsync q = queue;
        if (q != null) q.invalidate();
    }

    /** Chat was cleared: queued messages that arrived before the clear are dropped, as vanilla would have cleared them. */
    public static void cancelPending() {
        OrderedAsync q = queue;
        if (q != null) q.cancelPending();
    }
}
