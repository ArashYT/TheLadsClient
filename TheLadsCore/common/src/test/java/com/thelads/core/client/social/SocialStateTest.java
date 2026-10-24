package com.thelads.core.client.social;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.thelads.core.client.bridge.DefaultGameBridge;
import com.thelads.core.client.bridge.LadsGameBridge;
import com.thelads.core.client.social.p2p.P2PSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

public class SocialStateTest {

    private static class TestBridge implements SocialBridge {
        GameInfo gameInfo = new GameInfo("1.20.1", "fabric", "plain", "", "", "", "1.9.18");
        boolean inWorld = false;
        int leftWorldCount = 0;
        String connectedServerAddress = null;
        boolean openFriendsCalled = false;
        List<String> toasts = new ArrayList<>();
        List<String> chatActions = new ArrayList<>();

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
        public void connectToServer(String address) {
            this.connectedServerAddress = address;
        }

        @Override
        public void openFriendsScreen() {
            this.openFriendsCalled = true;
        }

        @Override
        public void toast(String title, String body, String acceptAction, String denyAction) {
            toasts.add(title + ": " + body + " [" + acceptAction + " / " + denyAction + "]");
        }

        @Override
        public void chatAction(String text, String actionId) {
            chatActions.add(text + " -> " + actionId);
        }
    }

    private static class FakeP2PSession implements P2PSession {
        private volatile boolean closed;
        private Consumer<String> closeHandler;
        final List<JsonObject> receivedSignals = new ArrayList<>();
        private final boolean relayed;
        private final long bytes;

        FakeP2PSession(boolean relayed, long bytes) {
            this.relayed = relayed;
            this.bytes = bytes;
        }

        FakeP2PSession() {
            this(false, 0);
        }

        @Override public void onSignal(JsonObject data) { receivedSignals.add(data); }
        @Override public InputStream in(int stream) { return new ByteArrayInputStream(new byte[0]); }
        @Override public OutputStream out(int stream) { return new ByteArrayOutputStream(); }
        @Override public boolean relayed() { return relayed; }
        @Override public long bytes() { return bytes; }
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
    private FakeP2PSession lastGuestP2P;

    @BeforeEach
    public void setup() {
        HostSession.skipLoopbackForTest = true;
        bridge = new TestBridge();
        SocialBridge.set(bridge);

        UUID selfUuid = UUID.randomUUID();
        LadsGameBridge.set(new DefaultGameBridge() {
            @Override
            public Session session() {
                return new Session(selfUuid, "TestUser", "test-token");
            }
        });

        lastGuestP2P = new FakeP2PSession(false, 0);
        GuestSession.setP2PStarter((sid, host, cfg, sendSignal) ->
                CompletableFuture.completedFuture(lastGuestP2P));

        sentMessages = new ArrayList<>();
        state = new SocialState();
        state.setClientSender(sentMessages::add);
    }

