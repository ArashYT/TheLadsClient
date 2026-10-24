package com.thelads.core.client.social;

import com.thelads.core.client.title.PauseMenuLayout;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class SocialBridgeContractTest {

    @AfterEach
    void tearDown() {
        SocialBridge.set(new SocialBridge() {});
    }

    @Test
    void defaultBridgeBehaviors() {
        SocialBridge bridge = SocialBridge.get();
        assertNotNull(bridge);
        assertFalse(bridge.canHost());
        assertEquals(-1, bridge.openToFriends(new HostOptions()));
        assertFalse(bridge.inWorldOrServer());
        assertTrue(bridge.guests().isEmpty());
        assertNull(bridge.worldId());
        assertNull(bridge.worldName());
        assertNotNull(bridge.gameInfo());
        assertEquals("plain", bridge.gameInfo().profileKind());
    }

    @Test
    void setCustomBridgeInstance() {
        UUID testUuid = UUID.randomUUID();
        SocialBridge custom = new SocialBridge() {
            @Override
            public boolean canHost() {
                return true;
            }

            @Override
            public String worldId() {
                return "test-world";
            }
        };
        SocialBridge.set(custom);
        assertSame(custom, SocialBridge.get());
        assertTrue(SocialBridge.get().canHost());
        assertEquals("test-world", SocialBridge.get().worldId());
    }

    @Test
    void hostOptionsDefaultsAndSanitization(@TempDir Path tempDir) throws IOException {
        HostOptions options = new HostOptions();
        assertEquals(HostOptions.Scope.FRIENDS, options.scope);
        assertEquals(HostOptions.Approval.ASK, options.approval);
        assertEquals(8, options.maxPlayers);
        assertEquals("survival", options.guestGameMode);
        assertEquals(0, options.guestOpLevel);
        assertTrue(options.pvp);
        assertNull(options.difficulty);
        assertTrue(options.backupOnOpen);
        assertTrue(options.showJoinable);

        options.save(tempDir);
        assertTrue(Files.isRegularFile(tempDir.resolve("lads-host.json")));

        HostOptions loaded = HostOptions.load(tempDir);
        assertEquals(options.maxPlayers, loaded.maxPlayers);
        assertEquals(options.guestGameMode, loaded.guestGameMode);
        assertEquals(options.scope, loaded.scope);

        // Test sanitization
        options.maxPlayers = 999;
        options.guestOpLevel = -5;
        options.viewDistanceCap = 100;
        HostOptions sanitized = options.sanitized();
        assertEquals(16, sanitized.maxPlayers);
        assertEquals(0, sanitized.guestOpLevel);
        assertEquals(32, sanitized.viewDistanceCap);
    }

    @Test
    void pauseMenuLayoutIncludesFriendsSlot() {
        assertEquals("friends", PauseMenuLayout.icon(PauseMenuLayout.Slot.FRIENDS));
        assertEquals("host", PauseMenuLayout.icon(PauseMenuLayout.Slot.WORLD));
        assertEquals("server", PauseMenuLayout.icon(PauseMenuLayout.Slot.MULTIPLAYER));
    }
}
