package com.thelads.ladsapi.test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.thelads.ladsapi.api.LadsPlayer;
import com.thelads.ladsapi.config.LadsConfig;
import com.thelads.ladsapi.rules.LadsRules;
import com.thelads.ladsapi.session.SessionManager;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

/** PROTOCOL.md section 7: the spectate message to a Lads Client, and the client's request for another target. */
public class SpectateTest {
    private static final String CLIENT_HELLO = "{\"t\":\"hello\",\"v\":1,\"lads\":\"1.13.9\",\"mc\":\"26.3\"}";
    private static final UUID TARGET = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");

    @TempDir Path tempDir;
    private FakePlatform platform;
    private SessionManager manager;
    private FakePlayerHandle player;
    private final List<String> asked = new ArrayList<>();

    @BeforeEach
    void setUp() {
        platform = new FakePlatform("paper", false, tempDir);
        manager = new SessionManager(platform, new LadsConfig(), new LadsRules());
        manager.addSpectateListener((p, act) -> asked.add(p.uuid() + " " + act));
        player = new FakePlayerHandle(UUID.randomUUID(), "Watcher", "pvp");
        platform.addPlayer(player);
        manager.join(player);
        platform.advanceTicks(1);
    }

    private LadsPlayer lads() {
        send(CLIENT_HELLO);
        player.getSentPayloads().clear();
        return manager.player(player.uuid()).orElseThrow();
    }

    private void send(String json) {
        manager.receive(player, json.getBytes(StandardCharsets.UTF_8));
    }

    private static JsonObject obj(String json) {
        return JsonParser.parseString(json).getAsJsonObject();
    }

    @Test
    void povNamesTheTargetAndRepeatsSendNothing() {
        LadsPlayer p = lads();
        p.spectate(TARGET, "Steve", true);
        assertEquals(1, player.getSentPayloads().size());
        JsonObject msg = obj(player.getLastSentString());
        assertEquals("spectate", msg.get("t").getAsString());
        assertEquals("pov", msg.get("mode").getAsString());
        assertEquals(TARGET.toString(), msg.get("target").getAsString());
        assertEquals("Steve", msg.get("name").getAsString());
        assertTrue(msg.get("switch").getAsBoolean());

        p.spectate(TARGET, "Steve", true); // a plugin's spectator loop calls it every tick
        assertEquals(1, player.getSentPayloads().size());

        UUID other = UUID.randomUUID();
        p.spectate(other, "N".repeat(40), false);
        assertEquals(2, player.getSentPayloads().size());
        msg = obj(player.getLastSentString());
        assertEquals(other.toString(), msg.get("target").getAsString());
        assertEquals(32, msg.get("name").getAsString().length());
        assertFalse(msg.has("switch"));
    }

    @Test
    void freeAndEnd() {
        LadsPlayer p = lads();
        p.spectateEnd(); // never spectated: nothing to take back
        assertTrue(player.getSentPayloads().isEmpty());

        p.spectateFree(true);
        JsonObject msg = obj(player.getLastSentString());
        assertEquals("free", msg.get("mode").getAsString());
        assertFalse(msg.has("target"));

        p.spectateEnd();
        assertEquals("{\"t\":\"spectate\",\"mode\":\"off\"}", player.getLastSentString());
        p.spectateEnd();
        assertEquals(2, player.getSentPayloads().size());

        p.spectate(null, "Nobody", true); // no target is no POV
        assertEquals(2, player.getSentPayloads().size());
    }

    @Test
    void aPlayerWithoutTheClientGetsNothing() {
        assertTrue(manager.player(player.uuid()).isEmpty());
        player.getSentPayloads().clear();
        manager.getSessions().get(player.uuid()).sendSpectate("pov", TARGET, "Steve", true);
        assertTrue(player.getSentPayloads().isEmpty());
        send("{\"t\":\"spectate\",\"act\":\"next\"}");
        assertTrue(asked.isEmpty());
    }

