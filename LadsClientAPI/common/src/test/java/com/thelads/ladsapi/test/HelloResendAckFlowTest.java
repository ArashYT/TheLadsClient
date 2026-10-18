package com.thelads.ladsapi.test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.thelads.ladsapi.config.LadsConfig;
import com.thelads.ladsapi.rules.LadsRules;
import com.thelads.ladsapi.session.LadsSession;
import com.thelads.ladsapi.session.SessionManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class HelloResendAckFlowTest {

    @Test
    public void testHelloResendAndAckFlow(@TempDir Path tempDir) {
        FakePlatform platform = new FakePlatform("paper", false, tempDir);
        LadsConfig config = new LadsConfig();
        LadsRules rules = new LadsRules();
        SessionManager manager = new SessionManager(platform, config, rules);

        FakePlayerHandle player = new FakePlayerHandle(UUID.randomUUID(), "TestPlayer", "world");
        platform.addPlayer(player);

        manager.join(player);

        // At tick 0: no hello sent yet
        assertEquals(0, player.getSentPayloads().size());

        // Advance 1 tick -> hello sent
        platform.advanceTicks(1);
        assertEquals(1, player.getSentPayloads().size());
        JsonObject hello1 = JsonParser.parseString(player.getSentStrings().get(0)).getAsJsonObject();
        assertEquals("hello", hello1.get("t").getAsString());
        assertEquals(1, hello1.get("v").getAsInt());

        // Advance 58 ticks (now at tick 59): no second hello yet
        platform.advanceTicks(58);
        assertEquals(1, player.getSentPayloads().size());

        // Advance 1 more tick (now at tick 60) -> resend hello
        platform.advanceTicks(1);
        assertEquals(2, player.getSentPayloads().size());
        JsonObject hello2 = JsonParser.parseString(player.getSentStrings().get(1)).getAsJsonObject();
        assertEquals("hello", hello2.get("t").getAsString());

        // Advance another 100 ticks -> never more resends
        platform.advanceTicks(100);
        assertEquals(2, player.getSentPayloads().size());

        // Now client replies with hello
        String clientHello = "{\"t\":\"hello\",\"v\":1,\"lads\":\"1.9.13\",\"mc\":\"1.20.1\"}";
        manager.receive(player, clientHello.getBytes(StandardCharsets.UTF_8));

        // Player is now marked Lads
        LadsSession session = manager.getSessions().get(player.uuid());
        assertNotNull(session);
        assertTrue(session.isLads());
        assertEquals("1.9.13", session.getLadsVersion());
        assertEquals("1.20.1", session.getMcVersion());

        // Server should have sent policy (payload index 2) and presence full (index 3)
        assertTrue(player.getSentPayloads().size() >= 3);
        JsonObject policyObj = JsonParser.parseString(player.getSentStrings().get(2)).getAsJsonObject();
        assertEquals("policy", policyObj.get("t").getAsString());
        assertEquals(1, policyObj.get("rev").getAsInt());

        // Client replies with ack
        String clientAck = "{\"t\":\"ack\",\"rev\":1,\"ignored\":[\"OldMod\"],\"refused\":[]}";
        assertDoesNotThrow(() -> manager.receive(player, clientAck.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    public void testRequireLadsClientKick(@TempDir Path tempDir) {
        FakePlatform platform = new FakePlatform("paper", false, tempDir);
        LadsConfig config = new LadsConfig();
        config.setRequireLadsClient(true);
        LadsRules rules = new LadsRules();
        SessionManager manager = new SessionManager(platform, config, rules);

        FakePlayerHandle player = new FakePlayerHandle(UUID.randomUUID(), "NonLadsPlayer", "world");
        platform.addPlayer(player);

        manager.join(player);

        // Advance to 99 ticks -> player not kicked yet
        platform.advanceTicks(99);
        assertFalse(player.isKicked());

        // Advance to 100 ticks (5 seconds) -> kicked!
        platform.advanceTicks(1);
        assertTrue(player.isKicked());
        assertEquals(config.getRequireLadsClientMessage(), player.getKickMessage());
    }
}
