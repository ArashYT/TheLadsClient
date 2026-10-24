package com.thelads.core.v1_20_1.social;

import com.thelads.core.client.social.HostOptions;
import com.thelads.core.client.social.SocialBridge;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.function.Predicate;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class SocialBridge1201Test {

    @Test
    public void testHostOptionsPersistence(@TempDir Path tempDir) throws Exception {
        Path worldDir = tempDir.resolve("TestWorld");
        Files.createDirectories(worldDir);

        HostOptions options = new HostOptions();
        options.scope = HostOptions.Scope.INVITE;
        options.difficulty = "HARD";
        options.viewDistanceCap = 16;
        options.pvp = false;
        options.backupOnOpen = true;

        options.save(worldDir);

        Path savedFile = worldDir.resolve("lads-host.json");
        Assertions.assertTrue(Files.isRegularFile(savedFile));

        HostOptions loaded = HostOptions.load(worldDir);
        Assertions.assertEquals(HostOptions.Scope.INVITE, loaded.scope);
        Assertions.assertEquals("HARD", loaded.difficulty);
        Assertions.assertEquals(16, loaded.viewDistanceCap);
        Assertions.assertFalse(loaded.pvp);
        Assertions.assertTrue(loaded.backupOnOpen);
    }

    @Test
    public void testLoginFilterStorage() {
        UUID allowedId = UUID.randomUUID();
        UUID deniedId = UUID.randomUUID();

        Predicate<UUID> filter = id -> id.equals(allowedId);
        SocialBridge bridge = new SocialBridge1201();
        bridge.setLoginFilter(filter);

        Predicate<UUID> activeFilter = SocialBridge1201.getLoginFilter();
        Assertions.assertNotNull(activeFilter);
        Assertions.assertTrue(activeFilter.test(allowedId));
        Assertions.assertFalse(activeFilter.test(deniedId));
    }

    @Test
    public void testConsumeLocalConnectDisplayNameOneShot() {
        SocialBridge1201.resetLoopbackState();
        SocialBridge bridge = new SocialBridge1201();
        bridge.connectToLocal(25565, "BestFriend");

        // Non-matching port should return null
        String wrongPort = SocialBridge1201.consumeLocalConnectDisplayName("127.0.0.1", 25566);
        Assertions.assertNull(wrongPort);

        // Non-loopback host should return null
        String wrongHost = SocialBridge1201.consumeLocalConnectDisplayName("192.168.1.10", 25565);
        Assertions.assertNull(wrongHost);

        // Matching host & port should return name
        String first = SocialBridge1201.consumeLocalConnectDisplayName("127.0.0.1", 25565);
        Assertions.assertEquals("BestFriend", first);

        // One-shot: second call must return null
        String second = SocialBridge1201.consumeLocalConnectDisplayName("127.0.0.1", 25565);
        Assertions.assertNull(second);
    }

    @Test
    public void testResetLoopbackStateClearsAll() {
        SocialBridge bridge = new SocialBridge1201();
        bridge.connectToLocal(25565, "TempFriend");
        bridge.setLoginFilter(id -> true);

        SocialBridge1201.resetLoopbackState();

        Assertions.assertNull(SocialBridge1201.getLoginFilter());
        Assertions.assertNull(SocialBridge1201.consumeLocalConnectDisplayName("127.0.0.1", 25565));
        Assertions.assertFalse(SocialBridge1201.isLoopbackPublish());
        Assertions.assertFalse(SocialBridge1201.isSuppressLanPinger());
    }

    @Test
    public void testMuteGuestsToggle() {
        UUID guestId = UUID.randomUUID();
        Assertions.assertFalse(SocialBridge1201.isMuted(guestId));

        SocialBridge bridge = new SocialBridge1201();
        bridge.guestAction(guestId, SocialBridge.GuestAction.MUTE);
        Assertions.assertTrue(SocialBridge1201.isMuted(guestId));

        bridge.guestAction(guestId, SocialBridge.GuestAction.MUTE);
        Assertions.assertFalse(SocialBridge1201.isMuted(guestId));
    }

    @Test
    public void testFriendsButtonLabel() {
        var label = SocialBridge1201.friendsButtonLabel();
        Assertions.assertNotNull(label);
        Assertions.assertTrue(label.getString().startsWith("Friends"));
    }

    @Test
    public void testHostOptionsDefaults() {
        HostOptions options = new HostOptions();
        Assertions.assertEquals(HostOptions.Scope.FRIENDS, options.scope);
        Assertions.assertEquals(HostOptions.Approval.ASK, options.approval);
        Assertions.assertEquals(8, options.maxPlayers);
        Assertions.assertEquals("survival", options.guestGameMode);
        Assertions.assertTrue(options.pvp);
        Assertions.assertTrue(options.backupOnOpen);
        Assertions.assertFalse(options.relayOnly);
    }

    @Test
    public void testBackupWorldCreation(@TempDir Path tempDir) throws Exception {
        Path worldDir = tempDir.resolve("saves").resolve("MyTestWorld");
        Files.createDirectories(worldDir);
        Files.writeString(worldDir.resolve("level.dat"), "dummy level data");
        Files.writeString(worldDir.resolve("session.lock"), "lock");

        SocialBridge1201.backupWorld(worldDir, "MyTestWorld");

        Path backupDir = tempDir.resolve("saves").resolve("lads-backups");
        Assertions.assertTrue(Files.isDirectory(backupDir));
        File[] files = backupDir.toFile().listFiles((d, name) -> name.startsWith("MyTestWorld-") && name.endsWith(".zip"));
        Assertions.assertNotNull(files);
        Assertions.assertEquals(1, files.length);
        Assertions.assertTrue(files[0].length() > 0);
    }
}
