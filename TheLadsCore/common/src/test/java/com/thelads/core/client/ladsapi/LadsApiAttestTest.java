package com.thelads.core.client.ladsapi;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The client's part of the anti-spoof handshake (PROTOCOL.md section 6): ask the relay after a hello with a nonce, send the token once. */
class LadsApiAttestTest {
    private static final String NONCE = "0123456789abcdef0123456789abcdef", SID = "fedcba9876543210fedcba9876543210";
    private static final String TOKEN = "v1.2000000120." + "A".repeat(86);

    private final List<byte[]> sent = new ArrayList<>();
    private final List<String> asked = new ArrayList<>();
    private int takenBack;

    @BeforeEach
    void setUp() {
        LadsApiClient.install(sent::add, "26.3");
        LadsApiClient.reset();
        LadsApiClient.setAttestor((nonce, sid) -> {
            if (nonce == null) takenBack++;
            else asked.add(nonce + "/" + sid);
        });
    }

    @AfterEach
    void tearDown() {
        LadsApiClient.reset();
        LadsApiClient.setAttestor(null);
    }

    private void serverHello(String extra) {
        LadsApiClient.receive(("{\"t\":\"hello\",\"v\":1,\"server\":\"Lads SMP\"" + extra + "}").getBytes(StandardCharsets.UTF_8));
    }

    private static String proof(String nonce) {
        return ",\"nonce\":\"" + nonce + "\",\"sid\":\"" + SID + "\"";
    }

    private JsonObject sent(int index) {
        return new Gson().fromJson(new String(sent.get(index), StandardCharsets.UTF_8), JsonObject.class);
    }

    @Test
    void tokenFollowsInASecondHello() {
        serverHello(proof(NONCE));
        assertEquals(1, sent.size(), "the first reply does not wait for the relay");
        assertFalse(sent(0).has("token"));
        assertEquals(List.of(NONCE + "/" + SID), asked);

        LadsApiClient.attested(NONCE, TOKEN);
        assertEquals(2, sent.size());
        JsonObject second = sent(1);
        assertEquals("hello", second.get("t").getAsString());
        assertEquals(1, second.get("v").getAsInt());
        assertEquals("26.3", second.get("mc").getAsString());
        assertEquals(TOKEN, second.get("token").getAsString());

        LadsApiClient.attested(NONCE, TOKEN);
        assertEquals(2, sent.size(), "a token goes out once");
    }

    @Test
    void oldPluginHelloAsksNothing() {
        serverHello("");
        assertTrue(asked.isEmpty());
        LadsApiClient.attested(NONCE, TOKEN); // nothing asked for it: never sent
        assertEquals(1, sent.size());
    }

    @Test
    void badNonceOrServerIdAsksNothing() {
        serverHello(",\"nonce\":\"short\",\"sid\":\"" + SID + "\"");
        serverHello(",\"nonce\":\"" + NONCE + "\"");
        serverHello(",\"nonce\":\"" + NONCE.toUpperCase() + "\",\"sid\":\"" + SID + "\"");
        serverHello(",\"nonce\":5,\"sid\":\"" + SID + "\"");
        serverHello(",\"nonce\":\"" + NONCE + "|x\",\"sid\":\"" + SID + "\"");
        assertTrue(asked.isEmpty());
    }

    @Test
    void neverSendsFirstOrToAnotherServer() {
        LadsApiClient.attested(NONCE, TOKEN); // no server hello at all
        assertTrue(sent.isEmpty());

        serverHello(proof(NONCE));
        LadsApiClient.attested("f".repeat(32), TOKEN); // an answer for some other nonce
        assertEquals(1, sent.size());

        LadsApiClient.serverSwitch(); // the proxy moved us: the token belongs to the old backend
        LadsApiClient.attested(NONCE, TOKEN);
        assertEquals(1, sent.size());

        serverHello(proof(NONCE));
        LadsApiClient.reset(); // disconnect
        LadsApiClient.attested(NONCE, TOKEN);
        assertEquals(2, sent.size(), "only the two first replies");
    }

    /** A slow relay link: the token may come after the lobby moved the player to another world. Same server, same nonce. */
    @Test
    void worldChangeOnTheSameServerKeepsTheWait() {
        LadsApiClient.joinGame(1L);
        serverHello(proof(NONCE));
        LadsApiClient.respawn(2L); // another world: the policy goes, the wait for the token stays
        LadsApiClient.dimension(7);
        LadsApiClient.dimension(8);
        assertEquals(0, takenBack);
        LadsApiClient.attested(NONCE, TOKEN);
        assertEquals(2, sent.size());
        assertEquals(TOKEN, sent(1).get("token").getAsString());
    }

    @Test
    void goneServerTakesTheRequestBack() {
        serverHello(proof(NONCE));
        LadsApiClient.serverSwitch();
        assertEquals(1, takenBack);
        LadsApiClient.serverSwitch(); // nothing waits any more
        assertEquals(1, takenBack);

        serverHello(proof(NONCE));
        LadsApiClient.receive("{\"t\":\"reset\"}".getBytes(StandardCharsets.UTF_8)); // the proxy plugin's server switch
        assertEquals(2, takenBack);

        serverHello(proof(NONCE));
        LadsApiClient.reset();
        assertEquals(3, takenBack);

        // 1.8.9 has no seed in its Respawn: a dimension change is how BungeeCord switches its backends
        LadsApiClient.joinGame(0L);
        LadsApiClient.dimension(0);
        serverHello(proof(NONCE));
        LadsApiClient.dimension(-1);
        assertEquals(4, takenBack);
        LadsApiClient.attested(NONCE, TOKEN);
        assertEquals(4, sent.size(), "only the four first replies");
    }

    @Test
    void malformedTokenIsNotSent() {
        for (String bad : new String[] {null, "", "v1.1.short", "v2.2000000120." + "A".repeat(86), "v1.2000000120." + "A".repeat(85) + "\""}) {
            serverHello(proof(NONCE));
            LadsApiClient.serverSwitch(); // lifts the two-replies-per-backend cap for the next round
            serverHello(proof(NONCE));
            int before = sent.size();
            LadsApiClient.attested(NONCE, bad);
            assertEquals(before, sent.size(), String.valueOf(bad));
            LadsApiClient.reset();
        }
    }

    @Test
    void sameNonceIsProvenOnce() {
        serverHello(proof(NONCE));
        LadsApiClient.attested(NONCE, TOKEN);
        serverHello(proof(NONCE)); // the plugin's hello again after a world change: same session, same nonce
        assertEquals(1, asked.size());
        assertEquals(3, sent.size());

        LadsApiClient.serverSwitch();
        serverHello(proof("a".repeat(32))); // another backend, another nonce
        assertEquals(2, asked.size());
    }

    @Test
    void relayIsAskedOnlyWhenAReplyWentOut() {
        for (int i = 0; i < 5; i++) serverHello(proof(NONCE)); // replies are capped at 2 per backend
        assertEquals(2, sent.size());
        assertEquals(2, asked.size());
    }

    @Test
    void withoutARelayNothingChanges() {
        LadsApiClient.setAttestor(null);
        serverHello(proof(NONCE));
        assertEquals(1, sent.size());
        LadsApiClient.attested(NONCE, TOKEN);
        assertEquals(1, sent.size());
    }
}
