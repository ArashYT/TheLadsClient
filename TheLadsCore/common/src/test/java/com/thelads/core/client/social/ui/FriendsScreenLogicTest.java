package com.thelads.core.client.social.ui;

import com.thelads.core.client.bridge.LadsGraphics;
import com.thelads.core.client.social.FriendEntry;
import com.thelads.core.client.social.Presence;
import com.thelads.core.client.social.SocialBridge;
import com.thelads.core.client.social.SocialProtocol;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FriendsScreenLogicTest {

    @Test
    void onlineFriendsSortedFirstThenByName() {
        UUID u1 = UUID.randomUUID();
        UUID u2 = UUID.randomUUID();
        UUID u3 = UUID.randomUUID();
        UUID u4 = UUID.randomUUID();
        UUID u5 = UUID.randomUUID();

        SocialBridge.GameInfo game = new SocialBridge.GameInfo("1.20.1", "fabric", "plain", "", "", "", "1.9.18");
        Presence onlineMenu = new Presence("menu", game, null, null);
        Presence onlineHosting = new Presence("hosting", game, null, new Presence.WorldAd("w1", "My World", "friends", 8, 1));
        Presence offline = Presence.OFFLINE;

        FriendEntry f1 = new FriendEntry(u1, "Zara", null, offline, 100);
        FriendEntry f2 = new FriendEntry(u2, "Charlie", null, onlineMenu, 200);
        FriendEntry f3 = new FriendEntry(u3, "Alice", null, onlineHosting, 300);
        FriendEntry f4 = new FriendEntry(u4, "Bob", null, offline, 400);
        FriendEntry f5 = new FriendEntry(u5, "Dave", "Adam", onlineMenu, 500); // display name "Adam"

        FakeCallbacks callbacks = new FakeCallbacks();
        callbacks.friends.addAll(List.of(f1, f2, f3, f4, f5));

        FriendsScreenLogic logic = new FriendsScreenLogic(callbacks);
        List<FriendEntry> sorted = logic.sortedFriends();

        // Online friends: "Adam" (f5), "Alice" (f3), "Charlie" (f2)
        // Offline friends: "Bob" (f4), "Zara" (f1)
        assertEquals(5, sorted.size());
        assertEquals("Adam", sorted.get(0).displayName());
        assertTrue(sorted.get(0).presence().online());

        assertEquals("Alice", sorted.get(1).displayName());
        assertTrue(sorted.get(1).presence().online());

        assertEquals("Charlie", sorted.get(2).displayName());
        assertTrue(sorted.get(2).presence().online());

        assertEquals("Bob", sorted.get(3).displayName());
        assertFalse(sorted.get(3).presence().online());

        assertEquals("Zara", sorted.get(4).displayName());
        assertFalse(sorted.get(4).presence().online());
    }

    @Test
    void joinButtonDisabledShowsReason() {
        UUID u1 = UUID.randomUUID();
        SocialBridge.GameInfo game = new SocialBridge.GameInfo("1.8.9", "forge", "plain", "", "", "", "1.9.18");
        Presence hosting = new Presence("hosting", game, null, new Presence.WorldAd("w2", "SMP", "friends", 8, 3));
        FriendEntry friend = new FriendEntry(u1, "Arash", null, hosting, 100);

        FakeCallbacks callbacks = new FakeCallbacks();
        callbacks.friends.add(friend);
        callbacks.joinCheck = new FriendsScreenLogic.JoinCheck(false, "Needs Zero SMP 0.4.0");

        FriendsScreenLogic logic = new FriendsScreenLogic(callbacks);
        TestGraphics g = new TestGraphics(480, 270);
        logic.render(g, 0, 0);

        assertFalse(logic.isJoinEnabled(u1), "Join button must be disabled when canJoin is false");
        assertEquals("Needs Zero SMP 0.4.0", logic.joinReason(u1));

        // Attempting to click the join button does not trigger requestJoin
        FriendsScreenLogic.Box joinBtn = logic.joinButtonBox(u1);
        assertNotNull(joinBtn, "Join button box should exist for hosting friend");
        logic.mouseClicked(joinBtn.x() + 2, joinBtn.y() + 2, 0);

        assertFalse(callbacks.requestedJoins.contains(u1), "Disabled join button must not trigger requestJoin");
    }

    @Test
    void incompatibleReasonOnlyShownOnHover() {
        UUID u1 = UUID.randomUUID();
        SocialBridge.GameInfo game = new SocialBridge.GameInfo("1.8.9", "forge", "plain", "", "", "", "1.9.18");
        Presence hosting = new Presence("hosting", game, null, new Presence.WorldAd("w2", "SMP", "friends", 8, 3));
        FriendEntry friend = new FriendEntry(u1, "Arash", null, hosting, 100);

        FakeCallbacks callbacks = new FakeCallbacks();
        callbacks.friends.add(friend);
        callbacks.joinCheck = new FriendsScreenLogic.JoinCheck(false, "Version mismatch");

        FriendsScreenLogic logic = new FriendsScreenLogic(callbacks);
        TestGraphics gNoHover = new TestGraphics(480, 270);
        logic.render(gNoHover, 0, 0);

        // Reason should NOT be rendered when mouse is at (0, 0)
        assertFalse(gNoHover.drawnTexts.contains("Version mismatch"),
                "Incompatible reason text must NOT render when Join button is not hovered");

        // When mouse hovers the Join button
        FriendsScreenLogic.Box joinBtn = logic.joinButtonBox(u1);
        assertNotNull(joinBtn);
        TestGraphics gHover = new TestGraphics(480, 270);
        logic.render(gHover, joinBtn.x() + 2, joinBtn.y() + 2);

        assertTrue(gHover.drawnTexts.contains("Version mismatch"),
                "Incompatible reason must render as a hover tooltip when hovering the disabled Join button");
    }

    @Test
    void realNicknameInputEnterSavesEscCancelsAndEmptyClears() {
        UUID u1 = UUID.randomUUID();
        FriendEntry friend = new FriendEntry(u1, "Alex", null, Presence.OFFLINE, 100);
        FakeCallbacks callbacks = new FakeCallbacks();
        callbacks.friends.add(friend);

        FriendsScreenLogic logic = new FriendsScreenLogic(callbacks);
        TestGraphics g = new TestGraphics(480, 270);
        logic.render(g, 0, 0);

        // Start editing nickname
        logic.startEditingNick(u1);
        assertEquals(u1, logic.editingNickId());

        // Type nickname "Ally"
        logic.setNickInput("Ally");
        logic.keyPressed(257, 28, 0); // Enter

        assertEquals("Ally", callbacks.nicked.get(u1), "Enter should save nickname");
        assertNull(logic.editingNickId(), "Editing should end after save");

        // Now test empty string clears nickname
        logic.startEditingNick(u1);
        logic.setNickInput("   ");
        logic.keyPressed(257, 28, 0); // Enter
        assertNull(callbacks.nicked.get(u1), "Empty nickname should clear it to null");

        // Test Esc cancels without saving
        logic.startEditingNick(u1);
        logic.setNickInput("CanceledName");
        logic.keyPressed(256, 1, 0); // Esc
        assertNull(logic.editingNickId());
        assertNotEquals("CanceledName", callbacks.nicked.get(u1));

        // Test 32 character cap
        logic.startEditingNick(u1);
        for (int i = 0; i < 40; i++) {
            logic.charTyped('a');
        }
        assertEquals(32, logic.nickInput().length(), "Nickname input must be capped at 32 characters");
    }

    @Test
    void loaderAndPackRenderedInStatusLine() {
        UUID u1 = UUID.randomUUID();
        SocialBridge.GameInfo game = new SocialBridge.GameInfo("1.20.1", "fabric", "pack", "Zero SMP", "0.4.0", "", "1.9.18");
        Presence presence = new Presence("singleplayer", game, null, null);
        FriendEntry friend = new FriendEntry(u1, "Steve", null, presence, 100);

        FakeCallbacks callbacks = new FakeCallbacks();
        callbacks.friends.add(friend);

        FriendsScreenLogic logic = new FriendsScreenLogic(callbacks);
        TestGraphics g = new TestGraphics(640, 360);
        logic.render(g, 0, 0);

        boolean foundPack = g.drawnTexts.stream().anyMatch(t -> t.contains("1.20.1 Fabric") && t.contains("Zero SMP"));
        assertTrue(foundPack, "Friend status line should render version, loader, and pack");
    }

    @Test
    void friendsListAndDmPaneScrollBounds() {
        FakeCallbacks callbacks = new FakeCallbacks();
        // Add 25 friends so list exceeds visible height
        for (int i = 0; i < 25; i++) {
            callbacks.friends.add(new FriendEntry(UUID.randomUUID(), "Friend" + i, null, Presence.OFFLINE, 100 + i));
        }

        FriendsScreenLogic logic = new FriendsScreenLogic(callbacks);
        TestGraphics g = new TestGraphics(480, 270);
        logic.render(g, 0, 0);

        assertTrue(logic.maxScroll() > 0, "maxScroll must be > 0 when items overflow");
        assertEquals(0, logic.scrollOffset());

        // Scroll down
        logic.mouseScrolled(100, 100, -5);
        assertTrue(logic.scrollOffset() > 0);

        // Scroll way down past end - clamped to maxScroll
        logic.mouseScrolled(100, 100, -100);
        assertEquals(logic.maxScroll(), logic.scrollOffset(), "scrollOffset must clamp at maxScroll");

        // Scroll way up past beginning - clamped to 0
        logic.mouseScrolled(100, 100, 100);
        assertEquals(0, logic.scrollOffset(), "scrollOffset must clamp at 0");

        // Test DM scrolling
        FriendEntry friend = callbacks.friends.get(0);
        logic.openDm(friend);

        for (int i = 0; i < 25; i++) {
            callbacks.dms.add(new FriendsScreenLogic.DmEntry(friend.id(), "Msg " + i, System.currentTimeMillis()));
        }

        logic.render(g, 0, 0);
        assertTrue(logic.maxDmScroll() > 0);
        // Auto-stick to bottom initially
        assertEquals(logic.maxDmScroll(), logic.dmScrollOffset());

        // Scroll up in DM thread
        logic.mouseScrolled(100, 100, 5);
        assertTrue(logic.dmScrollOffset() < logic.maxDmScroll());
    }

    @Test
    void dmInputCappedAtMaxDm() {
        UUID u1 = UUID.randomUUID();
        FriendEntry friend = new FriendEntry(u1, "PlayerOne", null, Presence.OFFLINE, 100);
        FakeCallbacks callbacks = new FakeCallbacks();
        callbacks.friends.add(friend);

        FriendsScreenLogic logic = new FriendsScreenLogic(callbacks);
        logic.openDm(friend);

        for (int i = 0; i < SocialProtocol.MAX_DM + 50; i++) {
            logic.charTyped('x');
        }

        assertEquals(SocialProtocol.MAX_DM, logic.dmInput().length(),
                "DM input length must be capped at SocialProtocol.MAX_DM (256)");
    }

    @Test
    void nullCallbacksListsDoNotThrow() {
        FriendsScreenLogic.Callbacks nullCallbacks = new FriendsScreenLogic.Callbacks() {
            @Override public List<FriendEntry> friends() { return null; }
            @Override public List<FriendEntry> incomingRequests() { return null; }
            @Override public List<FriendEntry> outgoingRequests() { return null; }
            @Override public List<FriendEntry> blocked() { return null; }
            @Override public List<FriendsScreenLogic.DmEntry> dms(UUID id) { return null; }
            @Override public String connectionStatus() { return null; }
        };

        FriendsScreenLogic logic = new FriendsScreenLogic(nullCallbacks);
        TestGraphics g = new TestGraphics(480, 270);
        assertDoesNotThrow(() -> logic.render(g, 0, 0));

        logic.setTab(FriendsScreenLogic.Tab.REQUESTS);
        assertDoesNotThrow(() -> logic.render(g, 0, 0));

        logic.setTab(FriendsScreenLogic.Tab.BLOCKED);
        assertDoesNotThrow(() -> logic.render(g, 0, 0));
    }

    @Test
    void dmPaneSendAndReceive() {
        UUID u1 = UUID.randomUUID();
        FriendEntry friend = new FriendEntry(u1, "PlayerOne", null, Presence.OFFLINE, 100);
        FakeCallbacks callbacks = new FakeCallbacks();
        callbacks.friends.add(friend);

        FriendsScreenLogic logic = new FriendsScreenLogic(callbacks);
        TestGraphics g = new TestGraphics(480, 270);
        logic.render(g, 0, 0);

        logic.openDm(friend);
        assertEquals(friend, logic.activeDmFriend());

        logic.setDmInput("Hello there!");
        logic.sendDm();

        assertEquals(1, callbacks.sentDms.size());
        assertEquals("Hello there!", callbacks.sentDms.get(0).text);
        assertEquals(u1, callbacks.sentDms.get(0).to);
        assertEquals("", logic.dmInput(), "Input field must clear after sending");
    }

    @Test
    void tabSwitchingChangesActiveTab() {
        FakeCallbacks callbacks = new FakeCallbacks();
        FriendsScreenLogic logic = new FriendsScreenLogic(callbacks);

        assertEquals(FriendsScreenLogic.Tab.FRIENDS, logic.tab());
        logic.setTab(FriendsScreenLogic.Tab.REQUESTS);
        assertEquals(FriendsScreenLogic.Tab.REQUESTS, logic.tab());
        logic.setTab(FriendsScreenLogic.Tab.BLOCKED);
        assertEquals(FriendsScreenLogic.Tab.BLOCKED, logic.tab());
        logic.setTab(FriendsScreenLogic.Tab.ADD);
        assertEquals(FriendsScreenLogic.Tab.ADD, logic.tab());
    }

    static class FakeCallbacks implements FriendsScreenLogic.Callbacks {
        final List<FriendEntry> friends = new ArrayList<>();
        final List<FriendEntry> incoming = new ArrayList<>();
        final List<FriendEntry> outgoing = new ArrayList<>();
        final List<FriendEntry> blocked = new ArrayList<>();
        final List<FriendsScreenLogic.DmEntry> dms = new ArrayList<>();
        final Map<UUID, String> nicked = new HashMap<>();
        FriendsScreenLogic.JoinCheck joinCheck = new FriendsScreenLogic.JoinCheck(true, "");
        final List<UUID> requestedJoins = new ArrayList<>();
        final List<SentDm> sentDms = new ArrayList<>();
        final List<UUID> accepted = new ArrayList<>();
        final List<UUID> declined = new ArrayList<>();
        final List<UUID> removed = new ArrayList<>();
        final List<UUID> blockedIds = new ArrayList<>();
        final List<String> addedNames = new ArrayList<>();
        boolean closed = false;

        record SentDm(UUID to, String text) {}

        @Override public List<FriendEntry> friends() { return friends; }
        @Override public List<FriendEntry> incomingRequests() { return incoming; }
        @Override public List<FriendEntry> outgoingRequests() { return outgoing; }
        @Override public List<FriendEntry> blocked() { return blocked; }
        @Override public List<FriendsScreenLogic.DmEntry> dms(UUID id) { return dms; }
        @Override public FriendsScreenLogic.JoinCheck canJoin(FriendEntry friend) { return joinCheck; }
        @Override public void requestJoin(UUID id) { requestedJoins.add(id); }
        @Override public void sendDm(UUID id, String text) { sentDms.add(new SentDm(id, text)); }
        @Override public void acceptRequest(UUID id) { accepted.add(id); }
        @Override public void declineRequest(UUID id) { declined.add(id); }
        @Override public void removeFriend(UUID id) { removed.add(id); }
        @Override public void blockFriend(UUID id) { blockedIds.add(id); }
        @Override public void setNick(UUID id, String nick) { nicked.put(id, nick); }
        @Override public void addFriend(String name) { addedNames.add(name); }
        @Override public void onClose() { closed = true; }
    }

    record TestGraphics(int w, int h, List<String> drawnTexts) implements LadsGraphics {
        TestGraphics(int w, int h) {
            this(w, h, new ArrayList<>());
        }

        @Override public void fill(int a, int b, int c, int d, int e) {}
        @Override public void drawText(String t, int x, int y, int c, boolean s) {
            if (t != null) drawnTexts.add(t);
        }
        @Override public void drawCenteredText(String t, int x, int y, int c, boolean s) {
            if (t != null) drawnTexts.add(t);
        }
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
