package com.thelads.ladsapi.test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.thelads.ladsapi.config.LadsConfig;
import com.thelads.ladsapi.rules.LadsRules;
import com.thelads.ladsapi.rules.ModuleRule;
import com.thelads.ladsapi.session.LadsSession;
import com.thelads.ladsapi.session.SessionManager;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The client resets on a JoinGame / seed-changing Respawn / proxy switch and then drops every policy until a new hello arrives
 * (PROTOCOL.md section 4), so a world change or proxy switch must replay hello -> policy -> presence, not send a bare policy.
 */
public class WorldChangeResyncTest {
    private static final String CLIENT_HELLO = "{\"t\":\"hello\",\"v\":1,\"lads\":\"1.11.1\",\"mc\":\"26.3\"}";

    private static String type(String json) {
        return JsonParser.parseString(json).getAsJsonObject().get("t").getAsString();
    }

    private static JsonObject obj(String json) {
        return JsonParser.parseString(json).getAsJsonObject();
    }

    private static void clientHello(SessionManager manager, FakePlayerHandle player) {
        manager.receive(player, CLIENT_HELLO.getBytes(StandardCharsets.UTF_8));
    }

    /** ladsapi:A/3, B/6: Velocity proxy-rules mode lost every policy after the first server switch. */
    @Test
    public void proxySwitchReplaysTheHandshakeForTheNewBackend(@TempDir Path tempDir) {
        FakePlatform platform = new FakePlatform("velocity", true, tempDir);
        LadsConfig config = new LadsConfig();
        config.setProxyRules(true);
        LadsRules rules = new LadsRules();
        rules.getWorlds().put("survival", Map.of("Fullbright", new ModuleRule("block")));
        SessionManager manager = new SessionManager(platform, config, rules);

        FakePlayerHandle player = new FakePlayerHandle(UUID.randomUUID(), "Proxied", "lobby");
        platform.addPlayer(player);
        manager.join(player);
        platform.advanceTicks(1);
        clientHello(manager, player);

        // join + first handshake: hello, policy (no per-backend rule in the lobby), presence. No extra proxy reset.
        assertEquals(List.of("hello", "policy", "presence"), player.getSentStrings().stream().map(WorldChangeResyncTest::type).toList());
        assertFalse(obj(player.getSentStrings().get(1)).getAsJsonObject("modules").has("Fullbright"));
        player.getSentPayloads().clear();

        // /server survival
        player.setWorld("survival");
        manager.worldChanged(player);

        List<String> sent = player.getSentStrings();
        assertEquals(List.of("hello", "policy", "presence"), sent.stream().map(WorldChangeResyncTest::type).toList());
        JsonObject policy = obj(sent.get(1));
        assertEquals(2, policy.get("rev").getAsInt());
        assertEquals("block", policy.getAsJsonObject("modules").get("Fullbright").getAsString());
        assertTrue(obj(sent.get(2)).get("full").getAsBoolean());

        // The client answers the new hello; the session is already acked, so the answer must not trigger another policy.
        player.getSentPayloads().clear();
        clientHello(manager, player);
        assertTrue(player.getSentPayloads().isEmpty());
    }

    /** B/6: with proxy-rules off the backends own the policy; the proxy must not start sessions or send anything from join(). */
    @Test
    public void proxyWithoutProxyRulesRunsNoSession(@TempDir Path tempDir) {
        FakePlatform platform = new FakePlatform("velocity", true, tempDir);
        SessionManager manager = new SessionManager(platform, new LadsConfig(), new LadsRules());
        FakePlayerHandle player = new FakePlayerHandle(UUID.randomUUID(), "Proxied", "lobby");
        platform.addPlayer(player);

        manager.join(player);
        platform.advanceTicks(200);

        assertTrue(manager.getSessions().isEmpty());
        assertTrue(player.getSentPayloads().isEmpty());
        assertFalse(player.isKicked());
    }

    /** ladsapi:A/2: Paper worlds with different seeds make the client reset, so the world change needs hello + policy. */
    @Test
    public void paperWorldChangeReplaysHelloOnlyWhenTheWorldDiffers(@TempDir Path tempDir) {
        FakePlatform platform = new FakePlatform("paper", false, tempDir);
        LadsRules rules = new LadsRules();
        rules.getWorlds().put("pvp", Map.of("Fullbright", new ModuleRule("block")));
        SessionManager manager = new SessionManager(platform, new LadsConfig(), rules);
        FakePlayerHandle player = new FakePlayerHandle(UUID.randomUUID(), "Walker", "world");
        platform.addPlayer(player);
        manager.join(player);
        platform.advanceTicks(1);
        clientHello(manager, player);
        player.getSentPayloads().clear();

        player.setWorld("pvp");
        manager.worldChanged(player);
        assertEquals(List.of("hello", "policy", "presence"), player.getSentStrings().stream().map(WorldChangeResyncTest::type).toList());
        assertEquals("block", obj(player.getSentStrings().get(1)).getAsJsonObject("modules").get("Fullbright").getAsString());
        player.getSentPayloads().clear();

        // The same world again (a second event for the same move) sends nothing.
        manager.worldChanged(player);
        assertTrue(player.getSentPayloads().isEmpty());
    }

