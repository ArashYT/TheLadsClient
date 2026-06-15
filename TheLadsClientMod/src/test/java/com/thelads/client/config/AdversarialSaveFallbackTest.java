package com.thelads.client.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.channels.FileLock;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.*;

public class AdversarialSaveFallbackTest {

    private File getConfigFile() {
        return new File("config/theladsclient.json");
    }

    @BeforeEach
    public void setup() throws Exception {
        com.thelads.client.e2e.ReflectionHelper.setupMocks();
    }

    @AfterEach
    public void teardown() {
        com.thelads.client.e2e.ReflectionHelper.teardownMocks();
    }

    @Test
    public void testSaveTempFileWriteFailure() throws Exception {
        File configFile = getConfigFile();
        File tempFile = new File(configFile.getAbsolutePath() + ".tmp");
        
        // Force an IOException when writing to tempFile by making it a directory
        configFile.getParentFile().mkdirs();
        tempFile.mkdirs();

        // This should hit the catch (IOException e) in save() and return early
        assertDoesNotThrow(() -> {
            ConfigManager.save();
        });

        // Cleanup
        tempFile.delete();
    }

    @Test
    public void testSaveFilesMoveFailure() throws Exception {
        File configFile = getConfigFile();
        File tempFile = new File(configFile.getAbsolutePath() + ".tmp");
        
        configFile.getParentFile().mkdirs();
        if (!configFile.exists()) {
            configFile.createNewFile();
        }

        // Lock the config file so Files.move throws an IOException
        try (RandomAccessFile raf = new RandomAccessFile(configFile, "rw");
             FileLock lock = raf.getChannel().lock()) {
             
            // This should hit the catch block for Files.move and execute the fallback
            assertDoesNotThrow(() -> {
                ConfigManager.save();
            });
        }
    }
}
