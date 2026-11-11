package com.thelads.core.client.title;

import static org.junit.jupiter.api.Assertions.*;

import com.thelads.core.client.social.FriendEntry;
import com.thelads.core.client.social.Presence;
import com.thelads.core.client.social.SocialState;
import com.thelads.core.client.title.TitleScreenTheme.Rect;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Layout math of the Panorama dropdown and the chat sidebar, and the sidebar's list model; no game, no graphics. */
class TitleOverlayLayoutTest {
    private static final int[][] SIZES = {{240, 180}, {260, 200}, {320, 240}, {530, 300}, {854, 480}, {1920, 1080}};

    private static boolean within(Rect r, int w, int h) {
        return r.x() >= 0 && r.y() >= 0 && r.x() + r.width() <= w && r.y() + r.height() <= h;
    }

    private static boolean apart(Rect a, Rect b) {
        return a.x() + a.width() <= b.x() || b.x() + b.width() <= a.x() || a.y() + a.height() <= b.y() || b.y() + b.height() <= a.y();
    }

    @Test void dropdownStaysOnScreenAndScrollsWhenLong() {
        for (int[] size : SIZES) {
            int w = size[0], h = size[1];
            for (int anchorWidth : new int[] {20, 110}) {
                Rect anchor = new Rect(w - 50 - 24 - anchorWidth, 6, anchorWidth, 20);
                for (int rows : new int[] {1, 3, 9, 40}) {
                    var d = TitleOverlayLayout.dropdown(w, h, anchor, rows);
                    String at = rows + " rows at " + w + "x" + h;
                    assertTrue(within(d.panel(), w, h), "panel on screen, " + at);
                    assertTrue(d.panel().x() >= TitleOverlayLayout.EDGE && d.panel().x() + d.panel().width() <= w - TitleOverlayLayout.EDGE, "clear of the side edges, " + at);
                    assertTrue(d.panel().y() >= anchor.y() + anchor.height(), "under its button, " + at);
                    assertTrue(d.list().height() >= TitleOverlayLayout.ROW, "at least one row shows, " + at);
                    assertEquals(rows * TitleOverlayLayout.ROW, d.contentHeight());
                    assertEquals(Math.max(0, d.contentHeight() - d.list().height()), d.maxScroll(), "scrolls exactly the hidden part, " + at);
                }
            }
        }
        var few = TitleOverlayLayout.dropdown(854, 480, new Rect(700, 6, 110, 20), 3);
        assertEquals(0, few.maxScroll(), "a short list shows whole");
        var many = TitleOverlayLayout.dropdown(240, 180, new Rect(150, 6, 20, 20), 30);
        assertTrue(many.maxScroll() > 0, "a long list on the smallest canvas scrolls");
    }

    @Test void dropdownRightEdgeFollowsItsButtonUntilTheScreenEdge() {
        var d = TitleOverlayLayout.dropdown(854, 480, new Rect(500, 6, 110, 20), 4);
        assertEquals(500 + 110, d.panel().x() + d.panel().width(), "right edges line up");
        var left = TitleOverlayLayout.dropdown(854, 480, new Rect(10, 6, 20, 20), 4);
        assertEquals(TitleOverlayLayout.EDGE, left.panel().x(), "a button near the left edge: the list stops short of it");
    }

    @Test void sidebarSlidesFromTheRightEdgeAndItsTabRidesAlong() {
        for (int[] size : SIZES) {
            int w = size[0], h = size[1];
            assertEquals(w, TitleOverlayLayout.sidebarX(w, 0), "closed: out of sight");
            assertEquals(w - TitleOverlayLayout.sidebarWidth(w), TitleOverlayLayout.sidebarX(w, 1), "open: fully in");
            int last = w + 1;
            for (float p = 0; p <= 1.001f; p += 0.05f) {
                int x = TitleOverlayLayout.sidebarX(w, p);
                assertTrue(x <= last, "moves one way only");
                last = x;
            }
            Rect closed = TitleOverlayLayout.tab(w, h, 0), open = TitleOverlayLayout.tab(w, h, 1);
            assertEquals(w, closed.x() + closed.width(), "the tab is flush to the right edge");
            assertEquals((h - closed.height()) / 2, closed.y(), "and in the middle of it");
            assertEquals(TitleOverlayLayout.sidebarX(w, 1), open.x() + open.width(), "open: on the panel's left edge");
            assertTrue(open.x() >= 0, "never off the left");
            assertTrue(TitleOverlayLayout.sidebarWidth(w) <= w - 40, "keeps 40 of the title clear");
        }
        assertEquals(0f, TitleOverlayLayout.ease(-1));
        assertEquals(1f, TitleOverlayLayout.ease(2));
    }

