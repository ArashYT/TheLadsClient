package com.thelads.core.client;

import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;

/**
 * Keep Cursor Position (OneClient's BetterScreens cursor lock): when a server replaces one container screen with another (it closes
 * the first, which grabs and centres the pointer, then opens the next, which releases it in the middle), the pointer goes back to
 * where it was. Each version reports a container screen closing ({@link #closed}) and asks as one opens ({@link #reopened}).
 * Visual only. Client thread only.
 */
public final class KeepCursor {
    public static final String NAME = "Keep Cursor Position";
    /** The next container screen must open this soon after the last one closed (servers do both in the same tick or the next). */
    static final long WINDOW = 500_000_000L;
    public static boolean on;
    /** Where the pointer was as the last container screen closed (window pixels). */
    public static double x, y;
    private static long closedAt;
    private static boolean pending;
    private static Module module;

    private KeepCursor() {}

    /** Once a client tick. */
    public static void tick() {
        if (module == null) module = ModuleManager.getInstance().getModule(NAME);
        on = module != null && module.isEnabled();
    }

    /** A container screen closed with the pointer at ({@code px}, {@code py}). */
    public static void closed(double px, double py, long now) {
        if (!on) return;
        x = px;
        y = py;
        closedAt = now;
        pending = true;
    }

    /** A container screen opened: true when the pointer goes back to {@link #x}, {@link #y}. Asked once per closing. */
    public static boolean reopened(long now) {
        boolean restore = on && pending && now - closedAt <= WINDOW;
        pending = false;
        return restore;
    }
}
