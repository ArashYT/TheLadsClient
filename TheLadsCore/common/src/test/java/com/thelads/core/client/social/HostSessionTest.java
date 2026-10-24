package com.thelads.core.client.social;

import com.google.gson.JsonObject;
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
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

public class HostSessionTest {

    private static class FakeP2PSession implements P2PSession {
        private volatile boolean closed;
        private Consumer<String> closeHandler;
        private final List<JsonObject> receivedSignals = new ArrayList<>();
        private final boolean relayed;
        private final long bytes;

        FakeP2PSession(boolean relayed, long bytes) {
            this.relayed = relayed;
            this.bytes = bytes;
        }

        @Override
        public void onSignal(JsonObject data) {
            receivedSignals.add(data);
        }

        @Override public InputStream in(int stream) { return new ByteArrayInputStream(new byte[0]); }
        @Override public OutputStream out(int stream) { return new ByteArrayOutputStream(); }
        @Override public boolean relayed() { return relayed; }
        @Override public long bytes() { return bytes; }

        @Override
        public void close(String reason) {
            if (closed) return;
            closed = true;
            if (closeHandler != null) closeHandler.accept(reason);
        }

        @Override
        public void onClose(Consumer<String> reason) {
            this.closeHandler = reason;
        }
    }

    private HostSession session;
    private List<JsonObject> sentEnds;
    private final List<SocialBridge.GuestInfo> online = new ArrayList<>();

    @BeforeEach
    public void setup() {
        HostSession.skipLoopbackForTest = true;
        SocialBridge bridge = new SocialBridge() {
            @Override public List<GuestInfo> guests() { return online; }
        };
        SocialState state = new SocialState(() -> bridge, () -> null, null);
        sentEnds = new ArrayList<>();
        state.setClientSender(sentEnds::add);
        session = state.hostSession();
    }

    private void join(UUID id) {
        online.add(new SocialBridge.GuestInfo(id, "guest", 0, false, "survival"));
    }

    @AfterEach
    public void cleanup() {
        HostSession.skipLoopbackForTest = false;
        HostSession.setP2PStarter(null);
        if (session != null && session.isHosting()) {
            session.stop("Test cleanup");
        }
    }

    @Test
    public void loginFilterRejectsUnknownUuid() {
        HostOptions options = new HostOptions();
        options.scope = HostOptions.Scope.FRIENDS;
        options.approval = HostOptions.Approval.ASK;
        options.maxPlayers = 4;

        session.initForTest(options, 25565);
        Predicate<UUID> filter = session.loginFilter();
        assertNotNull(filter);

        UUID guest1 = UUID.randomUUID();
        UUID guest2 = UUID.randomUUID();

        assertFalse(filter.test(guest1), "Unknown UUID must be rejected by login filter");

        session.accept(guest1);
        assertTrue(filter.test(guest1), "Accepted UUID must pass login filter");

        assertFalse(filter.test(guest2), "Unaccepted UUID must still be rejected");
    }

    @Test
    public void maxPlayersEnforced() {
        HostOptions options = new HostOptions();
        options.scope = HostOptions.Scope.FRIENDS;
        options.approval = HostOptions.Approval.ASK;
        options.maxPlayers = 2;

        session.initForTest(options, 25565);
        Predicate<UUID> filter = session.loginFilter();

        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();
        UUID p3 = UUID.randomUUID();

        session.accept(p1);
        session.accept(p2);
        session.accept(p3);

        join(p1);
        join(p2);

        assertEquals(2, session.activeGuestCount());

        assertTrue(filter.test(p1));
        assertTrue(filter.test(p2));

        assertFalse(filter.test(p3), "3rd player must be rejected when maxPlayers=2 is reached");

        online.removeIf(g -> g.id().equals(p1));
        assertEquals(1, session.activeGuestCount());
        assertTrue(filter.test(p3), "Player can join once a slot frees up");
    }

    @Test
    public void buffersSignalsUntilSessionCompletes() {
        CompletableFuture<P2PSession> future = new CompletableFuture<>();
        HostSession.setP2PStarter((sid, host, cfg, sendSignal) -> future);

        HostOptions options = new HostOptions();
        options.scope = HostOptions.Scope.FRIENDS;
        session.initForTest(options, 25565);

        UUID guest = UUID.randomUUID();
        String sid = "test-sid-1";
        P2PSession.IceConfig ice = new P2PSession.IceConfig(Collections.emptyList(), Collections.emptyList(), false);

        session.handleP2PStart(sid, guest, ice, sig -> {});
        assertTrue(session.ownsSid(sid));

        // Signal arrives BEFORE P2PSession future completes
        JsonObject sig1 = new JsonObject();
        sig1.addProperty("candidate", "candidate:1");
        session.onSignal(sid, sig1);

        JsonObject sig2 = new JsonObject();
        sig2.addProperty("candidate", "candidate:2");
        session.onSignal(sid, sig2);

        // Future completes with FakeP2PSession
        FakeP2PSession p2p = new FakeP2PSession(false, 500);
        future.complete(p2p);

        // Assert signals were buffered and replayed in order
        assertEquals(2, p2p.receivedSignals.size());
        assertEquals("candidate:1", p2p.receivedSignals.get(0).get("candidate").getAsString());
        assertEquals("candidate:2", p2p.receivedSignals.get(1).get("candidate").getAsString());

        // Subsequent signals after start are forwarded immediately
        JsonObject sig3 = new JsonObject();
        sig3.addProperty("candidate", "candidate:3");
        session.onSignal(sid, sig3);
        assertEquals(3, p2p.receivedSignals.size());
        assertEquals("candidate:3", p2p.receivedSignals.get(2).get("candidate").getAsString());
    }

    @Test
    public void revokeGuestClosesP2PSessionAndSendsP2PEnd() {
        FakeP2PSession p2p = new FakeP2PSession(true, 4096);
        HostSession.setP2PStarter((sid, host, cfg, sendSignal) -> CompletableFuture.completedFuture(p2p));

        HostOptions options = new HostOptions();
        options.scope = HostOptions.Scope.FRIENDS;
        session.initForTest(options, 25565);

        UUID guest = UUID.randomUUID();
        String sid = "test-sid-revoke";
        session.accept(guest);
        session.handleP2PStart(sid, guest, new P2PSession.IceConfig(Collections.emptyList(), Collections.emptyList(), false), sig -> {});

        assertTrue(session.ownsSid(sid));
        assertTrue(session.acceptedUuids().contains(guest));

        // Host revokes the guest
        session.revoke(guest);

        assertFalse(session.acceptedUuids().contains(guest));
        assertTrue(p2p.closed, "P2P session must be closed on revoke");

        // Verify p2p_end was sent exactly once with relayed=true, bytes=4096
        assertEquals(1, sentEnds.size());
        JsonObject endMsg = sentEnds.get(0);
        assertEquals("p2p_end", endMsg.get("t").getAsString());
        assertEquals(sid, endMsg.get("sid").getAsString());
        assertEquals("Revoked", endMsg.get("reason").getAsString());
        assertTrue(endMsg.get("relayed").getAsBoolean());
        assertEquals(4096L, endMsg.get("bytes").getAsLong());
    }
}
