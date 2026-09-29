package com.thelads.core.v26_2.feature.screenshots;

import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import javax.imageio.*;
import javax.imageio.stream.ImageInputStream;

/** Bounded decoder and file operations; no game state, user actions remain explicit. */
public final class ScreenshotFileIO {
    private ScreenshotFileIO() {}
    private static final java.util.concurrent.atomic.AtomicInteger WORKER_ID = new java.util.concurrent.atomic.AtomicInteger();
    /** Two decoders maximum, independent of Minecraft's CPU-sized background pool. */
    public static final java.util.concurrent.ExecutorService IMAGE_EXECUTOR = java.util.concurrent.Executors.newFixedThreadPool(2, task -> {
        Thread worker = new Thread(task, "Lads screenshot IO " + WORKER_ID.incrementAndGet());
        worker.setDaemon(true);
        return worker;
    });
    public static boolean imageFile(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return Files.isRegularFile(path) && (name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg"));
    }
    public static BufferedImage read(Path path) throws IOException { return read(path, 16384); }
    public static BufferedImage read(Path path, int maxEdge) throws IOException {
        if (!imageFile(path) || Files.size(path) > 256L * 1024 * 1024) throw new IOException("Unsupported screenshot file or file exceeds 256 MiB");
        try (ImageInputStream input = ImageIO.createImageInputStream(path.toFile())) {
            if (input == null) throw new IOException("Unreadable image");
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IOException("Unsupported image format");
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || width > 16384 || height > 16384 || (long) width * height > 64_000_000)
                    throw new IOException("Image dimensions exceed the gallery limit");
                var params = reader.getDefaultReadParam();
                int sample = Math.max(1, (Math.max(width, height) + maxEdge - 1) / maxEdge);
                params.setSourceSubsampling(sample, sample, 0, 0);
                return reader.read(0, params);
            } finally { reader.dispose(); }
        }
    }
    public static String extension(Path source) {
        String name = source.getFileName().toString();
        return name.substring(name.lastIndexOf('.'));
    }
    public static boolean validStem(String stem) {
        String s = stem.trim();
        return !s.isBlank() && s.length() <= 128 && !s.equals(".") && !s.equals("..") && !s.endsWith(".")
            && s.chars().noneMatch(c -> c < 32 || "\\/:*?\"<>|".indexOf(c) >= 0)
            && !s.toUpperCase(Locale.ROOT).matches("(?:CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(?:\\..*)?");
    }
    public static Path rename(Path source, String stem) throws IOException {
        if (!validStem(stem) || !imageFile(source)) throw new IOException("Invalid screenshot filename");
        Path original = source.toAbsolutePath().normalize();
        Path destination = original.resolveSibling(stem.trim() + extension(original));
        if (!destination.getParent().equals(original.getParent())) throw new IOException("Filename must remain in its folder");
        return Files.move(original, destination); // No replacement: an existing image always wins.
    }
    public static boolean delete(Path source) throws IOException {
        if (!imageFile(source)) throw new IOException("Only a selected screenshot can be deleted");
        return Files.deleteIfExists(source);
    }
}
