package com.thelads.core.client.social;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

public class HostSessionTest {

    private HostSession session;

    @BeforeEach
    public void setup() {
        session = new HostSession();
    }

    @AfterEach
    public void cleanup() {
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

        // guest1 is not accepted yet -> rejected
        assertFalse(filter.test(guest1), "Unknown UUID must be rejected by login filter");

        // accept guest1
        session.accept(guest1);
        assertTrue(filter.test(guest1), "Accepted UUID must pass login filter");

        // guest2 is still unknown -> rejected
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
        session.accept(p3); // Attempts to accept beyond maxPlayers

        session.onGuestConnected(p1);
        session.onGuestConnected(p2);

        assertEquals(2, session.activeGuestCount());

        // Both p1 and p2 are connected
        assertTrue(filter.test(p1));
        assertTrue(filter.test(p2));

        // p3 cannot join because maxPlayers (2) is reached
        assertFalse(filter.test(p3), "3rd player must be rejected when maxPlayers=2 is reached");

        // When p1 disconnects, p3 can join
        session.onGuestDisconnected(p1);
        assertEquals(1, session.activeGuestCount());
        assertTrue(filter.test(p3), "Player can join once a slot frees up");
    }
}