    @AfterEach
    public void cleanup() {
        HostSession.skipLoopbackForTest = false;
        GuestSession.setP2PStarter(null);
        HostSession.setP2PStarter(null);
        if (state != null) {
            state.stopHosting();
            state.closeGuestSession("cleanup");
        }
        SocialBridge.set(new SocialBridge() {});
        LadsGameBridge.set(new DefaultGameBridge());
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
        state.requestJoin(host1);
        state.handleP2PStart("sid-1", host1, "Host1", "world-1", "guest", new JsonObject());
        assertTrue(state.isGuestSessionActive());
        assertEquals(host1, state.activeGuestHostUuid());

        // Now an invite to second world is accepted
        bridge.inWorld = true; // Guest is inside world 1
        JsonObject invite = new JsonObject();
        invite.addProperty("t", "invite");
        invite.addProperty("inviteId", "invite-2");
        invite.addProperty("from", host2.toString());
        state.onMessage(invite);
        state.acceptInvite("invite-2");

        // Simulate backend p2p_start for second world
        state.handleP2PStart("sid-2", host2, "Host2", "world-2", "guest", new JsonObject());

        // Accepting invite and receiving p2p_start leaves world cleanly
        assertTrue(bridge.leftWorldCount >= 1, "leaveWorld must be called before joining second world");

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

    @Test
    public void serverInviteConnectsDirectlyToServer() {
        bridge.inWorld = true;
        UUID friend = UUID.randomUUID();

        // Receive server invite
        JsonObject inviteMsg = new JsonObject();
        inviteMsg.addProperty("t", SocialProtocol.INVITE);
        inviteMsg.addProperty("inviteId", "inv-server-1");
        inviteMsg.addProperty("from", friend.toString());
        inviteMsg.addProperty("server", "mc.hypixel.net");
        state.onMessage(inviteMsg);

        // Verify toast and chatAction emitted with invite_accept:inv-server-1
        assertTrue(bridge.toasts.stream().anyMatch(t -> t.contains("invite_accept:inv-server-1")));
        assertTrue(bridge.chatActions.stream().anyMatch(c -> c.contains("invite_accept:inv-server-1")));

        // Accept the invite
        state.acceptInvite("inv-server-1");

        // Left world and connected to mc.hypixel.net
        assertEquals(1, bridge.leftWorldCount);
        assertEquals("mc.hypixel.net", bridge.connectedServerAddress);

        // invite_accept sent to server
        assertTrue(sentMessages.stream().anyMatch(m ->
                "invite_accept".equals(m.get("t").getAsString()) && "inv-server-1".equals(m.get("inviteId").getAsString())));
    }

    @Test
    public void requestJoinDoesNotLeaveWorldUntilP2PStart() {
        bridge.inWorld = true;
        UUID host = UUID.randomUUID();

        // Request join
        state.requestJoin(host);

        // Must NOT leave the current world yet
        assertEquals(0, bridge.leftWorldCount, "requestJoin must not leave world until p2p_start arrives");

        // join_request sent to backend
        assertTrue(sentMessages.stream().anyMatch(m ->
                "join_request".equals(m.get("t").getAsString()) && host.toString().equals(m.get("host").getAsString())));

        // Backend confirms with p2p_start
        state.handleP2PStart("sid-join-1", host, "HostPlayer", "world-1", "guest", new JsonObject());

        // Now leaveWorld is invoked
        assertEquals(1, bridge.leftWorldCount, "leaveWorld must be invoked when p2p_start arrives for a guest in a world");
        assertTrue(state.isGuestSessionActive());
    }

    @Test
    public void onHelloMergesDMsAndPreservesInMemoryHistoryWith200Cap() {
        UUID friend = UUID.randomUUID();

        // 1. User sends a DM while online
        state.sendDm(friend, "Hello from client");
        assertEquals(1, state.dms(friend).size());
        assertEquals("Hello from client", state.dms(friend).get(0).text());

        // 2. Reconnect occurs: onHello arrives with an offline DM
        JsonObject hello = new JsonObject();
        JsonArray dms = new JsonArray();
        JsonObject offlineDm = new JsonObject();
        offlineDm.addProperty("from", friend.toString());
        offlineDm.addProperty("text", "Offline reply from friend");
        offlineDm.addProperty("ts", System.currentTimeMillis() + 100);
        dms.add(offlineDm);
        hello.add("dms", dms);

        state.onHello(hello);

        // Both client-sent DM and offline DM must be in history (in-memory not wiped!)
        List<SocialState.Dm> history = state.dms(friend);
        assertEquals(2, history.size());
        assertEquals("Hello from client", history.get(0).text());
        assertEquals("Offline reply from friend", history.get(1).text());

        // 3. Duplicate hello DMs must not duplicate
        state.onHello(hello);
        assertEquals(2, state.dms(friend).size(), "Duplicate offline DM from hello must not be duplicated");

        // 4. History is capped at 200 messages
        for (int i = 0; i < 250; i++) {
            state.sendDm(friend, "Burst msg " + i);
        }
        assertEquals(200, state.dms(friend).size(), "DM history per friend must be capped at 200 messages");
    }

    @Test
    public void handleActionDispatchesAllFrozenActionIds() {
        UUID friend = UUID.randomUUID();

        // 1. friend_accept / friend_decline
        state.handleAction("friend_accept:" + friend);
        assertTrue(sentMessages.stream().anyMatch(m -> "friend_accept".equals(m.get("t").getAsString()) && friend.toString().equals(m.get("uuid").getAsString())));

        state.handleAction("friend_decline:" + friend);
        assertTrue(sentMessages.stream().anyMatch(m -> "friend_decline".equals(m.get("t").getAsString()) && friend.toString().equals(m.get("uuid").getAsString())));

        // 2. invite_accept / invite_decline
        state.handleAction("invite_accept:inv-99");
        assertTrue(sentMessages.stream().anyMatch(m -> "invite_accept".equals(m.get("t").getAsString()) && "inv-99".equals(m.get("inviteId").getAsString())));

        state.handleAction("invite_decline:inv-99");
        assertTrue(sentMessages.stream().anyMatch(m -> "invite_decline".equals(m.get("t").getAsString()) && "inv-99".equals(m.get("inviteId").getAsString())));

        // 3. join_accept / join_deny
        state.handleAction("join_accept:req-99");
        assertTrue(sentMessages.stream().anyMatch(m -> "join_accept".equals(m.get("t").getAsString()) && "req-99".equals(m.get("reqId").getAsString())));

        state.handleAction("join_deny:req-99");
        assertTrue(sentMessages.stream().anyMatch(m -> "join_deny".equals(m.get("t").getAsString()) && "req-99".equals(m.get("reqId").getAsString())));

        // 4. open_friends & dm
        state.handleAction("open_friends");
        assertTrue(bridge.openFriendsCalled);

        bridge.openFriendsCalled = false;
        state.handleAction("dm:" + friend);
        assertTrue(bridge.openFriendsCalled);

        // 5. Unknown action id does not throw
        assertDoesNotThrow(() -> state.handleAction("unknown_random_action:123"));
        assertDoesNotThrow(() -> state.handleAction(null));
        assertDoesNotThrow(() -> state.handleAction(""));
    }

    @Test
    public void signalRoutingDispatchesToOwningSession() {
        UUID guest = UUID.randomUUID();
        UUID host = UUID.randomUUID();

        FakeP2PSession hostP2P = new FakeP2PSession();
        HostSession.setP2PStarter((sid, isHost, cfg, sendSignal) -> CompletableFuture.completedFuture(hostP2P));

        // Host starts hosting
        HostOptions opt = new HostOptions();
        opt.scope = HostOptions.Scope.FRIENDS;
        state.startHosting(opt);
        state.hostSession().accept(guest);
        state.hostSession().handleP2PStart("sid-host-1", guest, new P2PSession.IceConfig(Collections.emptyList(), Collections.emptyList(), false), sig -> {});

        // Guest starts guest session
        FakeP2PSession guestP2P = new FakeP2PSession();
        GuestSession.setP2PStarter((sid, isHost, cfg, sendSignal) -> CompletableFuture.completedFuture(guestP2P));
        state.requestJoin(host);
        state.handleP2PStart("sid-guest-1", host, "HostWorld", "w1", "guest", new JsonObject());

        assertTrue(state.hostSession().ownsSid("sid-host-1"));
        assertTrue(state.guestSession().ownsSid("sid-guest-1"));

        // Inbound signal for host
        JsonObject hostSignalMsg = new JsonObject();
        hostSignalMsg.addProperty("t", SocialProtocol.SIGNAL);
        hostSignalMsg.addProperty("sid", "sid-host-1");
        JsonObject dataHost = new JsonObject();
        dataHost.addProperty("candidate", "cand-host");
        hostSignalMsg.add("data", dataHost);
        state.onMessage(hostSignalMsg);

        assertEquals(1, hostP2P.receivedSignals.size());
        assertEquals("cand-host", hostP2P.receivedSignals.get(0).get("candidate").getAsString());
        assertEquals(0, guestP2P.receivedSignals.size());

        // Inbound signal for guest
        JsonObject guestSignalMsg = new JsonObject();
        guestSignalMsg.addProperty("t", SocialProtocol.SIGNAL);
        guestSignalMsg.addProperty("sid", "sid-guest-1");
        JsonObject dataGuest = new JsonObject();
        dataGuest.addProperty("candidate", "cand-guest");
        guestSignalMsg.add("data", dataGuest);
        state.onMessage(guestSignalMsg);

        assertEquals(1, guestP2P.receivedSignals.size());
        assertEquals("cand-guest", guestP2P.receivedSignals.get(0).get("candidate").getAsString());
    }

    @Test
    public void p2pEndSentExactlyOnceOnGuestClose() {
        FakeP2PSession guestP2P = new FakeP2PSession(true, 8192);
        GuestSession.setP2PStarter((sid, host, cfg, sendSignal) -> CompletableFuture.completedFuture(guestP2P));

        UUID host = UUID.randomUUID();
        state.requestJoin(host);
        state.handleP2PStart("sid-end-test", host, "Host", "world", "guest", new JsonObject());
        assertTrue(state.isGuestSessionActive());

        // Close guest session
        state.closeGuestSession("User disconnect");
        assertFalse(state.isGuestSessionActive());

        // Assert p2p_end was sent exactly once with relayed=true, bytes=8192
        long endCount = sentMessages.stream().filter(m -> "p2p_end".equals(m.get("t").getAsString())).count();
        assertEquals(1, endCount, "p2p_end must be sent exactly once upon session close");

        JsonObject endMsg = sentMessages.stream().filter(m -> "p2p_end".equals(m.get("t").getAsString())).findFirst().orElseThrow();
        assertEquals("sid-end-test", endMsg.get("sid").getAsString());
        assertEquals("User disconnect", endMsg.get("reason").getAsString());
        assertTrue(endMsg.get("relayed").getAsBoolean());
        assertEquals(8192L, endMsg.get("bytes").getAsLong());

        // Second close must NOT send another p2p_end
        state.closeGuestSession("Second close");
        long endCount2 = sentMessages.stream().filter(m -> "p2p_end".equals(m.get("t").getAsString())).count();
        assertEquals(1, endCount2, "Second close call must not send a second p2p_end");
    }

    @Test
    public void acceptingAnInviteInAWorldAsksBeforeLeaving() {
        UUID host = UUID.randomUUID();
        bridge.inWorld = true;
        JsonObject invite = new JsonObject();
        invite.addProperty("t", "invite");
        invite.addProperty("inviteId", "inv-switch");
        invite.addProperty("from", host.toString());
        JsonObject world = new JsonObject();
        world.addProperty("name", "SMP");
        invite.add("world", world);
        state.onMessage(invite);
        sentMessages.clear();

        state.handleAction("invite_accept:inv-switch");
        assertTrue(sentMessages.isEmpty(), "nothing is accepted before the player confirms");
        assertTrue(bridge.openFriendsCalled, "the Friends screen asks");
        assertEquals(0, bridge.leftWorldCount);
        SocialState.SwitchConfirm ask = state.takePendingSwitch();
        assertNotNull(ask);
        assertTrue(ask.question().startsWith("Leave ") && ask.question().endsWith("'s world?"), ask.question());
        assertNull(state.takePendingSwitch(), "asked once");

        ask.go().run();
        assertEquals("invite_accept", sentMessages.get(0).get("t").getAsString());
        assertEquals("inv-switch", sentMessages.get(0).get("inviteId").getAsString());
    }

    /** F9 (PocState 4): a p2p_start nobody asked for (no join request, no accepted invite) neither leaves the world nor connects. */
    @Test
    public void unsolicitedGuestP2PStartIsIgnored() {
        bridge.inWorld = true;
        UUID stranger = UUID.randomUUID();
        JsonObject start = new JsonObject();
        start.addProperty("t", "p2p_start");
        start.addProperty("sid", "x1");
        start.addProperty("peer", stranger.toString());
        start.addProperty("role", "guest");
        state.onMessage(start);
        assertEquals(0, bridge.leftWorldCount, "never leaves the world on the backend's word alone");
        assertFalse(state.isGuestSessionActive());
        assertNull(com.thelads.core.client.HypixelSafetyManager.getP2PSession());
    }

    /** F9 (PocState 2): the host opens no tunnel for a peer it never accepted. */
    @Test
    public void hostP2PStartForAStrangerIsIgnored() {
        List<String> started = new ArrayList<>();
        HostSession.setP2PStarter((sid, host, cfg, send) -> {
            started.add(sid);
            return new CompletableFuture<>();
        });
        HostOptions o = new HostOptions();
        o.scope = HostOptions.Scope.FRIENDS;
        o.backupOnOpen = false;
        state.startHosting(o);
        state.handleP2PStart("sid-stranger", UUID.randomUUID(), "Stranger", "w", "host", null);
        assertTrue(started.isEmpty(), "no ICE session for a stranger");
    }

    /** F8 (PocState 5): malformed backend messages are ignored instead of throwing (which dropped the whole social connection). */
    @Test
    public void malformedBackendMessagesAreIgnored() {
        UUID friend = UUID.randomUUID();
        for (String bad : new String[] {
                "{\"t\":\"invite\",\"from\":\"" + friend + "\",\"server\":\"a.b\"}",
                "{\"t\":\"dm\",\"from\":\"" + friend + "\",\"text\":\"hi\",\"ts\":\"x\"}",
                "{\"t\":\"p2p_start\",\"sid\":\"y\",\"peer\":\"nope\",\"role\":\"host\"}",
                "{\"t\":\"presence\",\"uuid\":\"" + friend + "\",\"world\":{\"slots\":\"many\"}}",
                "{\"t\":\"join_request\",\"from\":\"" + friend + "\"}"}) {
            assertDoesNotThrow(() -> state.onMessage(com.google.gson.JsonParser.parseString(bad).getAsJsonObject()), bad);
        }
    }
}