    @Test void sidebarPartsFitAndNeverOverlap() {
        for (int[] size : SIZES) {
            int w = size[0], h = size[1];
            var s = TitleOverlayLayout.sidebar(w, h, 1);
            String at = w + "x" + h;
            for (Rect part : List.of(s.panel(), s.messages(), s.friends(), s.search(), s.list(), s.footer(), s.close())) {
                assertTrue(within(part, w, h), "on screen at " + at + ": " + part);
                assertTrue(part.x() >= s.panel().x() && part.x() + part.width() <= s.panel().x() + s.panel().width(), "inside the panel at " + at);
            }
            List<Rect> stacked = List.of(s.messages(), s.search(), s.list(), s.footer());
            for (int i = 0; i < stacked.size(); i++) for (int j = i + 1; j < stacked.size(); j++) assertTrue(apart(stacked.get(i), stacked.get(j)), "stacked parts apart at " + at);
            assertTrue(apart(s.messages(), s.friends()), "the two segments apart at " + at);
            assertTrue(s.list().height() >= 2 * TitleOverlayLayout.PERSON_ROW, "two people show at " + at);
            assertTrue(s.close().y() + s.close().height() <= TitleOverlayLayout.HEADER, "close sits in the header at " + at);
            // the tab is clear of the pause-free title controls at the edges: nothing but the title's own header sits in its column
            var tab = TitleOverlayLayout.tab(w, h, 0);
            assertTrue(tab.y() > 26 && tab.y() + tab.height() < h - 29, "the tab clears the header controls and the account strip at " + at);
        }
    }

    @Test void listHitTestingAndScrollingFollowTheRows() {
        int[] heights = {16, 34, 34, 16, 34};
        assertEquals(134, TitleOverlayLayout.sum(heights));
        assertEquals(0, TitleOverlayLayout.rowAt(heights, 0, 5));
        assertEquals(1, TitleOverlayLayout.rowAt(heights, 0, 16));
        assertEquals(2, TitleOverlayLayout.rowAt(heights, 0, 50));
        assertEquals(2, TitleOverlayLayout.rowAt(heights, 20, 30), "scrolled: 30 below the top is 50 into the list");
        assertEquals(-1, TitleOverlayLayout.rowAt(heights, 0, 200), "past the last row");
        assertEquals(-1, TitleOverlayLayout.rowAt(heights, 0, -3), "above the first");
        assertEquals(0, TitleOverlayLayout.clampScroll(-9, 134, 80));
        assertEquals(54, TitleOverlayLayout.clampScroll(999, 134, 80));
        assertEquals(0, TitleOverlayLayout.clampScroll(10, 60, 80), "short content never scrolls");
        assertEquals(0, TitleOverlayLayout.scrollToShow(heights, 1, 0, 80), "already in view");
        assertEquals(54, TitleOverlayLayout.scrollToShow(heights, 4, 0, 80), "scrolls down just enough");
        assertEquals(16, TitleOverlayLayout.scrollToShow(heights, 1, 54, 80), "and back up to the top of the row");
    }

    // ---- The list model ----

    private static final long NOW = 1_000_000_000_000L;

    private static FriendEntry friend(String name, boolean online, String server) {
        Presence p = online ? new Presence(server == null ? "menu" : "server", null, server, null) : Presence.OFFLINE;
        return new FriendEntry(UUID.nameUUIDFromBytes(name.getBytes()), name, null, p, 0);
    }

    private static SocialState.Dm dm(FriendEntry from, long ts, String text) {
        return new SocialState.Dm(from == null ? new UUID(0, 0) : from.id(), text, ts);
    }

    @Test void conversationsAreNewestFirstWithUnreadOnlyForTheirNewMessages() {
        var zara = friend("Zara", true, null);
        var bo = friend("Bo", false, null);
        var cy = friend("Cy", true, null);
        Map<UUID, List<SocialState.Dm>> chats = new HashMap<>();
        chats.put(zara.id(), List.of(dm(zara, NOW - 600_000, "hey")));
        chats.put(bo.id(), List.of(dm(bo, NOW - 60_000, "old"), dm(null, NOW - 30_000, "yo")));
        var rows = ChatSidebar.conversations(List.of(zara, bo, cy), chats::get, "", new HashMap<>(), NOW - 3_600_000, NOW);
        assertEquals(List.of("Bo", "Zara"), rows.stream().map(ChatSidebar.Row::name).toList(), "no chat for Cy; newest first");
        assertEquals("You: yo", rows.get(0).sub(), "our own last message is marked");
        assertFalse(rows.get(0).unread(), "we spoke last");
        assertTrue(rows.get(1).unread(), "Zara's message is new");
        assertEquals("10m", rows.get(1).time());
        assertEquals("Z", rows.get(1).initial());
        assertTrue(rows.get(1).online());
        // Opened after it arrived, or older than this game: read.
        assertFalse(ChatSidebar.conversations(List.of(zara), chats::get, "", Map.of(zara.id(), NOW), 0, NOW).get(0).unread());
        assertFalse(ChatSidebar.conversations(List.of(zara), chats::get, "", new HashMap<>(), NOW, NOW).get(0).unread());
        // The filter reads names and the last message.
        assertEquals(1, ChatSidebar.conversations(List.of(zara, bo), chats::get, "ZAR", new HashMap<>(), 0, NOW).size());
        assertEquals(1, ChatSidebar.conversations(List.of(zara, bo), chats::get, "yo", new HashMap<>(), 0, NOW).size());
        assertTrue(ChatSidebar.conversations(List.of(zara, bo), chats::get, "nobody", new HashMap<>(), 0, NOW).isEmpty());
        assertTrue(ChatSidebar.conversations(List.of(), chats::get, "", new HashMap<>(), 0, NOW).isEmpty(), "empty state");
    }

