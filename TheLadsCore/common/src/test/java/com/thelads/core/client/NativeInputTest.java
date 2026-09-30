package com.thelads.core.client;

import java.util.Locale;
import org.junit.jupiter.api.Test;
import static com.thelads.core.client.MenuKeyController.Action.*;
import static com.thelads.core.client.MenuKeyController.Decision.*;
import static org.junit.jupiter.api.Assertions.*;

/** Menu-key routing (MenuKeyController) and the HUD clock text (GameTimeText) every adapter shares. */
class NativeInputTest {
    @Test void routesMenuWithoutRepeating() {
        var c = new MenuKeyController();
        assertEquals(PASS, c.key(90, 44, PRESS, false, true, false)); // Zoom Z remains independent.
        assertEquals(PASS, c.key(344, 54, REPEAT, true, true, false));
        assertEquals(OPEN, c.key(344, 54, PRESS, true, true, false));
        assertEquals(CONSUME, c.key(344, 54, REPEAT, true, false, true));
        assertEquals(CONSUME, c.key(344, 54, PRESS, true, false, true)); // Duplicate down.
        assertEquals(PASS, c.key(90, 44, PRESS, false, false, true));
        assertEquals(CONSUME, c.key(344, 54, RELEASE, true, false, true));
        assertEquals(TRY_CLOSE, c.key(344, 54, PRESS, true, false, true));
        c.capture(344, 54); // Screen actually closed.
        assertEquals(CONSUME, c.key(344, 54, REPEAT, true, true, false));
        assertEquals(CONSUME, c.key(344, 54, RELEASE, true, true, false));
        assertEquals(PASS, c.key(344, 54, RELEASE, true, true, false));
        assertEquals(OPEN, c.key(344, 54, PRESS, true, true, false));
    }

    @Test void preservesOtherScreensAndEditing() {
        var c = new MenuKeyController();
        // Chat, Controls, inventory, title and an inactive/dead world cannot open the menu.
        assertEquals(PASS, c.key(344, 54, PRESS, true, false, false));
        assertEquals(PASS, c.key(344, 54, REPEAT, true, false, false));
        assertEquals(PASS, c.key(344, 54, RELEASE, true, false, false));
        assertEquals(TRY_CLOSE, c.key(82, 19, PRESS, true, false, true));
        // Editing consumed the press, so the adapter does not capture. Repeats/releases
        // continue to the screen, and the independent character callback is never intercepted.
        assertEquals(PASS, c.key(82, 19, REPEAT, true, false, true));
        assertEquals(PASS, c.key(82, 19, RELEASE, true, false, true));
        assertEquals(TRY_CLOSE, c.key(82, 19, PRESS, true, false, true));
    }

    @Test void handlesRebindFocusAndMouse() {
        var c = new MenuKeyController();
        assertEquals(OPEN, c.key(344, 54, PRESS, true, true, false));
        // Rebinding while a key is down does not lose the original release.
        assertEquals(CONSUME, c.key(344, 54, RELEASE, false, false, false));
        assertEquals(OPEN, c.key(82, 19, PRESS, true, true, false));
        c.reset(); // Lost focus / changed world.
        assertEquals(PASS, c.key(82, 19, REPEAT, true, true, false));
        assertEquals(OPEN, c.key(82, 19, PRESS, true, true, false));
        assertEquals(CONSUME, c.key(82, 19, RELEASE, true, false, true));
        assertEquals(OPEN, c.key(-1, 91, PRESS, true, true, false)); // Scancode-only key.
        assertEquals(PASS, c.key(-1, 92, RELEASE, false, false, true));
        assertEquals(CONSUME, c.key(-1, 91, RELEASE, false, false, true));
        assertEquals(OPEN, c.key(-1004, 0, PRESS, true, true, false)); // Rebound mouse button.
        assertEquals(PASS, c.key(-1, 0, RELEASE, false, false, true));
        assertEquals(CONSUME, c.key(-1004, 0, RELEASE, true, false, true));
    }

    private static String previousClock(long clock) {
        long time = (clock + 6000L) % 24000L;
        return String.format("%02d:%02d", time / 1000L, (time % 1000L) * 60L / 1000L);
    }

    /** All 24,000 daily ticks, boundaries, locale and object reuse. */
    @Test void preservesClockAndReusesText() {
        var cache = new GameTimeText();
        for (long tick = 0; tick < 24000; tick++)
            if (!previousClock(tick).equals(cache.get(tick))) fail("clock text at tick " + tick);
        for (long tick : new long[] {-24001, -24000, -6001, -6000, -1, 24000, 48000,
                                      123456789, Long.MIN_VALUE, Long.MAX_VALUE})
            assertEquals(previousClock(tick), cache.get(tick), "clock text at tick " + tick);
        String first = cache.get(0);
        assertSame(first, cache.get(0));
        assertSame(first, cache.get(16), "same minute, distinct game tick");
        assertSame(first, cache.get(24000), "same minute in another day/world");
        assertNotEquals(first, cache.get(17), "next displayed minute");
        Locale original = Locale.getDefault(Locale.Category.FORMAT);
        try {
            Locale.setDefault(Locale.Category.FORMAT, Locale.forLanguageTag("ar-EG"));
            assertEquals(previousClock(17), cache.get(17));
            Locale.setDefault(Locale.Category.FORMAT, Locale.US);
            assertEquals(previousClock(17), cache.get(17));
        } finally {
            Locale.setDefault(Locale.Category.FORMAT, original);
        }
    }
}
