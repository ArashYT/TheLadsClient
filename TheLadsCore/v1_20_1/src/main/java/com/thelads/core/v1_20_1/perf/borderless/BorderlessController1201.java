package com.thelads.core.v1_20_1.perf.borderless;

import com.mojang.blaze3d.platform.Window;
import com.thelads.core.client.BorderlessWindow;
import com.thelads.core.v1_20_1.compat.ModPresence1201;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * BorderlessFullscreen needs the window's mode switch to run whenever the module is switched while the game is already
 * fullscreen, and once at startup when the saved options start the game fullscreen: vanilla only runs it when the player
 * toggles fullscreen. This tick notices a module switch that disagrees with the window and asks the window to redo it.
 */
public final class BorderlessController1201 {
    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore-Borderless");
    private static final boolean ORIGINAL_MOD = ModPresence1201.loaded("borderlesswindow");
    private static long lastAttempt;
    private static int attempts;
    private static boolean lastWanted;
    private static boolean reportedSkip;

    private BorderlessController1201() {}

    public static boolean standsDown() {
        return ORIGINAL_MOD;
    }

    public static void tick() {
        if (ORIGINAL_MOD) {
            if (!reportedSkip) {
                reportedSkip = true;
                LOGGER.info("BorderlessFullscreen: the Borderless Window jar is installed, Lads stands down");
            }
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        Window window = minecraft.getWindow();

        if (window == null || !window.isFullscreen() || !((Object) window instanceof BorderlessAccess1201 access)) {
            attempts = 0;
            return;
        }

        boolean wanted = BorderlessWindow.enabled();

        if (wanted != lastWanted) {
            lastWanted = wanted;
            attempts = 0;
        }

        if (wanted == access.lads$borderlessActive() || attempts >= 3) {
            return;
        }

        long now = System.nanoTime();

        if (now - lastAttempt < 1_000_000_000L) {
            return;
        }

        lastAttempt = now;
        attempts++;
        LOGGER.info("BorderlessFullscreen: {} the borderless window (attempt {})", wanted ? "entering" : "leaving", attempts);
        access.lads$reapplyFullscreenMode();
    }
}
