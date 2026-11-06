package com.thelads.core.v1_20_1.feature.screenshots.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ScreenshotViewerConfigOrderTest {
    @TempDir
    Path temp;

    @Test
    void defaultsToNewestFirstWhenConfigMissingOrUnrelatedOptionSaved() {
        ScreenshotViewerConfig fresh = new ScreenshotViewerConfig(temp);
        assertEquals(ScreenshotListOrder.DESCENDING, fresh.getOrFallback(ScreenshotViewerOptions.DEFAULT_LIST_ORDER, ScreenshotListOrder.DESCENDING));
        assertTrue(fresh.get(ScreenshotViewerOptions.DEFAULT_LIST_ORDER).orElseThrow().isInverted());

        fresh.put(ScreenshotViewerOptions.INITIAL_SCREENSHOT_AMOUNT_PER_ROW, 6);
        fresh.writeToFile();

        ScreenshotViewerConfig reloaded = new ScreenshotViewerConfig(temp);
        assertEquals(6, reloaded.getOrFallback(ScreenshotViewerOptions.INITIAL_SCREENSHOT_AMOUNT_PER_ROW, 4));
        assertEquals(ScreenshotListOrder.DESCENDING, reloaded.get(ScreenshotViewerOptions.DEFAULT_LIST_ORDER).orElseThrow());
    }

    @Test
    void preservesDeliberatelySavedAscendingOrderInCurrentAndLegacyConfigs() throws Exception {
        Path ladsDir = temp.resolve("current").resolve("theladscore");
        Files.createDirectories(ladsDir);
        Files.writeString(ladsDir.resolve("screenshot_viewer.json"), "{\"default_list_order\":\"ASCENDING\"}", StandardCharsets.UTF_8);

        ScreenshotViewerConfig current = new ScreenshotViewerConfig(temp.resolve("current"));
        assertEquals(ScreenshotListOrder.ASCENDING, current.get(ScreenshotViewerOptions.DEFAULT_LIST_ORDER).orElseThrow());

        Path legacyDir = temp.resolve("legacy");
        Files.createDirectories(legacyDir);
        Files.writeString(legacyDir.resolve("screenshot_viewer-client.json"), "{\"ingui\":{\"default_list_order\":\"ASCENDING\"}}", StandardCharsets.UTF_8);

        ScreenshotViewerConfig legacy = new ScreenshotViewerConfig(legacyDir);
        assertEquals(ScreenshotListOrder.ASCENDING, legacy.get(ScreenshotViewerOptions.DEFAULT_LIST_ORDER).orElseThrow());
    }
}
