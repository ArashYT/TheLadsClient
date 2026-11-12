package com.thelads.core.client.social;

import com.google.gson.JsonObject;
import com.thelads.core.client.bridge.DefaultGameBridge;
import com.thelads.core.client.bridge.LadsGameBridge;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/** Party model: backend messages in, requests out, and nothing connects until the player says Join. */
public class SocialPartyTest {
    private final UUID self = UUID.randomUUID(), alice = UUID.randomUUID(), bob = UUID.randomUUID(), stranger = UUID.randomUUID();
    private final List<String> toasts = new ArrayList<>(), connects = new ArrayList<>();
    private boolean inWorld;
    private SocialState state;
    private List<JsonObject> sent;

    @BeforeEach
    public void setup() {
        SocialBridge.set(new SocialBridge() {
            @Override public void toast(String title, String body, String accept, String deny) { toasts.add(title + "|" + body + "|" + accept + "|" + deny); }
            @Override public void connectToServer(String address) { connects.add(address); }
            @Override public boolean inWorldOrServer() { return inWorld; }
        });
        LadsGameBridge.set(new DefaultGameBridge() {
            @Override public Session session() { return new Session(self, "Me", "token"); }
        });
        sent = new ArrayList<>();
        state = new SocialState();
        state.setClientSender(sent::add);
        state.addFriendLocally(new FriendEntry(alice, "Alice", null, Presence.OFFLINE, 0));
        state.addFriendLocally(new FriendEntry(bob, "Bob", null, Presence.OFFLINE, 0));
    }

    @AfterEach
    public void cleanup() {
        SocialBridge.set(new SocialBridge() {});
        LadsGameBridge.set(new DefaultGameBridge());
    }

    private JsonObject members(String t, UUID leader, UUID... ids) {
        JsonObject o = SocialProtocol.msg(t, "leader", leader.toString());
        com.google.gson.JsonArray arr = new com.google.gson.JsonArray();
        for (UUID id : ids) {
            JsonObject m = new JsonObject();
            m.addProperty("id", id.toString());
            m.addProperty("name", id.equals(self) ? "Me" : id.equals(alice) ? "Alice" : "Bob");
            arr.add(m);
        }
        o.add("members", arr);
        return o;
    }

    private void joinParty(UUID leader, UUID... ids) {
        state.onMessage(members("party_state", leader, ids));
    }

    @Test
    public void inviteInShowsToastAndAcceptSendsAcceptOnly() {
        state.onMessage(SocialProtocol.msg("party_invite_in", "from", alice.toString(), "name", "Alice"));
        assertEquals(1, state.partyInvites().size());
        assertTrue(toasts.get(0).startsWith("Party Invite|Alice invited you to a party|party_accept:" + alice));
        state.handleAction("party_accept:" + alice);
        assertEquals("party_accept", sent.get(0).get("t").getAsString());
        assertEquals(alice.toString(), sent.get(0).get("from").getAsString());
        assertTrue(state.partyInvites().isEmpty());
        assertTrue(connects.isEmpty());
    }

    @Test
    public void inviteFromNonFriendIgnored() {
        state.onMessage(SocialProtocol.msg("party_invite_in", "from", stranger.toString(), "name", "Evil"));
        assertTrue(state.partyInvites().isEmpty());
        assertTrue(toasts.isEmpty());
    }

    @Test
    public void declineSendsDecline() {
        state.onMessage(SocialProtocol.msg("party_invite_in", "from", alice.toString()));
        state.declinePartyInvite(alice);
        assertEquals("party_decline", sent.get(0).get("t").getAsString());
        assertTrue(state.partyInvites().isEmpty());
    }

    @Test
    public void partyStateAndEnded() {
        joinParty(alice, alice, self, bob);
        assertEquals(alice, state.party().leader());
        assertEquals(3, state.party().members().size());
        assertFalse(state.isPartyLeader());
        joinParty(self, self, bob);
        assertTrue(state.isPartyLeader());
        state.onMessage(SocialProtocol.msg("party_ended"));
        assertNull(state.party());
    }

    @Test
    public void leaderOnlyRequestsAreNotSentByMembers() {
        joinParty(alice, alice, self);
        state.kickFromParty(alice);
        state.promoteInParty(alice);
        state.warp("play.example.com:25565");
        state.inviteToParty(bob);
        assertTrue(sent.isEmpty());
        state.leaveParty();
        assertEquals("party_leave", sent.get(0).get("t").getAsString());
    }

