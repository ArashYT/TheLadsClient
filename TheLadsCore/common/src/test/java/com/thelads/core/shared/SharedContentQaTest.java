package com.thelads.core.shared;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SharedContentQaTest {
    @TempDir
    Path temp;
    private Path verification;
    private Path game;

    @BeforeEach
    void fakeRepository() throws Exception {
        Files.createDirectories(temp.resolve("TheLadsCore"));
        Files.writeString(temp.resolve("TheLadsCore").resolve("settings.gradle"), "rootProject.name = 'TheLadsCore'");
        verification = Files.createDirectories(temp.resolve("artifacts").resolve("verification"));
        game = Files.createDirectories(verification.resolve("26.3-title"));
    }

    @AfterEach
    void restoreEnvironmentResolution() {
        SharedContentPaths.setRootForTests(null);
    }

    @Test
    void rolesNeedASafeRunIdAndNameEveryDisposableItem() {
        SharedContentQa qa = new SharedContentQa();
        qa.configure(null, null);
        assertEquals(SharedContentQa.Role.CHECK, qa.role());

        qa.configure("create", "20260929-a_1");
        assertEquals(SharedContentQa.Role.CREATE, qa.role());
        assertEquals("lads-shared-qa-20260929-a_1.zip", qa.packFileName());
        assertEquals("Lads Shared QA 20260929-a_1", qa.serverName());
        assertEquals("lads-qa-20260929-a_1.invalid", qa.serverIp());
        assertEquals("Lads Shared QA 20260929-a_1", qa.worldName());

        assertThrows(IllegalArgumentException.class, () -> qa.configure("delete", "x"));
        assertThrows(IllegalArgumentException.class, () -> qa.configure("observe", null));
        assertThrows(IllegalArgumentException.class, () -> qa.configure("observe", "..\\escape"));
        assertThrows(IllegalArgumentException.class, () -> qa.configure("create", "a".repeat(41)));
    }

    @Test
    void sandboxRootInsideTheVerificationFolderIsAccepted() throws Exception {
        SharedContentPaths.setRootForTests(Files.createDirectories(verification.resolve("global-sandbox")));
        SharedContentQa qa = new SharedContentQa();
        qa.requireSandbox(game);
        assertEquals(3, qa.passed());
    }

    @Test
    void realOrMisplacedRootsAreRefused() throws Exception {
        SharedContentPaths.setRootForTests(Files.createDirectories(temp.resolve("AppData").resolve(".minecraft")));
        assertThrows(IllegalStateException.class, () -> new SharedContentQa().requireSandbox(game));

        SharedContentPaths.setRootForTests(Files.createDirectories(verification.resolve("global-sandbox")));
        Path outsideVerification = Files.createDirectories(temp.resolve("artifacts").resolve("26.3-title"));
        assertThrows(IllegalStateException.class, () -> new SharedContentQa().requireSandbox(outsideVerification));

        Files.delete(temp.resolve("TheLadsCore").resolve("settings.gradle"));
        assertThrows(IllegalStateException.class, () -> new SharedContentQa().requireSandbox(game));
    }

    @Test
    void sharedFoldersMustResolveToTheSharedRoot() throws Exception {
        Path root = Files.createDirectories(verification.resolve("global-sandbox"));
        Path saves = Files.createDirectories(root.resolve("saves"));
        SharedContentPaths.setRootForTests(root);
        SharedContentQa qa = new SharedContentQa();

        assertEquals(saves.toRealPath(), qa.requireShared("saves", saves, SharedContentPaths.savesDir()));
        Path profileSaves = Files.createDirectories(game.resolve("saves"));
        assertThrows(IllegalStateException.class, () -> qa.requireShared("saves", profileSaves, SharedContentPaths.savesDir()));
        assertThrows(IllegalStateException.class, () -> qa.requireShared("saves", profileSaves, profileSaves));
        assertEquals(1, qa.passed());

        qa.requireSharedServersFile();
        Files.write(root.resolve("servers.dat"), new byte[19]);
        qa.requireSharedServersFile();
        assertEquals(3, qa.passed());
    }
}
