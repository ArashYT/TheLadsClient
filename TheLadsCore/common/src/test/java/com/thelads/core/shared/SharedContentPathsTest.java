package com.thelads.core.shared;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SharedContentPathsTest {
    @TempDir
    Path temp;

    @AfterEach
    void restoreEnvironmentResolution() {
        SharedContentPaths.setRootForTests(null);
    }

    @Test
    void withoutEnvironmentTheRedirectIsOffAndScreenshotsKeepTheOsDefault() {
        var windows = SharedContentPaths.resolve(Map.of("APPDATA", temp.toString()), "Windows 11", "C:\\Users\\qa");
        assertFalse(windows.redirect());
        assertEquals(temp.resolve(".minecraft"), windows.root());

        var mac = SharedContentPaths.resolve(Map.of(), "Mac OS X", temp.toString());
        assertEquals(temp.resolve("Library").resolve("Application Support").resolve("minecraft"), mac.root());

        var linux = SharedContentPaths.resolve(Map.of(), "Linux", temp.toString());
        assertEquals(temp.resolve(".minecraft"), linux.root());

        var windowsWithoutAppData = SharedContentPaths.resolve(Map.of(), "Windows 10", temp.toString());
        assertEquals(temp.resolve(".minecraft"), windowsWithoutAppData.root());
    }

    @Test
    void absoluteEnvironmentValueEnablesTheRedirect() {
        Path sandbox = temp.resolve("global sandbox");
        var resolved = SharedContentPaths.resolve(
            Map.of(SharedContentPaths.ENV, " " + sandbox.resolve("x").resolve("..") + " ", "APPDATA", temp.toString()), "Windows 11", "C:\\Users\\qa");
        assertTrue(resolved.redirect());
        assertEquals(sandbox, resolved.root());
    }

    @Test
    void invalidEnvironmentValueDisablesTheRedirectInsteadOfFailing() {
        for (String invalid : new String[] {"relative\\global", "relative/global", "C:relative", "\\rooted", "bad\u0000path"}) {
            var resolved = SharedContentPaths.resolve(Map.of(SharedContentPaths.ENV, invalid, "APPDATA", temp.toString()), "Windows 11", "C:\\Users\\qa");
            assertFalse(resolved.redirect(), invalid);
            assertEquals(temp.resolve(".minecraft"), resolved.root(), invalid);
        }
        var blank = SharedContentPaths.resolve(Map.of(SharedContentPaths.ENV, "  ", "APPDATA", temp.toString()), "Windows 11", "C:\\Users\\qa");
        assertFalse(blank.redirect());
    }

    @Test
    void contentFoldersLiveDirectlyUnderTheRoot() {
        SharedContentPaths.setRootForTests(temp);
        assertTrue(SharedContentPaths.redirectEnabled());
        assertEquals(temp.resolve("saves"), SharedContentPaths.savesDir());
        assertEquals(temp.resolve("resourcepacks"), SharedContentPaths.resourcePacksDir());
        assertEquals(temp.resolve("shaderpacks"), SharedContentPaths.shaderPacksDir());
        assertEquals(temp.resolve("screenshots"), SharedContentPaths.screenshotsDir());
        assertEquals(temp.resolve("servers.dat"), SharedContentPaths.serversFile());
    }

    @Test
    void sandboxGuardAcceptsOnlyARootInsideTheGivenFolder() throws Exception {
        Path verification = Files.createDirectories(temp.resolve("artifacts").resolve("verification"));
        Path sandbox = Files.createDirectories(verification.resolve("global-sandbox"));
        Path elsewhere = Files.createDirectories(temp.resolve("real-minecraft"));

        SharedContentPaths.setRootForTests(sandbox);
        assertTrue(SharedContentPaths.redirectedInside(verification));
        SharedContentPaths.setRootForTests(elsewhere);
        assertFalse(SharedContentPaths.redirectedInside(verification));
        SharedContentPaths.setRootForTests(verification.resolve("missing"));
        assertFalse(SharedContentPaths.redirectedInside(verification));
    }

    @Test
    void serversLockCreatesTheSharedFolderAndRunsTheAction() throws Exception {
        Path root = temp.resolve("new global");
        SharedContentPaths.setRootForTests(root);
        String result = SharedContentPaths.withServersLock(() -> {
            assertTrue(Files.isRegularFile(root.resolve(".lads-servers.lock")));
            return "saved";
        });
        assertEquals("saved", result);
    }
}
