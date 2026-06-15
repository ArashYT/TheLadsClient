package com.thelads.client;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import com.thelads.client.config.ConfigManager;
import java.io.File;
import java.io.FileWriter;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

public class AdversarialConcurrencyTest {

    private File getConfigFile() {
        return new File("config/theladsclient.json");
    }

    @BeforeEach
    public void setup() throws Exception {
        com.thelads.client.e2e.ReflectionHelper.setupMocks();
        
        File file = getConfigFile();
        file.getParentFile().mkdirs();
        try (FileWriter writer = new FileWriter(file)) {
            writer.write("{\"capesEnabled\": false, \"uiScalingEnabled\": true}");
        }
    }

    @AfterEach
    public void teardown() {
        com.thelads.client.e2e.ReflectionHelper.teardownMocks();
    }

    @Test
    public void testConcurrentSaveAndLoadCorruptsConfig() throws Exception {
        final int THREADS = 10;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(THREADS * 2);
        AtomicBoolean wipeDetected = new AtomicBoolean(false);

        // We load initially to ensure it's loaded
        ConfigManager.loadConfig();
        
        // Assert initial state is what we wrote
        assertFalse(ConfigManager.getConfig().isCapesEnabled());
        assertTrue(ConfigManager.getConfig().isUiScalingEnabled());

        for (int i = 0; i < THREADS; i++) {
            new Thread(() -> {
                try {
                    startLatch.await();
                    for (int j = 0; j < 50; j++) {
                        ConfigManager.save();
                    }
                } catch (Exception e) {
                } finally {
                    doneLatch.countDown();
                }
            }).start();

            new Thread(() -> {
                try {
                    startLatch.await();
                    for (int j = 0; j < 50; j++) {
                        ConfigManager.loadConfig();
                        if (ConfigManager.getConfig().isCapesEnabled() == true) { // Back to default = wiped!
                            wipeDetected.set(true);
                        }
                    }
                } catch (Exception e) {
                } finally {
                    doneLatch.countDown();
                }
            }).start();
        }

        startLatch.countDown();
        doneLatch.await();

        assertFalse(wipeDetected.get(), "Config was wiped due to concurrent read/write (truncation during FileReader)");
    }
}