    /** ladsapi:A/11: Fabric/Forge respawn creates a new ServerPlayer; the session must follow the new handle. */
    @Test
    public void respawnReplacesTheHandleAndResyncsOnlyAcrossWorlds(@TempDir Path tempDir) {
        FakePlatform platform = new FakePlatform("fabric", false, tempDir);
        LadsRules rules = new LadsRules();
        rules.getWorlds().put("minecraft:the_nether", Map.of("Fullbright", new ModuleRule("block")));
        SessionManager manager = new SessionManager(platform, new LadsConfig(), rules);
        UUID uuid = UUID.randomUUID();
        FakePlayerHandle first = new FakePlayerHandle(uuid, "Dies", "minecraft:overworld");
        platform.addPlayer(first);
        manager.join(first);
        platform.advanceTicks(1);
        clientHello(manager, first);
        first.getSentPayloads().clear();

        // Respawn in the same world: new object, nothing to send.
        FakePlayerHandle second = new FakePlayerHandle(uuid, "Dies", "minecraft:overworld");
        manager.respawned(second);
        LadsSession session = manager.getSessions().get(uuid);
        assertSame(second, session.getHandle());
        assertTrue(first.getSentPayloads().isEmpty());
        assertTrue(second.getSentPayloads().isEmpty());

        // The next dimension change reads the NEW player's world and sends through the new handle (not the dead entity).
        second.setWorld("minecraft:the_nether");
        manager.worldChanged(second);
        assertTrue(first.getSentPayloads().isEmpty(), "the dead player object must not be used any more");
        assertEquals("block", obj(second.getSentStrings().get(1)).getAsJsonObject("modules").get("Fullbright").getAsString());

        // Death in the nether, respawn in the overworld: the respawn alone must resync (no change event needed).
        second.getSentPayloads().clear();
        FakePlayerHandle third = new FakePlayerHandle(uuid, "Dies", "minecraft:overworld");
        manager.respawned(third);
        assertEquals(List.of("hello", "policy", "presence"), third.getSentStrings().stream().map(WorldChangeResyncTest::type).toList());
        assertFalse(obj(third.getSentStrings().get(1)).getAsJsonObject("modules").has("Fullbright"));
    }

    /** A world change before the first reply: ask again so the client that already reset still completes the handshake. */
    @Test
    public void worldChangeBeforeTheFirstReplyRepeatsTheHello(@TempDir Path tempDir) {
        FakePlatform platform = new FakePlatform("paper", false, tempDir);
        SessionManager manager = new SessionManager(platform, new LadsConfig(), new LadsRules());
        FakePlayerHandle player = new FakePlayerHandle(UUID.randomUUID(), "Early", "world");
        platform.addPlayer(player);
        manager.join(player);

        // Before the +1 tick hello nothing is sent (the scheduled hello comes after the world change anyway).
        player.setWorld("lobby");
        manager.worldChanged(player);
        assertTrue(player.getSentPayloads().isEmpty());

        platform.advanceTicks(1);
        player.setWorld("pvp");
        manager.worldChanged(player);
        assertEquals(List.of("hello", "hello"), player.getSentStrings().stream().map(WorldChangeResyncTest::type).toList());

        clientHello(manager, player);
        assertEquals("policy", type(player.getSentStrings().get(2)));
    }

    /** The +100 tick kick of an old session must not hit the session of a quick reconnect. */
    @Test
    public void scheduledTasksOfAnOldSessionDoNotKickTheReconnect(@TempDir Path tempDir) {
        FakePlatform platform = new FakePlatform("paper", false, tempDir);
        LadsConfig config = new LadsConfig();
        config.setRequireLadsClient(true);
        SessionManager manager = new SessionManager(platform, config, new LadsRules());
        UUID uuid = UUID.randomUUID();
        FakePlayerHandle player = new FakePlayerHandle(uuid, "Rejoin", "world");
        platform.addPlayer(player);

        manager.join(player);
        platform.advanceTicks(60);
        manager.quit(uuid);
        manager.join(player);

        platform.advanceTicks(40); // tick 100: only the first session's kick task is due
        assertFalse(player.isKicked());
        platform.advanceTicks(60); // tick 160: the reconnect's own kick task
        assertTrue(player.isKicked());
    }
}
