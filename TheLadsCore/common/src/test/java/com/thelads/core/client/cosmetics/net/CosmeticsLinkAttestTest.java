package com.thelads.core.client.cosmetics.net;

import com.thelads.core.client.bridge.DefaultGameBridge;
import com.thelads.core.client.bridge.LadsGameBridge;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The relay's attest answer (cosmetics.py "attest") on its way to the game thread. */
class CosmeticsLinkAttestTest {
    private static final String NONCE = "0123456789abcdef0123456789abcdef", SID = "fedcba9876543210fedcba9876543210";
    private static final UUID SELF = UUID.randomUUID();

    private static CosmeticsLink link() {
        return new CosmeticsLink("ws://127.0.0.1:1/cosmetics/ws");
    }

    @Test
    void answerForTheAskedNonceIsHandedOverOnce() {
        CosmeticsLink link = link();
        link.requestAttest(NONCE, SID);
        link.handle("{\"t\":\"attest\",\"nonce\":\"" + NONCE + "\",\"token\":\"v1.1.sig\"}", SELF);
        assertArrayEquals(new String[] {NONCE, "v1.1.sig"}, link.takeAttested());
        assertNull(link.takeAttested());
    }

    @Test
    void unaskedOrForeignAnswersAreDropped() {
        CosmeticsLink link = link();
        link.handle("{\"t\":\"attest\",\"nonce\":\"" + NONCE + "\",\"token\":\"v1.1.sig\"}", SELF); // nothing was asked
        assertNull(link.takeAttested());

        link.requestAttest(NONCE, SID);
        link.handle("{\"t\":\"attest\",\"nonce\":\"" + SID + "\",\"token\":\"v1.1.sig\"}", SELF); // another nonce
        link.handle("{\"t\":\"attest\",\"token\":\"v1.1.sig\"}", SELF);
        assertNull(link.takeAttested());
    }

    @Test
    void errorAnswerEndsTheWaitWithoutAToken() {
        CosmeticsLink link = link();
        link.requestAttest(NONCE, SID);
        link.handle("{\"t\":\"attest\",\"nonce\":\"" + NONCE + "\",\"error\":\"unavailable\"}", SELF);
        assertNull(link.takeAttested());
        link.handle("{\"t\":\"attest\",\"nonce\":\"" + NONCE + "\",\"token\":\"v1.1.sig\"}", SELF); // the wait is over
        assertNull(link.takeAttested());
    }

    @Test
    void newerRequestReplacesTheOlder() {
        CosmeticsLink link = link();
        link.requestAttest(NONCE, SID);
        link.requestAttest(SID, SID);
        link.handle("{\"t\":\"attest\",\"nonce\":\"" + NONCE + "\",\"token\":\"old\"}", SELF);
        assertNull(link.takeAttested());
        link.handle("{\"t\":\"attest\",\"nonce\":\"" + SID + "\",\"token\":\"new\"}", SELF);
        assertArrayEquals(new String[] {SID, "new"}, link.takeAttested());
    }

    /** In game the link took 65 s to come up (11-10): the request waits for it, and only a relay that was asked can time out. */
    @Test
    void unsentRequestWaitsForASlowLink() {
        LadsGameBridge before = LadsGameBridge.get();
        LadsGameBridge.set(new DefaultGameBridge() {
            @Override public boolean isIngame() { return true; }
            @Override public String getServerAddress() { return "lobby.example.org"; }
            @Override public Session session() { return null; } // keeps the link thread from starting
        });
        try {
            CosmeticsLink link = link();
            link.requestAttest(NONCE, SID);
            CosmeticsLink.Attest ask = link.attest();
            assertEquals(0, ask.sentAt);
            for (int i = 0; i < 20; i++) link.sample(false);
            assertSame(ask, link.attest(), "not asked yet: nothing to time out");

            ask.sentAt = System.currentTimeMillis() - CosmeticsLink.ATTEST_WAIT_MS + 5_000;
            for (int i = 0; i < 20; i++) link.sample(false);
            assertSame(ask, link.attest());

            ask.sentAt = System.currentTimeMillis() - CosmeticsLink.ATTEST_WAIT_MS - 1;
            for (int i = 0; i < 20; i++) link.sample(false);
            assertNull(link.attest(), "the relay was asked and never answered");
        } finally {
            LadsGameBridge.set(before);
        }
    }

    @Test
    void goneServerTakesTheRequestBack() {
        CosmeticsLink link = link();
        link.requestAttest(NONCE, SID);
        link.requestAttest(null, null);
        assertNull(link.attest());
        link.handle("{\"t\":\"attest\",\"nonce\":\"" + NONCE + "\",\"token\":\"v1.1.sig\"}", SELF);
        assertNull(link.takeAttested());
    }

    /** 1.21.11 and 26.2 draw no relay cosmetics: their tick brings the link up without a room, and only while a server waits. */
    @Test
    void proofOnlyTickIsUpOnlyWhileAServerWaits() {
        LadsGameBridge before = LadsGameBridge.get();
        LadsGameBridge.set(new DefaultGameBridge() {
            @Override public boolean isIngame() { return true; }
            @Override public String getServerAddress() { return "lobby.example.org"; }
            @Override public Session session() { return new Session(SELF, "Lad", "token"); }
        });
        try {
            CosmeticsLink link = link();
            for (int i = 0; i < 20; i++) link.sample(false);
            assertNull(link.want());
            link.requestAttest(NONCE, SID);
            for (int i = 0; i < 20; i++) link.sample(false);
            assertEquals(new CosmeticsLink.Want(SELF, null), link.want());
            link.handle("{\"t\":\"attest\",\"nonce\":\"" + NONCE + "\",\"token\":\"v1.1.sig\"}", SELF);
            for (int i = 0; i < 20; i++) link.sample(false);
            assertNull(link.want());
        } finally {
            LadsGameBridge.set(before);
        }
    }
}
