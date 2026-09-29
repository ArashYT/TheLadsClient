package com.thelads.core.v26_2.feature;

import java.util.Locale;
import static com.thelads.core.v26_2.feature.MenuKeyController.Action.*;
import static com.thelads.core.v26_2.feature.MenuKeyController.Decision.*;

/** Dependency-free targeted checks: run directly with javac/java, without the common test suite. */
public final class NativeInputChecks {
    private static int routingChecks, clockChecks;

    public static void main(String[] args) {
        routesMenuWithoutRepeating();
        preservesOtherScreensAndEditing();
        handlesRebindFocusAndMouse();
        preservesClockAndReusesText();
        System.out.println("PASS " + routingChecks + " input checks; " + clockChecks
            + " clock checks (all 24,000 daily ticks, boundaries, locale and object reuse)");
    }

    private static void route(MenuKeyController.Decision expected, MenuKeyController.Decision actual) {
        routingChecks++;
        if (expected != actual) throw new AssertionError("Expected " + expected + ", got " + actual);
    }

    private static void routesMenuWithoutRepeating() {
        var c = new MenuKeyController();
        route(PASS, c.key(90, 44, PRESS, false, true, false)); // Zoom Z remains independent.
        route(PASS, c.key(344, 54, REPEAT, true, true, false));
        route(OPEN, c.key(344, 54, PRESS, true, true, false));
        route(CONSUME, c.key(344, 54, REPEAT, true, false, true));
        route(CONSUME, c.key(344, 54, PRESS, true, false, true)); // Duplicate down.
        route(PASS, c.key(90, 44, PRESS, false, false, true));
        route(CONSUME, c.key(344, 54, RELEASE, true, false, true));
        route(TRY_CLOSE, c.key(344, 54, PRESS, true, false, true));
        c.capture(344, 54); // Screen actually closed.
        route(CONSUME, c.key(344, 54, REPEAT, true, true, false));
        route(CONSUME, c.key(344, 54, RELEASE, true, true, false));
        route(PASS, c.key(344, 54, RELEASE, true, true, false));
        route(OPEN, c.key(344, 54, PRESS, true, true, false));
    }

    private static void preservesOtherScreensAndEditing() {
        var c = new MenuKeyController();
        // Chat, Controls, inventory, title and an inactive/dead world cannot open the menu.
        route(PASS, c.key(344, 54, PRESS, true, false, false));
        route(PASS, c.key(344, 54, REPEAT, true, false, false));
        route(PASS, c.key(344, 54, RELEASE, true, false, false));
        route(TRY_CLOSE, c.key(82, 19, PRESS, true, false, true));
        // Editing consumed the press, so the adapter does not capture. Repeats/releases
        // continue to the screen, and the independent character callback is never intercepted.
        route(PASS, c.key(82, 19, REPEAT, true, false, true));
        route(PASS, c.key(82, 19, RELEASE, true, false, true));
        route(TRY_CLOSE, c.key(82, 19, PRESS, true, false, true));
    }

    private static void handlesRebindFocusAndMouse() {
        var c = new MenuKeyController();
        route(OPEN, c.key(344, 54, PRESS, true, true, false));
        // Rebinding while a key is down does not lose the original release.
        route(CONSUME, c.key(344, 54, RELEASE, false, false, false));
        route(OPEN, c.key(82, 19, PRESS, true, true, false));
        c.reset(); // Lost focus / changed world.
        route(PASS, c.key(82, 19, REPEAT, true, true, false));
        route(OPEN, c.key(82, 19, PRESS, true, true, false));
        route(CONSUME, c.key(82, 19, RELEASE, true, false, true));
        route(OPEN, c.key(-1, 91, PRESS, true, true, false)); // Scancode-only key.
        route(PASS, c.key(-1, 92, RELEASE, false, false, true));
        route(CONSUME, c.key(-1, 91, RELEASE, false, false, true));
        route(OPEN, c.key(-1004, 0, PRESS, true, true, false)); // Rebound mouse button.
        route(PASS, c.key(-1, 0, RELEASE, false, false, true));
        route(CONSUME, c.key(-1004, 0, RELEASE, true, false, true));
    }

    private static String previousClock(long clock) {
        long time = (clock + 6000L) % 24000L;
        return String.format("%02d:%02d", time / 1000L, (time % 1000L) * 60L / 1000L);
    }

    private static void checkClock(boolean condition) {
        clockChecks++;
        if (!condition) throw new AssertionError("Clock check " + clockChecks);
    }

    private static void preservesClockAndReusesText() {
        var cache = new GameTimeText();
        for (long tick = 0; tick < 24000; tick++)
            checkClock(previousClock(tick).equals(cache.get(tick)));
        for (long tick : new long[] {-24001, -24000, -6001, -6000, -1, 24000, 48000,
                                      123456789, Long.MIN_VALUE, Long.MAX_VALUE})
            checkClock(previousClock(tick).equals(cache.get(tick)));
        String first = cache.get(0);
        checkClock(first == cache.get(0));
        checkClock(first == cache.get(16)); // Same minute, distinct game tick.
        checkClock(first == cache.get(24000)); // Same minute in another day/world.
        checkClock(!first.equals(cache.get(17))); // Next displayed minute.
        Locale original = Locale.getDefault(Locale.Category.FORMAT);
        try {
            Locale.setDefault(Locale.Category.FORMAT, Locale.forLanguageTag("ar-EG"));
            checkClock(previousClock(17).equals(cache.get(17)));
            Locale.setDefault(Locale.Category.FORMAT, Locale.US);
            checkClock(previousClock(17).equals(cache.get(17)));
        } finally {
            Locale.setDefault(Locale.Category.FORMAT, original);
        }
    }
}