    @Test
    public void leaderSendsKickPromoteWarpAndInvite() {
        joinParty(self, self, alice);
        state.inviteToParty(bob);
        state.kickFromParty(alice);
        state.promoteInParty(alice);
        state.warp("play.example.com:25565", "Example");
        assertEquals(List.of("party_invite", "party_kick", "party_promote", "party_warp"), sent.stream().map(m -> m.get("t").getAsString()).toList());
        assertEquals("Example", sent.get(3).get("name").getAsString());
    }

    @Test
    public void inviteOnlyFriendsAndBadWarpServerNotSent() {
        state.inviteToParty(stranger);
        joinParty(self, self);
        state.warp("bad server\n");
        state.warp("x".repeat(300));
        state.warp("a/b");
        assertTrue(sent.isEmpty());
    }

    @Test
    public void warpIsOnlyAnOfferAndJoinConnectsOnlyAfterClick() {
        AtomicReference<SocialState.PartyWarp> seen = new AtomicReference<>();
        state.setPartyWarpListener(seen::set);
        joinParty(alice, alice, self);
        state.onMessage(SocialProtocol.msg("party_warp", "from", alice.toString(), "server", "play.example.com:25565", "name", "Example"));
        SocialState.PartyWarp w = state.pendingPartyWarp();
        assertNotNull(w);
        assertSame(w, seen.get());
        assertEquals("Example", w.serverName());
        assertTrue(toasts.get(toasts.size() - 1).contains("Alice is going to Example"));
        assertTrue(connects.isEmpty(), "no auto-connect");
        state.joinPartyWarp();
        assertEquals(List.of("play.example.com:25565"), connects);
        assertNull(state.pendingPartyWarp());
    }

    @Test
    public void ignoreDropsWarpWithoutConnecting() {
        joinParty(alice, alice, self);
        state.onMessage(SocialProtocol.msg("party_warp", "from", alice.toString(), "server", "a.b:1"));
        state.handleAction("party_warp_ignore:" + state.pendingPartyWarp().token());
        assertNull(state.pendingPartyWarp());
        assertTrue(connects.isEmpty());
    }

    @Test
    public void joinInsideWorldAsksBeforeLeaving() {
        inWorld = true;
        joinParty(alice, alice, self);
        state.onMessage(SocialProtocol.msg("party_warp", "from", alice.toString(), "server", "a.b:1"));
        state.joinPartyWarp();
        assertTrue(connects.isEmpty());
        SocialState.SwitchConfirm confirm = state.takePendingSwitch();
        assertNotNull(confirm);
        confirm.go().run();
        assertEquals(List.of("a.b:1"), connects);
    }

    @Test
    public void warpFromNonLeaderOrOutsideAPartyOrBadServerIgnored() {
        state.onMessage(SocialProtocol.msg("party_warp", "from", alice.toString(), "server", "a.b:1"));
        assertNull(state.pendingPartyWarp());
        joinParty(alice, alice, self, bob);
        state.onMessage(SocialProtocol.msg("party_warp", "from", bob.toString(), "server", "a.b:1"));
        state.onMessage(SocialProtocol.msg("party_warp", "from", alice.toString(), "server", "evil host/../x"));
        assertNull(state.pendingPartyWarp());
        // leadership changed after the offer: a stale offer from the old leader is not followed
        state.onMessage(SocialProtocol.msg("party_warp", "from", alice.toString(), "server", "a.b:1"));
        joinParty(bob, bob, self);
        assertNull(state.pendingPartyWarp());
        state.joinPartyWarp();
        assertTrue(connects.isEmpty());
    }

    @Test
    public void partyEndedAndNewLinkClearPendingWarp() {
        joinParty(alice, alice, self);
        state.onMessage(SocialProtocol.msg("party_warp", "from", alice.toString(), "server", "a.b:1"));
        state.onMessage(SocialProtocol.msg("party_ended"));
        assertNull(state.pendingPartyWarp());
        joinParty(alice, alice, self);
        state.onHello(new JsonObject());
        assertNull(state.party());
    }

    @Test
    public void errorTextForNotLeader() {
        assertEquals("Only the party leader can do that", SocialState.errorText(SocialProtocol.E_NOT_LEADER, "party", null));
    }
}
