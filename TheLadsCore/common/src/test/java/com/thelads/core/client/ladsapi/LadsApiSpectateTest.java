package com.thelads.core.client.ladsapi;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The client's part of spectating (PROTOCOL.md section 7): the state lives only while the server says so. */
class LadsApiSpectateTest {
    private static final String TARGET = "069a79f4-44e9-4726-a5be-fca90e38aaf5";

    private final List<String> sent = new ArrayList<>();
    private long now = 1_000_000;

    @BeforeEach
    void setUp() {
        LadsApiClient.install(bytes -> sent.add(new String(bytes, StandardCharsets.UTF_8)), "26.3");
        LadsApiClient.reset();
        LadsApiClient.clock = () -> now;
    }

    @AfterEach
    void tearDown() {
        LadsApiClient.reset();
    }

    private void server(String json) {
        now += 1000; // outside the incoming rate limit
        LadsApiClient.receive(json.getBytes(StandardCharsets.UTF_8));
    }

    private void hello() {
        server("{\"t\":\"hello\",\"v\":1,\"server\":\"The Lads Network\"}");
        sent.clear();
    }

    private void pov(String extra) {
        server("{\"t\":\"spectate\",\"mode\":\"pov\",\"target\":\"" + TARGET + "\"" + extra + "}");
    }

    @Test
    void nothingFromAServerThatNeverSaidHello() {
        pov(",\"switch\":true");
        assertFalse(SpectateState.active().on());
        assertFalse(LadsApiClient.spectateRequest(true));
        assertTrue(sent.isEmpty());
    }

    @Test
    void povCarriesTargetNameAndSwitch() {
        hello();
        pov(",\"name\":\"Steve\",\"switch\":true");
        SpectateState s = SpectateState.active();
        assertEquals(SpectateState.Mode.POV, s.mode);
        assertTrue(s.pov());
        assertEquals(UUID.fromString(TARGET), s.target);
        assertEquals("Steve", s.name);
        assertTrue(s.canSwitch);

        pov(""); // a full state every time: no name, no switch
        s = SpectateState.active();
        assertEquals("", s.name);
        assertFalse(s.canSwitch);
    }

    @Test
    void freeHasNoTarget() {
        hello();
        server("{\"t\":\"spectate\",\"mode\":\"free\",\"target\":\"" + TARGET + "\",\"switch\":true}");
        SpectateState s = SpectateState.active();
        assertEquals(SpectateState.Mode.FREE, s.mode);
        assertTrue(s.on());
        assertFalse(s.pov());
        assertNull(s.target);
        assertTrue(s.canSwitch);
    }

    @Test
    void whatTheClientCannotUseIsOff() {
        hello();
        for (String bad : List.of(
            "{\"t\":\"spectate\",\"mode\":\"off\"}",
            "{\"t\":\"spectate\"}",
            "{\"t\":\"spectate\",\"mode\":\"cinematic\",\"target\":\"" + TARGET + "\"}",
            "{\"t\":\"spectate\",\"mode\":\"pov\"}",
            "{\"t\":\"spectate\",\"mode\":\"pov\",\"target\":\"not-a-uuid\"}",
            "{\"t\":\"spectate\",\"mode\":\"pov\",\"target\":7}",
            "{\"t\":\"spectate\",\"mode\":[\"pov\"],\"target\":\"" + TARGET + "\"}")) {
            pov(",\"switch\":true");
            assertTrue(SpectateState.active().pov());
            server(bad);
            assertFalse(SpectateState.active().on(), bad);
            assertNull(SpectateState.active().target, bad);
        }
    }

    @Test
    void switchMustBeATrueBoolean() {
        hello();
        pov(",\"switch\":\"true\"");
        assertFalse(SpectateState.active().canSwitch);
        pov(",\"switch\":1");
        assertFalse(SpectateState.active().canSwitch);
        pov(",\"switch\":{}");
        assertTrue(SpectateState.active().pov());
        assertFalse(SpectateState.active().canSwitch);
    }

    @Test
    void theNameIsPlainAndShort() {
        assertEquals("Steve", SpectateState.cleanName("§b§lSteve§r"));
        assertEquals("ab", SpectateState.cleanName(" a\n\tb\u0000 "));
        assertEquals(32, SpectateState.cleanName("x".repeat(100)).length());
        assertEquals("", SpectateState.cleanName(null));
        assertEquals("", SpectateState.cleanName("§"));
    }

    @Test
    void theStateEndsWithTheServer() {
        hello();
        pov("");
        server("{\"t\":\"reset\"}");
        assertFalse(SpectateState.active().on(), "proxy reset");

        pov("");
        assertTrue(SpectateState.active().on());
        LadsApiClient.joinGame(42L);
        assertFalse(SpectateState.active().on(), "another backend");

        pov("");
        LadsApiClient.respawn(42L); // death in the same world
        assertTrue(SpectateState.active().on());
        LadsApiClient.respawn(43L);
        assertFalse(SpectateState.active().on(), "another world: the server sends its state again after the new hello");

        hello();
        pov("");
        hello();
        assertFalse(SpectateState.active().on(), "a new hello starts clean");

        pov("");
        LadsApiClient.serverSwitch();
        assertFalse(SpectateState.active().on(), "configuration phase");

        hello();
        pov("");
        LadsApiClient.reset();
        assertFalse(SpectateState.active().on(), "disconnect");
    }

    @Test
    void theRequestNeedsTheServersOffer() {
        hello();
        assertFalse(LadsApiClient.spectateRequest(true), "not spectating");

        pov("");
        assertFalse(LadsApiClient.spectateRequest(true), "no switch offered");
        assertTrue(sent.isEmpty());

        pov(",\"switch\":true");
        assertTrue(LadsApiClient.spectateRequest(true));
        assertEquals(List.of("{\"t\":\"spectate\",\"act\":\"next\"}"), sent);

        now += 250;
        assertTrue(LadsApiClient.spectateRequest(false));
        assertEquals("{\"t\":\"spectate\",\"act\":\"prev\"}", sent.get(1));

        server("{\"t\":\"spectate\",\"mode\":\"off\"}");
        assertFalse(LadsApiClient.spectateRequest(true), "ended");
        assertEquals(2, sent.size());
    }

    @Test
    void aHeldKeyAsksOnce() {
        hello();
        pov(",\"switch\":true");
        assertTrue(LadsApiClient.spectateRequest(true));
        now += 50;
        assertFalse(LadsApiClient.spectateRequest(true));
        now += 100;
        assertFalse(LadsApiClient.spectateRequest(false));
        now += 50;
        assertTrue(LadsApiClient.spectateRequest(true));
        assertEquals(2, sent.size());
    }

    @Test
    void theRequestWorksInFreeFlightToo() {
        hello();
        server("{\"t\":\"spectate\",\"mode\":\"free\",\"switch\":true}");
        assertTrue(LadsApiClient.spectateRequest(true));
        assertEquals(1, sent.size());
    }
}
