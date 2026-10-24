package com.thelads.ladsapi.test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.thelads.ladsapi.config.LadsConfig;
import com.thelads.ladsapi.rules.LadsRules;
import com.thelads.ladsapi.session.SessionManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class PresenceTest {

    @Test
    public void testPresenceLifecycle(@TempDir Path tempDir) {
        FakePlatform platform = new FakePlatform("paper", false, tempDir);
        LadsConfig config = new LadsConfig();
        config.setBadgePresence(true);
        LadsRules rules = new LadsRules();
        SessionManager manager = new SessionManager(platform, config, rules);

        UUID uuid1 = UUID.randomUUID();
        FakePlayerHandle p1 = new FakePlayerHandle(uuid1, "Player1", "world");
        platform.addPlayer(p1);

        UUID uuid2 = UUID.randomUUID();
        FakePlayerHandle p2 = new FakePlayerHandle(uuid2, "Player2", "world");
        platform.addPlayer(p2);

        // Player 1 joins
        manager.join(p1);
        platform.advanceTicks(1);
        String clientHello1 = "{\"t\":\"hello\",\"v\":1,\"lads\":\"1.9.13\",\"mc\":\"26.3\"}";
        manager.receive(p1, clientHello1.getBytes(StandardCharsets.UTF_8));

        // Player 1 received hello, policy, and presence full:
        String p1PresenceFull = p1.getLastSentString();
        JsonObject p1FullObj = JsonParser.parseString(p1PresenceFull).getAsJsonObject();
        assertEquals("presence", p1FullObj.get("t").getAsString());
        assertTrue(p1FullObj.get("full").getAsBoolean());
        assertTrue(p1FullObj.getAsJsonArray("add").toString().contains(uuid1.toString()));

        // Now Player 2 joins
        manager.join(p2);
        platform.advanceTicks(1);
        String clientHello2 = "{\"t\":\"hello\",\"v\":1,\"lads\":\"1.9.13\",\"mc\":\"26.3\"}";
        manager.receive(p2, clientHello2.getBytes(StandardCharsets.UTF_8));

        // Player 2 receives presence full with both UUIDs:
        String p2PresenceFull = p2.getLastSentString();
        JsonObject p2FullObj = JsonParser.parseString(p2PresenceFull).getAsJsonObject();
        assertEquals("presence", p2FullObj.get("t").getAsString());
        assertTrue(p2FullObj.get("full").getAsBoolean());
        assertTrue(p2FullObj.getAsJsonArray("add").toString().contains(uuid1.toString()));
        assertTrue(p2FullObj.getAsJsonArray("add").toString().contains(uuid2.toString()));

        // Player 1 should have received presence add for uuid2:
        String p1Last = p1.getLastSentString();
        JsonObject p1AddObj = JsonParser.parseString(p1Last).getAsJsonObject();
        assertEquals("presence", p1AddObj.get("t").getAsString());
        assertFalse(p1AddObj.get("full").getAsBoolean());
        assertTrue(p1AddObj.getAsJsonArray("add").toString().contains(uuid2.toString()));

        // Now Player 2 quits
        manager.quit(uuid2);

        // Player 1 should have received presence remove for uuid2:
        String p1QuitNotify = p1.getLastSentString();
        JsonObject p1RemObj = JsonParser.parseString(p1QuitNotify).getAsJsonObject();
        assertEquals("presence", p1RemObj.get("t").getAsString());
        assertFalse(p1RemObj.get("full").getAsBoolean());
        assertTrue(p1RemObj.getAsJsonArray("remove").toString().contains(uuid2.toString()));
    }
}
