// SPDX-License-Identifier: MIT
// Adapted from Screenshot Viewer 1.3.6 (LGatodu47). See ScreenshotViewer-LICENSE.txt.
package com.thelads.core.v26_2.feature.screenshots.config;
import io.github.lgatodu47.catconfig.*;
import io.github.lgatodu47.catconfigmc.MinecraftConfigSides;
import java.nio.file.*;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.LoggerFactory;

public final class ScreenshotViewerConfig extends CatConfig {
    public ScreenshotViewerConfig() {
        super(MinecraftConfigSides.CLIENT, "screenshots", CatConfigLogger.delegate(LoggerFactory.getLogger("Lads Screenshots")));
    }
    protected ConfigOptionAccess getConfigOptions() { return ScreenshotViewerOptions.OPTIONS; }
    protected Path getConfigDirectory() { return FabricLoader.getInstance().getConfigDir().resolve("theladscore"); }
    // Screen Done writes synchronously; avoid an immortal watcher and concurrent live UI mutation.
    protected ConfigWatcher makeAndStartConfigWatcherThread() { return null; }
    public static void importLegacy() {
        Path directory = FabricLoader.getInstance().getConfigDir();
        Path target = directory.resolve("theladscore/screenshots-client.json");
        Path source = directory.resolve("screenshot_viewer-client.json");
        try {
            Files.createDirectories(target.getParent());
            if (!Files.exists(target) && Files.isRegularFile(source) && Files.size(source) <= 1_048_576) {
                com.google.gson.JsonParser.parseString(Files.readString(source)).getAsJsonObject();
                Files.copy(source, target);
            }
        } catch (Exception failure) { LoggerFactory.getLogger("Lads Screenshots").warn("Could not import legacy gallery settings", failure); }
    }
}
