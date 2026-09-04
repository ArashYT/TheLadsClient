package com.thelads.core;

import com.thelads.core.client.util.ClientPaths;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class ClientPathsTest {

    @Test
    public void testDynamicPathResolution(@TempDir Path tempDir) {
        ClientPaths.setBaseDir(tempDir);
        assertEquals(tempDir, ClientPaths.getBaseDir());

        File accounts = ClientPaths.getAccountsFile();
        assertEquals(tempDir.resolve("lads_accounts.json").toFile(), accounts);

        File profile = ClientPaths.getProfileFile();
        assertEquals(tempDir.resolve("lads_profile.json").toFile(), profile);

        File config = ClientPaths.getConfigFile();
        assertEquals(tempDir.resolve("thelads_config.json").toFile(), config);

        File skin = ClientPaths.getSkinFile();
        assertEquals(tempDir.resolve("skin.png").toFile(), skin);

        Path skinCache = ClientPaths.getSkinCacheDir();
        assertTrue(skinCache.toFile().exists());

        Path shared = ClientPaths.getSharedDir();
        assertTrue(shared.toFile().exists());
    }
}
