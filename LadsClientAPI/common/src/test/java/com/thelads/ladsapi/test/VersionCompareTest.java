package com.thelads.ladsapi.test;

import com.thelads.ladsapi.config.LadsConfig;
import com.thelads.ladsapi.rules.LadsRules;
import com.thelads.ladsapi.session.SessionManager;
import com.thelads.ladsapi.util.VersionUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class VersionCompareTest {

    @Test
    public void testVersionUtilComparisons() {
        assertTrue(VersionUtil.compare("1.9.13", "1.9.12") > 0);
        assertEquals(0, VersionUtil.compare("1.9.13", "1.9.13"));
        assertTrue(VersionUtil.compare("1.9.12", "1.9.13") < 0);
        assertTrue(VersionUtil.compare("1.10.0", "1.9.13") > 0);
        assertTrue(VersionUtil.compare("2.0.0", "1.99.99") > 0);
        assertEquals(0, VersionUtil.compare("1.9", "1.9.0"));

        assertTrue(VersionUtil.isOlder("1.9.12", "1.9.13"));
        assertFalse(VersionUtil.isOlder("1.9.13", "1.9.13"));
        assertFalse(VersionUtil.isOlder("1.10.0", "1.9.13"));
        assertFalse(VersionUtil.isOlder("1.9.13", ""));
        assertFalse(VersionUtil.isOlder("1.9.13", null));
    }

    @Test
    public void testMinClientVersionKick(@TempDir Path tempDir) {
        FakePlatform platform = new FakePlatform("paper", false, tempDir);
        LadsConfig config = new LadsConfig();
        config.setMinClientVersion("1.9.13");
        LadsRules rules = new LadsRules();
        SessionManager manager = new SessionManager(platform, config, rules);

        FakePlayerHandle player = new FakePlayerHandle(UUID.randomUUID(), "OldClientPlayer", "world");
        platform.addPlayer(player);

        manager.join(player);
        platform.advanceTicks(1);

        // Older client hello
        String clientHello = "{\"t\":\"hello\",\"v\":1,\"lads\":\"1.9.12\",\"mc\":\"26.3\"}";
        manager.receive(player, clientHello.getBytes(StandardCharsets.UTF_8));

        // Player must be kicked immediately
        assertTrue(player.isKicked());
        assertTrue(player.getKickMessage().contains("1.9.12"));
        assertTrue(player.getKickMessage().contains("1.9.13"));
    }
}
