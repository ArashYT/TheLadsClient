package com.thelads.core.client.social;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.*;

public class WorldBackupTest {

    @Test
    void zipsTheWorldIntoGameDirBackupsAndKeepsTheNewestFive(@TempDir Path game) throws Exception {
        Path world = Files.createDirectories(game.resolve("saves").resolve("MyWorld"));
        Files.writeString(world.resolve("level.dat"), "level");
        Files.createDirectories(world.resolve("region"));
        Files.write(world.resolve("region").resolve("r.0.0.mca"), new byte[64 * 1024]);
        Files.writeString(world.resolve("session.lock"), "lock");
        Files.writeString(world.resolve("lads-host.json"), "{}");
        Path backups = Files.createDirectories(game.resolve("lads-backups"));
        for (int i = 0; i < 7; i++) Files.writeString(backups.resolve("MyWorld-2026010" + i + "-1200.zip"), "old");
        Files.writeString(backups.resolve("MyWorld-2-20260101-1200.zip"), "another world");
        Path stalePart = backups.resolve("MyWorld-20250101-0000.zip.part"); // left by a crash, days old
        Files.writeString(stalePart, "half");
        Files.setLastModifiedTime(stalePart, java.nio.file.attribute.FileTime.fromMillis(System.currentTimeMillis() - 3 * 86_400_000L));

        List<Integer> progress = new ArrayList<>();
        Path zip = WorldBackup.zip(world.resolve("."), progress::add); // 1.20.1/26.3 hand over saves/<world>/.

        assertEquals(backups, zip.getParent(), "saves/../lads-backups");
        try (ZipFile z = new ZipFile(zip.toFile())) {
            assertNotNull(z.getEntry("level.dat"));
            assertNotNull(z.getEntry("region/r.0.0.mca"));
            assertNull(z.getEntry("session.lock"));
            assertNull(z.getEntry("lads-host.json"));
        }
        assertEquals(100, progress.get(progress.size() - 1));
        try (var list = Files.list(backups)) {
            assertEquals(5, list.filter(p -> p.getFileName().toString().matches("MyWorld-\\d{8}-\\d{4}\\.zip")).count());
        }
        assertTrue(Files.exists(zip), "the new backup is among the five kept");
        assertTrue(Files.exists(backups.resolve("MyWorld-2-20260101-1200.zip")), "another world's backups are untouched");
        assertFalse(Files.exists(stalePart), "a stale .part is cleaned up");
    }

    @Test
    void backupRunsOffTheGameThreadAndOpensAfterwardsEvenWhenItFails(@TempDir Path game) throws Exception {
        CompletableFuture<Void> save = new CompletableFuture<>();
        LinkedBlockingQueue<Runnable> gameThread = new LinkedBlockingQueue<>();
        List<String> toasts = new ArrayList<>();
        Path missingWorld = game.resolve("saves").resolve("Gone"); // never created: the zip fails
        SocialBridge bridge = new SocialBridge() {
            @Override public Path worldDir() { return missingWorld; }
            @Override public CompletableFuture<Void> saveWorld() { return save; }
            @Override public void runOnGameThread(Runnable task) { gameThread.add(task); }
            @Override public int openToFriends(HostOptions options) { return 25565; }
            @Override public void toast(String title, String body, String accept, String deny) { toasts.add(body); }
        };
        SocialState state = new SocialState(() -> bridge, () -> new com.thelads.core.client.bridge.LadsGameBridge.Session(UUID.randomUUID(), "Host", "t"), null);
        state.setClientSender(m -> {});
        HostOptions options = new HostOptions(); // backupOnOpen defaults to on

        state.startHosting(options);
        assertFalse(state.hostSession().isHosting(), "nothing opens before the backup: the caller is never blocked on it");
        save.complete(null); // the server finished save-all
        Runnable open = gameThread.poll(5, TimeUnit.SECONDS);
        assertNotNull(open, "the world opens on the game thread after the backup");
        assertTrue(toasts.stream().anyMatch(t -> t.startsWith("Backup failed")), toasts.toString());
        open.run();
        assertTrue(state.hostSession().isHosting(), "a failed backup still opens the world");
        state.stopHosting();
    }
}
