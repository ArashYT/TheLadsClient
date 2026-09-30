package com.thelads.core.shared;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class QaWorldGuardTest {
    @TempDir
    Path temp;
    private Path verification;
    private Path game;

    @BeforeEach
    void fakeRepository() throws Exception {
        Files.createDirectories(temp.resolve("TheLadsCore"));
        Files.writeString(temp.resolve("TheLadsCore").resolve("settings.gradle"), "rootProject.name = 'TheLadsCore'");
        verification = Files.createDirectories(temp.resolve("artifacts").resolve("verification"));
        game = Files.createDirectories(verification.resolve("1.21.1-title"));
    }

    @AfterEach
    void restoreEnvironmentResolution() {
        SharedContentPaths.setRootForTests(null);
    }

    @Test
    void eachVersionHasItsOwnSave() {
        assertEquals("Client QA 1_21_1", QaWorldGuard.saveName("1.21.1"));
        assertEquals("Client QA 1_21_11", QaWorldGuard.saveName("1.21.11"));
        assertNotEquals(QaWorldGuard.saveName("1.21.1"), QaWorldGuard.saveName("26.2"));
    }

    @Test
    void onlyThatVersionsTitleSandboxIsAccepted() throws Exception {
        assertEquals(game.toRealPath(), QaWorldGuard.gameDirectory(game, "1.21.1"));
        assertThrows(IOException.class, () -> QaWorldGuard.gameDirectory(game, "1.21.11"));
        Path settings = Files.createDirectories(verification.resolve("1.21.1-settings"));
        assertThrows(IOException.class, () -> QaWorldGuard.gameDirectory(settings, "1.21.1"));
        Path outside = Files.createDirectories(temp.resolve("artifacts").resolve("1.21.1-title"));
        assertThrows(IOException.class, () -> QaWorldGuard.gameDirectory(outside, "1.21.1"));
        Files.delete(temp.resolve("TheLadsCore").resolve("settings.gradle"));
        assertThrows(IOException.class, () -> QaWorldGuard.gameDirectory(game, "1.21.1"));
    }

    @Test
    void savesAreLocalOrTheSandboxSharedFolder() throws Exception {
        Path real = game.toRealPath();
        Path local = Files.createDirectories(game.resolve("saves"));
        assertEquals(local.toRealPath(), QaWorldGuard.saves(real, local));

        Path root = Files.createDirectories(verification.resolve("global-sandbox"));
        Path shared = Files.createDirectories(root.resolve("saves"));
        SharedContentPaths.setRootForTests(root);
        assertEquals(shared.toRealPath(), QaWorldGuard.saves(real, shared));

        // A shared root outside artifacts/verification (the real .minecraft) is refused even for its own saves folder.
        Path appData = Files.createDirectories(temp.resolve("AppData").resolve(".minecraft"));
        Path realSaves = Files.createDirectories(appData.resolve("saves"));
        SharedContentPaths.setRootForTests(appData);
        assertThrows(IOException.class, () -> QaWorldGuard.saves(real, realSaves));
        assertThrows(IOException.class, () -> QaWorldGuard.saves(real, shared));
    }

    @Test
    void existingSaveNeedsItsLevelData() throws Exception {
        Path saves = Files.createDirectories(game.resolve("saves")).toRealPath();
        Path world = Files.createDirectories(saves.resolve("Client QA 1_21_1"));
        assertThrows(IOException.class, () -> QaWorldGuard.existingSave(saves, "Client QA 1_21_1"));
        Files.write(world.resolve("level.dat"), new byte[] {1});
        assertEquals(world.toRealPath(), QaWorldGuard.existingSave(saves, "Client QA 1_21_1"));
        assertThrows(IOException.class, () -> QaWorldGuard.existingSave(saves, "Client QA 1_21_11"));
        Path elsewhere = Files.createDirectories(game.resolve("elsewhere").resolve("Real World"));
        Files.write(elsewhere.resolve("level.dat"), new byte[] {1});
        assertThrows(IOException.class, () -> QaWorldGuard.existingSave(saves, "../elsewhere/Real World"));
    }

    @Test
    void newerOrUnversionedSavesAreRefused() {
        assertDoesNotThrow(() -> QaWorldGuard.requireOpenable(3955, 3955));
        assertDoesNotThrow(() -> QaWorldGuard.requireOpenable(3700, 3955));
        assertThrows(IOException.class, () -> QaWorldGuard.requireOpenable(4671, 3955));
        assertThrows(IOException.class, () -> QaWorldGuard.requireOpenable(0, 3955));
    }
}
