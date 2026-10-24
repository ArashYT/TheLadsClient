package com.thelads.core.client.social;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

public class SocialStateTest {

    private static class TestBridge implements SocialBridge {
        GameInfo gameInfo = new GameInfo("1.20.1", "fabric", "plain", "", "", "", "1.9.18");
        boolean inWorld = false;
        int leftWorldCount = 0;
        List<String> toasts = new ArrayList<>();

        @Override
        public int openToFriends(HostOptions options) {
            return 25565;
        }

        @Override
        public GameInfo gameInfo() {
            return gameInfo;
        }

        @Override
        public boolean inWorldOrServer() {
            return inWorld;
        }

        @Override
        public void leaveWorld(Runnable then) {
            inWorld = false;
            leftWorldCount++;
            then.run();
        }

        @Override
        public void toast(String title, String body, String acceptAction, String denyAction) {
            toasts.add(title + ": " + body);
        }
    }

    private static class FakeP2PSession implements com.thelads.core.client.social.p2p.P2PSession {
        private volatile boolean closed;
        private Consumer<String> closeHandler;

        @Override public void onSignal(JsonObject data) {}
        @Override public java.io.InputStream in(int stream) { return new java.io.ByteArrayInputStream(new byte[0]); }
        @Override public java.io.OutputStream out(int stream) { return new java.io.ByteArrayOutputStream(); }
        @Override public boolean relayed() { return false; }
        @Override public long bytes() { return 0; }
        @Override public void close(String reason) {
            if (closed) return;
            closed = true;
            if (closeHandler != null) closeHandler.accept(reason);
        }
        @Override public void onClose(Consumer<String> reason) { this.closeHandler = reason; }
    }

    private TestBridge bridge;
    private SocialState state;
    private List<JsonObject> sentMessages;

    @BeforeEach
    public void setup() {
        bridge = new TestBridge();
        SocialBridge.set(bridge);

        GuestSession.setP2PStarter((sid, host, cfg, sendSignal) ->
                java.util.concurrent.CompletableFuture.completedFuture(new FakeP2PSession()));

        sentMessages = new ArrayList<>();
        state = new SocialState();
        state.setClientSender(sentMessages::add);
    }

    @AfterEach
    public void cleanup() {
        GuestSession.setP2PStarter(null);
        if (state != null) {
            state.stopHosting();
            state.closeGuestSession("cleanup");
        }
        SocialBridge.set(new SocialBridge() {});
    }

    @Test
    public void joinDisabledOnVersionMismatch() {
        UUID id = UUID.randomUUID();

        // 1. Offline friend
        FriendEntry offlineFriend = new FriendEntry(id, "Arash", null, Presence.OFFLINE, 0);
        SocialState.JoinCheck checkOffline = state.canJoin(offlineFriend);
        assertFalse(checkOffline.ok());
        assertNotNull(checkOffline.reason());

        // 2. Online in menu (no world)
        Presence menuPresence = new Presence("menu", bridge.gameInfo(), null, null);
        FriendEntry menuFriend = new FriendEntry(id, "Arash", null, menuPresence, 0);
        SocialState.JoinCheck checkMenu = state.canJoin(menuFriend);
        assertFalse(checkMenu.ok());
        assertEquals("Not hosting a world", checkMenu.reason());

        // 3. World is full
        Presence.WorldAd fullWorld = new Presence.WorldAd("world1", "My World", "friends", 4, 4);
        Presence fullPresence = new Presence("hosting", bridge.gameInfo(), null, fullWorld);
        FriendEntry fullFriend = new FriendEntry(id, "Arash", null, fullPresence, 0);
        SocialState.JoinCheck checkFull = state.canJoin(fullFriend);
        assertFalse(checkFull.ok());
        assertEquals("World is full", checkFull.reason());

        // 4. MC version mismatch: Host is on 1.8.9, Guest is on 1.20.1
        SocialBridge.GameInfo host189 = new SocialBridge.GameInfo("1.8.9", "forge", "plain", "", "", "", "1.9.18");
        Presence.WorldAd ad189 = new Presence.WorldAd("world1", "My World", "friends", 8, 1);
        Presence presence189 = new Presence("hosting", host189, null, ad189);
        FriendEntry friend189 = new FriendEntry(id, "Arash", null, presence189, 0);
        SocialState.JoinCheck check189 = state.canJoin(friend189);
        assertFalse(check189.ok(), "Join must be disabled when MC versions differ");
        assertTrue(check189.reason().contains("1.8.9"), "Reason should specify needed version: " + check189.reason());

        // 5. Modpack version mismatch: Host is on Zero SMP 0.4.0
        SocialBridge.GameInfo hostPack = new SocialBridge.GameInfo("1.20.1", "fabric", "pack", "Zero SMP", "0.4.0", "hash1", "1.9.18");
        Presence.WorldAd adPack = new Presence.WorldAd("world1", "Zero SMP World", "friends", 8, 1);
        Presence presencePack = new Presence("hosting", hostPack, null, adPack);
        FriendEntry friendPack = new FriendEntry(id, "Arash", null, presencePack, 0);
        SocialState.JoinCheck checkPack = state.canJoin(friendPack);
        assertFalse(checkPack.ok(), "Join must be disabled when pack versions differ");
        assertTrue(checkPack.reason().contains("Zero SMP 0.4.0"), "Reason should specify needed pack: " + checkPack.reason());

        // 6. Matching game info -> Join allowed!
        Presence.WorldAd okAd = new Presence.WorldAd("world1", "Vanilla World", "friends", 8, 1);
        Presence okPresence = new Presence("hosting", bridge.gameInfo(), null, okAd);
        FriendEntry okFriend = new FriendEntry(id, "Arash", null, okPresence, 0);
        SocialState.JoinCheck checkOk = state.canJoin(okFriend);
        assertTrue(checkOk.ok(), "Join must be allowed when versions match");
        assertNull(checkOk.reason());
    }

