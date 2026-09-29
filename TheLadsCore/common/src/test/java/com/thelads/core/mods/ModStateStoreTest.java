package com.thelads.core.mods;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class ModStateStoreTest {
    @TempDir Path dir;

    private JsonObject json() throws Exception {
        return JsonParser.parseString(Files.readString(dir.resolve(ModStateStore.FILE_NAME))).getAsJsonObject();
    }

    @Test void missingFileMeansNoExplicitRequestsAndNoError() {
        var state = new ModStateStore(dir).read();
        assertTrue(state.mods().isEmpty());
        assertNull(state.error());
        assertNull(state.enabled("sodium", "AANobbMI"));
        assertEquals("absent", new ModStateStore(dir).stamp());
    }

    @Test void requestIsWrittenWithTheSharedSchemaAndReadBack() throws Exception {
        var store = new ModStateStore(dir);
        store.setRequested(Map.of("sodium", false), Map.of("sodium", "AANobbMI"));
        JsonObject entry = json().getAsJsonObject("mods").getAsJsonObject("sodium");
        assertEquals(1, json().get("schema").getAsInt());
        assertFalse(entry.get("enabled").getAsBoolean());
        assertEquals("AANobbMI", entry.get("projectId").getAsString());
        assertEquals("game", entry.get("source").getAsString());
        assertDoesNotThrow(() -> java.time.Instant.parse(entry.get("updatedAt").getAsString()));
        assertEquals(Boolean.FALSE, store.read().enabled("sodium", null));
        // A profile whose Minecraft version uses another mod id for the same project keeps the choice.
        assertEquals(Boolean.FALSE, store.read().enabled("sodium-renamed", "AANobbMI"));
        try (var files = Files.list(dir)) {
            assertEquals(List.of(ModStateStore.FILE_NAME, ModStateStore.FILE_NAME + ".lock"),
                files.map(p -> p.getFileName().toString()).sorted().toList(), "no temp files are left behind");
        }
    }

    @Test void unknownKeysFieldsAndOtherModsSurviveAGameWrite() throws Exception {
        Files.writeString(dir.resolve(ModStateStore.FILE_NAME), """
            {"schema": 1, "futureTopLevel": {"keep": [1, 2]},
             "mods": {"sodium": {"enabled": true, "projectId": "AANobbMI", "source": "launcher", "futureField": "x"},
                      "iris": {"enabled": false, "source": "launcher"}}}""");
        new ModStateStore(dir).setRequested(Map.of("sodium", false), Map.of());
        JsonObject root = json();
        assertEquals("[1,2]", root.getAsJsonObject("futureTopLevel").get("keep").toString());
        JsonObject sodium = root.getAsJsonObject("mods").getAsJsonObject("sodium");
        assertFalse(sodium.get("enabled").getAsBoolean());
        assertEquals("x", sodium.get("futureField").getAsString());
        assertEquals("AANobbMI", sodium.get("projectId").getAsString(), "an existing projectId is not dropped");
        assertEquals("game", sodium.get("source").getAsString());
        assertEquals("launcher", root.getAsJsonObject("mods").getAsJsonObject("iris").get("source").getAsString());
    }

    @Test void nullValuedUnknownKeysSurviveAGameWrite() throws Exception {
        Files.writeString(dir.resolve(ModStateStore.FILE_NAME), """
            {"schema": 1, "future": null,
             "mods": {"sodium": {"enabled": true, "projectId": null, "note": null}}}""");
        new ModStateStore(dir).setRequested(Map.of("sodium", false), Map.of());
        JsonObject root = json();
        assertTrue(root.has("future") && root.get("future").isJsonNull(), "a null-valued top-level key is kept");
        JsonObject sodium = root.getAsJsonObject("mods").getAsJsonObject("sodium");
        assertFalse(sodium.get("enabled").getAsBoolean());
        assertTrue(sodium.has("projectId") && sodium.get("projectId").isJsonNull());
        assertTrue(sodium.has("note") && sodium.get("note").isJsonNull());
    }

    @Test void corruptFileMeansDiskStateAndIsKeptAsideBeforeTheNextWrite() throws Exception {
        Path file = dir.resolve(ModStateStore.FILE_NAME);
        String damaged = "{\"mods\": {\"sodium\": {\"enabled\": fal";
        Files.writeString(file, damaged);
        var state = new ModStateStore(dir).read();
        assertTrue(state.mods().isEmpty());
        assertNotNull(state.error());
        assertEquals(damaged, Files.readString(file), "reading never rewrites a damaged file");

        new ModStateStore(dir).setRequested(Map.of("iris", false), Map.of());
        List<Path> kept;
        try (var files = Files.list(dir)) {
            kept = files.filter(p -> p.getFileName().toString().startsWith(ModStateStore.FILE_NAME + ".corrupt-")).toList();
        }
        assertEquals(1, kept.size());
        assertEquals(damaged, Files.readString(kept.getFirst()));
        assertEquals(Set.of("iris"), json().getAsJsonObject("mods").keySet());
        assertNull(new ModStateStore(dir).read().error());
    }

    @Test void wrongShapeIsTreatedAsDamagedToo() throws Exception {
        Files.writeString(dir.resolve(ModStateStore.FILE_NAME), "{\"mods\": [\"sodium\"]}");
        assertNotNull(new ModStateStore(dir).read().error());
        Files.writeString(dir.resolve(ModStateStore.FILE_NAME), "[]");
        assertNotNull(new ModStateStore(dir).read().error());
    }

    @Test void unreadableFileIsNeverReplaced() throws Exception {
        Path file = dir.resolve(ModStateStore.FILE_NAME);
        Files.createDirectories(file.resolve("blocker"));
        var store = new ModStateStore(dir);
        assertNotNull(store.read().error());
        assertThrows(java.io.IOException.class, () -> store.setRequested(Map.of("sodium", false), Map.of()));
        assertTrue(Files.isDirectory(file.resolve("blocker")));
    }

    @Test void writerWaitsForTheLauncherLockAndKeepsItsConcurrentEdit() throws Exception {
        Path lock = dir.resolve(ModStateStore.FILE_NAME + ".lock");
        CompletableFuture<Void> writer;
        try (var channel = FileChannel.open(lock, StandardOpenOption.CREATE, StandardOpenOption.WRITE); var held = channel.lock()) {
            writer = CompletableFuture.runAsync(() -> {
                try {
                    new ModStateStore(dir).setRequested(Map.of("sodium", false), Map.of());
                } catch (java.io.IOException e) {
                    throw new java.io.UncheckedIOException(e);
                }
            });
            Thread.sleep(400);
            assertFalse(writer.isDone(), "the game must not write while the launcher holds the lock");
            // The launcher's own read-modify-write completes while it holds the lock.
            Files.writeString(dir.resolve(ModStateStore.FILE_NAME),
                "{\"schema\":1,\"mods\":{\"iris\":{\"enabled\":false,\"source\":\"launcher\"}}}");
        }
        writer.get(5, TimeUnit.SECONDS);
        JsonObject mods = json().getAsJsonObject("mods");
        assertFalse(mods.getAsJsonObject("iris").get("enabled").getAsBoolean(), "the launcher's edit is not lost");
        assertFalse(mods.getAsJsonObject("sodium").get("enabled").getAsBoolean());
    }

    @Test void replaceIsRetriedWhileAReaderBlocksItAndNeverLosesTheFile() throws Exception {
        assumeTrue(System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win"), "Windows sharing rules");
        Path file = dir.resolve(ModStateStore.FILE_NAME);
        var store = new ModStateStore(dir);
        store.setRequested(Map.of("iris", false), Map.of());
        // java.io opens without FILE_SHARE_DELETE, so Windows denies replacing the file until this reader closes.
        var reader = new java.io.FileInputStream(file.toFile());
        CompletableFuture.runAsync(() -> {
            try {
                reader.close();
            } catch (java.io.IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
        }, CompletableFuture.delayedExecutor(300, TimeUnit.MILLISECONDS));
        long start = System.nanoTime();
        store.setRequested(Map.of("sodium", false), Map.of());
        assertTrue(System.nanoTime() - start >= 250_000_000L, "the denied replace waited for the reader");
        assertEquals(Boolean.FALSE, store.read().enabled("sodium", null));

        String before = Files.readString(file);
        try (var held = new java.io.FileInputStream(file.toFile())) {
            assertThrows(java.nio.file.AccessDeniedException.class, () -> store.setRequested(Map.of("lithium", false), Map.of()));
        }
        assertEquals(before, Files.readString(file), "a failed write leaves the previous file intact");
        try (var files = Files.list(dir)) {
            assertEquals(List.of(ModStateStore.FILE_NAME, ModStateStore.FILE_NAME + ".lock"),
                files.map(p -> p.getFileName().toString()).sorted().toList(), "the temp file is removed after the failure");
        }
    }

    @Test void concurrentGameWritersNeverLoseAnUpdate() throws Exception {
        List<CompletableFuture<Void>> writers = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            String id = "mod" + i;
            writers.add(CompletableFuture.runAsync(() -> {
                try {
                    new ModStateStore(dir).setRequested(Map.of(id, false), Map.of());
                } catch (java.io.IOException e) {
                    throw new java.io.UncheckedIOException(e);
                }
            }));
        }
        CompletableFuture.allOf(writers.toArray(CompletableFuture[]::new)).get(20, TimeUnit.SECONDS);
        assertEquals(8, json().getAsJsonObject("mods").size());
    }
}