    @Test
    void theRequestCountsOnlyWhileTheServerOfferedTheSwitch() {
        LadsPlayer p = lads();
        send("{\"t\":\"spectate\",\"act\":\"next\"}");
        assertTrue(asked.isEmpty(), "not spectating");

        p.spectate(TARGET, "Steve", false);
        send("{\"t\":\"spectate\",\"act\":\"next\"}");
        assertTrue(asked.isEmpty(), "no switch offered");

        p.spectate(TARGET, "Steve", true);
        send("{\"t\":\"spectate\",\"act\":\"next\"}");
        send("{\"t\":\"spectate\",\"act\":\"prev\"}");
        assertEquals(List.of(player.uuid() + " next", player.uuid() + " prev"), asked);

        // The client names no target and no other action exists: all of these are dropped.
        send("{\"t\":\"spectate\",\"act\":\"exit\"}");
        send("{\"t\":\"spectate\",\"act\":7}");
        send("{\"t\":\"spectate\"}");
        send("{\"t\":\"spectate\",\"act\":[\"next\"],\"target\":\"" + TARGET + "\"}");
        assertEquals(2, asked.size());

        p.spectateEnd();
        send("{\"t\":\"spectate\",\"act\":\"next\"}");
        assertEquals(2, asked.size(), "ended");
    }

    /** The hello's caps say what the client does itself; a plugin picks the camera mode by it (PvPArena). */
    @Test
    void theHelloNamesWhatTheClientCanDo() {
        assertFalse(lads().can("spectate"), "a hello without caps: an older client");

        FakePlayerHandle other = new FakePlayerHandle(UUID.randomUUID(), "Camera", "pvp");
        platform.addPlayer(other);
        manager.join(other);
        platform.advanceTicks(1);
        manager.receive(other, ("{\"t\":\"hello\",\"v\":1,\"lads\":\"1.13.9\",\"mc\":\"26.3\",\"caps\":[\"spectate\",7,null,"
            + "[\"x\"],\"Not Plain\",\"" + "a".repeat(25) + "\"]}").getBytes(StandardCharsets.UTF_8));
        LadsPlayer p = manager.player(other.uuid()).orElseThrow();
        assertTrue(p.can("spectate"));
        assertFalse(p.can("Not Plain"));
        assertFalse(p.can("a".repeat(25)));
        assertFalse(p.can(null));

        FakePlayerHandle junk = new FakePlayerHandle(UUID.randomUUID(), "Junk", "pvp");
        platform.addPlayer(junk);
        manager.join(junk);
        platform.advanceTicks(1);
        manager.receive(junk, "{\"t\":\"hello\",\"v\":1,\"lads\":\"1.13.9\",\"mc\":\"26.3\",\"caps\":\"spectate\"}".getBytes(StandardCharsets.UTF_8));
        assertFalse(manager.player(junk.uuid()).orElseThrow().can("spectate"), "caps must be an array");
    }

    @Test
    void aBrokenListenerDoesNotStopTheOthers() {
        SessionManager m = new SessionManager(platform, new LadsConfig(), new LadsRules());
        List<String> heard = new ArrayList<>();
        m.addSpectateListener((p, act) -> { throw new IllegalStateException("boom"); });
        m.addSpectateListener((p, act) -> heard.add(act));
        m.join(player);
        platform.advanceTicks(1);
        m.receive(player, CLIENT_HELLO.getBytes(StandardCharsets.UTF_8));
        m.player(player.uuid()).orElseThrow().spectateFree(true);
        m.receive(player, "{\"t\":\"spectate\",\"act\":\"prev\"}".getBytes(StandardCharsets.UTF_8));
        assertEquals(List.of("prev"), heard);
    }

    /** The client drops its spectate state on the hello a world change brings (PROTOCOL.md section 4), so it follows the presence. */
    @Test
    void aWorldChangeSendsTheStateAgain() {
        LadsPlayer p = lads();
        p.spectate(TARGET, "Steve", true);
        player.getSentPayloads().clear();

        player.setWorld("pvp-duels");
        manager.worldChanged(player);
        List<String> types = player.getSentStrings().stream().map(s -> obj(s).get("t").getAsString()).toList();
        assertEquals(List.of("hello", "policy", "presence", "spectate"), types);
        assertEquals(TARGET.toString(), obj(player.getLastSentString()).get("target").getAsString());

        p.spectateEnd();
        player.getSentPayloads().clear();
        player.setWorld("pvp");
        manager.worldChanged(player);
        types = player.getSentStrings().stream().map(s -> obj(s).get("t").getAsString()).toList();
        assertEquals(List.of("hello", "policy", "presence"), types);
    }
}
