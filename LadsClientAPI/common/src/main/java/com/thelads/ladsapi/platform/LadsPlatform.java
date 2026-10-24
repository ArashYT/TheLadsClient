package com.thelads.ladsapi.platform;

import java.nio.file.Path;
import java.util.Collection;
import java.util.logging.Logger;

/**
 * What each server platform (Paper, Velocity, Fabric, Forge) gives the shared core. FROZEN INTERFACE (phase 0).
 */
public interface LadsPlatform {
    /** "paper", "velocity", "fabric", "forge". */
    String id();

    /** True on a proxy (Velocity): sends `reset` on every server switch (PROTOCOL.md "reset"). */
    boolean isProxy();

    /** Where config.yml and rules.yml live (plugins/LadsClientAPI or config/ladsclientapi). */
    Path dataFolder();

    Logger logger();

    /** Runs on the server/main thread after `ticks` (20 per second). */
    void runLater(Runnable task, int ticks);

    /** Every connected player right now. */
    Collection<? extends LadsPlayerHandle> online();
}