    @Test
    public void secondJoinReplacesFirstAfterConfirm() {
        bridge.inWorld = true;

        UUID host1 = UUID.randomUUID();
        UUID host2 = UUID.randomUUID();

        // Guest joins first world
        state.handleP2PStart("sid-1", host1, "Host1", "world-1", "guest", new JsonObject());
        assertTrue(state.isGuestSessionActive());
        assertEquals(host1, state.activeGuestHostUuid());

        // Now an invite to second world is accepted
        bridge.inWorld = true; // Guest is inside world 1
        state.acceptInvite("invite-2");

        // Accepting invite when already in world should leave world cleanly
        assertTrue(bridge.leftWorldCount >= 1, "leaveWorld must be called before joining second world");

        // Simulate backend p2p_start for second world
        state.handleP2PStart("sid-2", host2, "Host2", "world-2", "guest", new JsonObject());

        // Only second session is active
        assertTrue(state.isGuestSessionActive());
        assertEquals(host2, state.activeGuestHostUuid());
        assertEquals("sid-2", state.activeGuestSid());
    }

    @Test
    public void autoAcceptOnlyFriendsInFriendsScope() {
        HostOptions options = new HostOptions();
        options.scope = HostOptions.Scope.FRIENDS;
        options.approval = HostOptions.Approval.AUTO_FRIENDS;
        options.maxPlayers = 8;
        state.startHosting(options);

        UUID friendId = UUID.randomUUID();
        UUID strangerId = UUID.randomUUID();

        // Add friendId to friends list
        state.addFriendLocally(new FriendEntry(friendId, "GoodFriend", null, Presence.OFFLINE, 0));

        // 1. Join request from friend: auto-accepted!
        state.handleJoinRequest("req-1", friendId, bridge.gameInfo());

        boolean autoAcceptedFriend = sentMessages.stream().anyMatch(m ->
                "join_accept".equals(m.get("t").getAsString()) && "req-1".equals(m.get("reqId").getAsString()));
        assertTrue(autoAcceptedFriend, "Friend join request must be automatically accepted in AUTO_FRIENDS mode");
        assertTrue(state.hostSession().acceptedUuids().contains(friendId));

        sentMessages.clear();

        // 2. Join request from stranger: NOT auto-accepted
        state.handleJoinRequest("req-2", strangerId, bridge.gameInfo());

        boolean autoAcceptedStranger = sentMessages.stream().anyMatch(m ->
                "join_accept".equals(m.get("t").getAsString()) && "req-2".equals(m.get("reqId").getAsString()));
        assertFalse(autoAcceptedStranger, "Stranger join request must NOT be auto-accepted");
        assertFalse(state.hostSession().acceptedUuids().contains(strangerId));

        // 3. If approval is ASK: friend request is NOT auto-accepted
        options.approval = HostOptions.Approval.ASK;
        state.startHosting(options);
        sentMessages.clear();

        state.handleJoinRequest("req-3", friendId, bridge.gameInfo());
        boolean autoAcceptedAsk = sentMessages.stream().anyMatch(m ->
                "join_accept".equals(m.get("t").getAsString()) && "req-3".equals(m.get("reqId").getAsString()));
        assertFalse(autoAcceptedAsk, "When approval is ASK, friend join request must not be auto-accepted");
    }
}
