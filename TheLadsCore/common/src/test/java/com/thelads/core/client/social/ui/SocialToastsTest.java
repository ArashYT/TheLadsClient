package com.thelads.core.client.social.ui;

import com.thelads.core.client.bridge.LadsGraphics;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SocialToastsTest {

    private final AtomicLong mockClock = new AtomicLong(1000);

    @BeforeEach
    void setUp() {
        SocialToasts.clear();
        SocialToasts.setTimeSupplier(mockClock::get);
    }

    @Test
    void newestFirstAndExpiresAfter15s() {
        AtomicBoolean toast1Accepted = new AtomicBoolean(false);
        AtomicBoolean toast2Accepted = new AtomicBoolean(false);

        // At t = 1000, show Toast 1
        mockClock.set(1000);
        SocialToasts.show("Invite 1", "Arash invited you to Survival", "Join", "Decline",
                () -> toast1Accepted.set(true), () -> {});

        assertEquals(1, SocialToasts.activeToasts().size());
        assertEquals("Invite 1", SocialToasts.activeToasts().get(0).title());

        // At t = 2000, show Toast 2
        mockClock.set(2000);
        SocialToasts.show("Invite 2", "Bob invited you to Creative", "Join", "Decline",
                () -> toast2Accepted.set(true), () -> {});

        assertEquals(2, SocialToasts.activeToasts().size());
        // Newest must be first!
        assertEquals("Invite 2", SocialToasts.activeToasts().get(0).title());
        assertEquals("Invite 1", SocialToasts.activeToasts().get(1).title());

        // Accepting newest should accept Toast 2
        boolean accepted = SocialToasts.acceptNewest();
        assertTrue(accepted);
        assertTrue(toast2Accepted.get(), "Toast 2 (newest) must be accepted");
        assertFalse(toast1Accepted.get(), "Toast 1 must not be accepted yet");
        assertEquals(1, SocialToasts.activeToasts().size());
        assertEquals("Invite 1", SocialToasts.activeToasts().get(0).title());

        // Now test 15s expiration on Toast 1 (created at t=1000)
        // At t = 15,999 (14.999s elapsed since Toast 1 creation), Toast 1 is still active
        mockClock.set(15999);
        assertEquals(1, SocialToasts.activeToasts().size());

        // At t = 16,001 (15.001s elapsed since Toast 1 creation), Toast 1 must expire
        mockClock.set(16001);
        assertEquals(0, SocialToasts.activeToasts().size(), "Toast must expire after 15 seconds");

        // acceptNewest returns false when no active toasts
        assertFalse(SocialToasts.acceptNewest());
    }

    @Test
    void denyNewestTriggersDenyCallback() {
        AtomicBoolean denied = new AtomicBoolean(false);
        mockClock.set(5000);
        SocialToasts.show("Request", "Eve wants to join", "Accept", "Deny",
                () -> {}, () -> denied.set(true));

        assertEquals(1, SocialToasts.activeToasts().size());
        boolean res = SocialToasts.denyNewest();
        assertTrue(res);
        assertTrue(denied.get());
        assertEquals(0, SocialToasts.activeToasts().size());
    }

    @Test
    void renderAndClickAccept() {
        AtomicBoolean accepted = new AtomicBoolean(false);
        mockClock.set(1000);
        SocialToasts.show("Friend Request", "Alice sent a request", "Accept", "Ignore",
                () -> accepted.set(true), () -> {});

        TestGraphics g = new TestGraphics(800, 600);
        SocialToasts.render(g, 800, 600, 0, 0);

        // Click within the toast accept area
        int toastX = 800 - 210;
        int toastY = 10;
        // Accept button is inside toast
        boolean clicked = SocialToasts.mouseClicked(toastX + 30, toastY + 38, 0, 800, 600);
        assertTrue(clicked);
        assertTrue(accepted.get());
        assertEquals(0, SocialToasts.activeToasts().size());
    }

    record TestGraphics(int w, int h) implements LadsGraphics {
        @Override public void fill(int a, int b, int c, int d, int e) {}
        @Override public void drawText(String t, int x, int y, int c, boolean s) {}
        @Override public void drawCenteredText(String t, int x, int y, int c, boolean s) {}
        @Override public int textWidth(String t) { return t == null ? 0 : t.length() * 6; }
        @Override public int fontHeight() { return 9; }
        @Override public void pushPose() {}
        @Override public void popPose() {}
        @Override public void translate(float x, float y) {}
        @Override public void scale(float x, float y) {}
        @Override public void enableScissor(int a, int b, int c, int d) {}
        @Override public void disableScissor() {}
        @Override public void blit(String t, int x, int y, int u, int v, int w, int h) {}
        @Override public void drawHead(String n, String u, int x, int y, int s) {}
        @Override public int getScaledWidth() { return w; }
        @Override public int getScaledHeight() { return h; }
    }
}
