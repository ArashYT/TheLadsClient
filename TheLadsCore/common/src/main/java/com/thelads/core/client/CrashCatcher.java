package com.thelads.core.client;

import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.SliderOption;

/**
 * Crash Catcher (OneClient's CrashPatch): a crash in the client's game tick while a world is open closes the world and shows a Lads
 * screen with the crash summary, Copy and Return to title, instead of closing the game. The game tick is Minecraft.tick on 1.20.1
 * and 26.3 (world, entity, particle, sound, screen and input ticking) and Minecraft.runTick on 1.8.9 (the same, plus the packets it
 * handles). Everything else crashes as vanilla does: a crash while drawing a frame (the GL state is unknown afterwards), outside a
 * world, during startup or while resources load, any Error anywhere in its causes (out of memory, stack overflow, a missing class or
 * mixin), F3+C's deliberate debug crash, a second crash within 30 seconds of a caught one, and more caught crashes in one session than
 * Crash Limit. A caught crash is still written to crash-reports and the log, as vanilla writes it, with a {@link #MARKER} section: the
 * game exits normally later, so the launcher sends the reports carrying it as it sends a crash's.
 */
public final class CrashCatcher {
    public static final String NAME = "Crash Catcher", LIMIT = "Crash Limit";
    /** The section a caught crash's report gets ("-- Lads Crash Catcher --"), which the launcher looks for (CrashReporter.CaughtMarker). */
    public static final String MARKER = "Lads Crash Catcher", MARKER_DETAIL = "Caught";
    public static final String MARKER_TEXT = "The game left the world and kept running instead of closing";
    /** The title of the crash F3+C makes on purpose: never caught. */
    static final String DEBUG_CRASH = "Manually triggered debug crash";
    /** A crash this soon after a caught one is not caught: the world it left is not to be trusted. */
    static final long REPEAT = 30_000_000_000L;
    /** 1.8.9: true while Minecraft.refreshResources runs (it runs inside the game tick there, after F3+T or a pack change). */
    public static boolean reloading;
    /** The last caught crash: what failed and where (the screen), and the whole report (Copy). */
    public static String summary = "", report = "";
    private static int caught;
    private static long caughtAt;

    private CrashCatcher() {}

    /**
     * May this crash of the game tick be caught ({@code title}: its crash report's title, null when it has none): the module is on, the
     * player is in a world and it is none of the cases above.
     */
    public static boolean catches(Throwable error, String title, boolean inWorld, long now) {
        Module module = ModuleManager.getInstance().getModule(NAME);
        if (module == null || !module.isEnabled() || !inWorld || reloading || !(error instanceof Exception) || DEBUG_CRASH.equals(title)) return false;
        if (caught > 0 && now - caughtAt < REPEAT) return false;
        if (caught >= (module.getOption(LIMIT) instanceof SliderOption limit ? (int) limit.getValue() : 3)) return false;
        Throwable cause = error;
        for (int depth = 0; cause != null && depth < 32; depth++, cause = cause.getCause()) if (cause instanceof Error) return false;
        return true;
    }

    /** A crash was caught: {@code title} is its crash report's title ("Ticking entity"), {@code text} the whole report. */
    public static void caught(Throwable error, String title, String text, long now) {
        caught++;
        caughtAt = now;
        summary = summary(error, title);
        report = text == null ? "" : text;
    }

    /** The report's title, then the innermost cause's class and message and the line it was thrown at. */
    static String summary(Throwable error, String title) {
        Throwable root = error;
        for (int depth = 0; root.getCause() != null && root.getCause() != root && depth < 32; depth++) root = root.getCause();
        StringBuilder text = new StringBuilder(title == null || title.isEmpty() ? "Game tick" : title).append('\n')
            .append(root.getClass().getSimpleName());
        if (root.getMessage() != null) text.append(": ").append(root.getMessage());
        StackTraceElement[] trace = root.getStackTrace();
        if (trace.length > 0) text.append("\nat ").append(trace[0]);
        return text.toString();
    }
}
