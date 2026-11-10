package com.thelads.core.client.cosmetics.net;

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
}
