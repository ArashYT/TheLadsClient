package com.thelads.core.v1_20_1.perf.borderless;

/** Implemented by the borderless mixin on {@code Window}; lets the tick reconcile the module switch with the window. */
public interface BorderlessAccess1201 {
    /** True while the window is the undecorated monitor-sized desktop window of BorderlessFullscreen. */
    boolean lads$borderlessActive();

    /** Makes the window run its fullscreen mode switch again on the next frame (a no-op while it is windowed). */
    void lads$reapplyFullscreenMode();
}
