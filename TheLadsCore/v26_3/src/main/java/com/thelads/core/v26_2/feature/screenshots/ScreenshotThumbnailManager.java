// SPDX-License-Identifier: MIT
// Adapted from Screenshot Viewer 1.3.6 (LGatodu47). See ScreenshotViewer-LICENSE.txt.
package com.thelads.core.v26_2.feature.screenshots;

import io.github.lgatodu47.catconfig.CatConfig;
import io.github.lgatodu47.catconfigmc.screen.ConfigListener;
import com.thelads.core.v26_2.feature.screenshots.config.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import javax.imageio.ImageIO;
import net.minecraft.util.Util;
import org.slf4j.LoggerFactory;

/** Content-stamped, lazy cache. Only files in our reserved cache directory are managed. */
public final class ScreenshotThumbnailManager implements ConfigListener {
    private final CatConfig config;
    private Path root;
    private CompressionRatio ratio = CompressionRatio.NONE;
    private final Map<String, CompletableFuture<File>> cache = new LinkedHashMap<>(16, .75f, true);
    public ScreenshotThumbnailManager(CatConfig config) { this.config = config; configUpdated(); }
    public void configUpdated() {
        Path next = config.getOrFallback(ScreenshotViewerOptions.THUMBNAIL_FOLDER,
            (java.util.function.Supplier<? extends File>) ScreenshotViewerUtils::getDefaultThumbnailFolder).toPath().toAbsolutePath().normalize().resolve("lads-cache-v1");
        CompressionRatio nextRatio = config.getOrFallback(ScreenshotViewerOptions.COMPRESSION_RATIO, CompressionRatio.NONE);
        if (!next.equals(root) || nextRatio != ratio) { cache.clear(); root = next; ratio = nextRatio; }
    }
    public Optional<CompletableFuture<File>> getThumbnail(File screenshot) {
        if (ratio == CompressionRatio.NONE) return Optional.empty();
        try {
            Path source = screenshot.toPath().toRealPath();
            String stamp = source + "|" + Files.getLastModifiedTime(source) + "|" + Files.size(source) + "|" + ratio;
            String prefix = filePrefix(source);
            String key = prefix + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(stamp.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            CompletableFuture<File> result = cache.get(key);
            if (result == null) {
                Path target = root.resolve(key + ".png"); CompressionRatio scale = ratio;
                result = CompletableFuture.supplyAsync(() -> generate(source, target, scale), com.thelads.core.v26_2.feature.screenshots.ScreenshotFileIO.IMAGE_EXECUTOR);
                cache.put(key, result);
                if (cache.size() > 128) cache.remove(cache.keySet().iterator().next());
            }
            return Optional.of(result);
        } catch (Exception failure) { return Optional.empty(); }
    }
    public void removeThumbnail(File source) {
        cache.clear();
        Path directory = root;
        try {
            String prefix = filePrefix(source.toPath().toAbsolutePath().normalize());
            CompletableFuture.runAsync(() -> {
                if (!Files.isDirectory(directory) || Files.isSymbolicLink(directory)) return;
                try (var entries = Files.list(directory)) {
                    for (Path file : entries.filter(p -> p.getFileName().toString().matches(prefix + "[a-f0-9]{64}\\.png")).toList()) Files.deleteIfExists(file);
                } catch (IOException failure) { LoggerFactory.getLogger("Lads Screenshots").warn("Could not remove obsolete gallery cache", failure); }
            }, com.thelads.core.v26_2.feature.screenshots.ScreenshotFileIO.IMAGE_EXECUTOR);
        } catch (Exception ignored) { }
    }
    private static String filePrefix(Path source) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(source.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8))).substring(0, 16) + "-";
    }
    public static File generate(Path source, Path target, CompressionRatio scale) {
        try {
            if (Files.isRegularFile(target)) return target.toFile();
            Files.createDirectories(target.getParent());
            // Never replace an arbitrary file or follow a pre-existing link in the reserved location.
            if (Files.isSymbolicLink(target) || Files.isSymbolicLink(target.getParent())) throw new IOException("Linked cache path");
            BufferedImage input = ScreenshotFileIO.read(source);
            BufferedImage output = new BufferedImage(Math.max(1, scale.scale(input.getWidth())), Math.max(1, scale.scale(input.getHeight())), BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = output.createGraphics();
            try { graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR); graphics.drawImage(input, 0, 0, output.getWidth(), output.getHeight(), null); }
            finally { graphics.dispose(); input.flush(); }
            Path temporary = Files.createTempFile(target.getParent(), "lads-", ".tmp");
            try {
                ImageIO.write(output, "png", temporary.toFile());
                try { Files.move(temporary, target); } catch (FileAlreadyExistsException alreadyDone) { }
            } finally { output.flush(); Files.deleteIfExists(temporary); }
            return target.toFile();
        } catch (Exception failure) {
            LoggerFactory.getLogger("Lads Screenshots").warn("Could not generate thumbnail for {}", source.getFileName(), failure);
            return source.toFile(); // Read the actual screenshot when cache creation fails.
        }
    }
}