    @Test void friendsListOnlineFirstInSectionsWithWhatTheyPlay() {
        var rows = ChatSidebar.friends(List.of(friend("Ann", true, "play.example.net"), friend("Bob", false, null), friend("Cat", true, null)), "");
        assertEquals(List.of("ONLINE - 2", "Ann", "Cat", "OFFLINE - 1", "Bob"), rows.stream().map(ChatSidebar.Row::name).toList());
        assertFalse(rows.get(0).person());
        assertEquals("Playing on play.example.net", rows.get(1).sub());
        assertEquals("In menus", rows.get(2).sub());
        assertEquals("Offline", rows.get(4).sub());
        assertFalse(rows.get(4).online());
        assertEquals(List.of("OFFLINE - 1", "Bob"), ChatSidebar.friends(List.of(friend("Ann", true, null), friend("Bob", false, null)), "bo").stream().map(ChatSidebar.Row::name).toList(),
            "a section with nobody left is dropped");
        assertTrue(ChatSidebar.friends(List.of(), "").isEmpty(), "empty state");
    }

    @Test void messageAgeAndPreviewsReadShort() {
        assertEquals("now", ChatSidebar.ago(NOW, NOW - 5_000));
        assertEquals("now", ChatSidebar.ago(NOW, NOW + 5_000), "a clock slightly ahead is still now");
        assertEquals("1m", ChatSidebar.ago(NOW, NOW - 60_000));
        assertEquals("3h", ChatSidebar.ago(NOW, NOW - 3 * 3_600_000L));
        assertEquals("2d", ChatSidebar.ago(NOW, NOW - 2 * 86_400_000L));
        assertEquals("3w", ChatSidebar.ago(NOW, NOW - 21 * 86_400_000L));
        var zara = friend("Zara", true, null);
        assertEquals("GIF", ChatSidebar.preview(zara, new SocialState.Dm(zara.id(), "https://tenor.com/x", NOW, null, "https://media/x.gif")));
        assertEquals("You: Photo", ChatSidebar.preview(zara, new SocialState.Dm(new UUID(0, 0), "Screenshot", NOW, "img", null)));
        assertEquals("a b", ChatSidebar.preview(zara, dm(zara, NOW, "a\nb")));
    }

    // ---- The Panorama list model ----

    @Test void panoramaRowsListEveryFamilyWithNameAndVersionAndMarkTheCurrentOne() {
        String[] choices = {PanoramaPicker.DEFAULT, "26.3", "26.2.1", "26.2", "1.21.11", "1.21.4", "1.20.6", "1.8.9"};
        var rows = PanoramaPicker.entries(choices);
        assertEquals(List.of("Default", "Minecraft 26.3", "Minecraft 26.2", "Tricky Trials", "Trails & Tales", "Bountiful Update"),
            rows.stream().map(PanoramaPicker.Entry::name).toList(), "Default, then one row per family, newest first");
        assertEquals(List.of(PanoramaPicker.DEFAULT, "26.3", "26.2.1", "1.21.11", "1.20.6", "1.8.9"), rows.stream().map(PanoramaPicker.Entry::choice).toList());
        assertEquals("1.21.11", rows.get(3).version(), "each row shows the version it picks");
        assertTrue(PanoramaPicker.isCurrent(rows.get(0), PanoramaPicker.DEFAULT));
        assertFalse(PanoramaPicker.isCurrent(rows.get(1), PanoramaPicker.DEFAULT));
        assertTrue(PanoramaPicker.isCurrent(rows.get(3), "1.21.4"), "a hotfix picked in the settings marks its family");
        assertFalse(PanoramaPicker.isCurrent(rows.get(0), "1.21.4"));
        long marked = rows.stream().filter(r -> PanoramaPicker.isCurrent(r, "26.2")).count();
        assertEquals(1, marked, "exactly one current row");
        assertEquals(1, PanoramaPicker.entries(new String[] {PanoramaPicker.DEFAULT}).size(), "list still loading: Default alone");
    }
}
