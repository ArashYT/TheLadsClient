package com.thelads.core.client;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import org.slf4j.LoggerFactory;

/** The Lads artwork as the Minecraft window/taskbar icon, in vanilla's icons/icon_NxN.png sizes and order. */
public final class WindowIcons {
    public static final int[] SIZES = {16, 32, 48, 128, 256};
    private static volatile boolean logged;

    private WindowIcons() {}

    /** False only if the jar lost its icon resources; the game then keeps its own icon. */
    public static boolean available() {
        for (int size : SIZES) if (WindowIcons.class.getResource(path(size)) == null) return false;
        return true;
    }

    public static InputStream open(int size) throws IOException {
        InputStream in = WindowIcons.class.getResourceAsStream(path(size));
        if (in == null) throw new FileNotFoundException(path(size));
        return in;
    }

    public static void logApplied() {
        if (logged) return;
        logged = true;
        LoggerFactory.getLogger("TheLadsCore").info("Lads window icon applied ({} sizes)", SIZES.length);
    }

    static String path(int size) { return "/assets/theladscore/icons/window_" + size + ".png"; }
}
