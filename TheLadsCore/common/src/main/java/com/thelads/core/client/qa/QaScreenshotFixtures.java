package com.thelads.core.client.qa;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import javax.imageio.ImageIO;

/**
 * Seeds 3 screenshots with distinct modified times in a QA game's screenshots folder so gallery default newest-first
 * ordering is unambiguous against alphabetical or oldest-first sorting:
 *   bravo_newest.png (newest) -> charlie_middle.png (middle) -> alpha_oldest.png (oldest).
 */
public final class QaScreenshotFixtures {
    public static final String OLDEST = "alpha_oldest.png";
    public static final String MIDDLE = "charlie_middle.png";
    public static final String NEWEST = "bravo_newest.png";
    public static final List<String> EXPECTED_NEWEST_FIRST = Arrays.asList(NEWEST, MIDDLE, OLDEST);
    private static final String BACKUP_DIR = ".qa-seed-backup";

    private QaScreenshotFixtures() {}

    public static void seed(Path gameDir) throws IOException {
        Path shotsDir = gameDir.resolve("screenshots");
        Files.createDirectories(shotsDir);
        Path backupDir = shotsDir.resolve(BACKUP_DIR);
        Files.createDirectories(backupDir);
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(shotsDir)) {
            for (Path entry : stream) {
                if (!Files.isRegularFile(entry)) continue;
                String name = entry.getFileName().toString();
                String lower = name.toLowerCase(Locale.ROOT);
                if (!lower.endsWith(".png") && !lower.endsWith(".jpg") && !lower.endsWith(".jpeg")) continue;
                if (OLDEST.equals(name) || MIDDLE.equals(name) || NEWEST.equals(name)) {
                    Files.deleteIfExists(entry);
                } else {
                    Files.move(entry, backupDir.resolve(name), StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
        long base = 1_760_000_000_000L;
        writeCard(shotsDir.resolve(OLDEST), 0x16243A, 0x38BDF8, 1, base - 180_000L);
        writeCard(shotsDir.resolve(MIDDLE), 0x332211, 0xF59E0B, 2, base - 120_000L);
        writeCard(shotsDir.resolve(NEWEST), 0x123126, 0x10B981, 3, base - 60_000L);
    }

    public static void restore(Path gameDir) throws IOException {
        Path shotsDir = gameDir.resolve("screenshots");
        if (!Files.isDirectory(shotsDir)) return;
        Files.deleteIfExists(shotsDir.resolve(OLDEST));
        Files.deleteIfExists(shotsDir.resolve(MIDDLE));
        Files.deleteIfExists(shotsDir.resolve(NEWEST));
        Path backupDir = shotsDir.resolve(BACKUP_DIR);
        if (Files.isDirectory(backupDir)) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(backupDir)) {
                for (Path entry : stream) {
                    if (Files.isRegularFile(entry)) {
                        Files.move(entry, shotsDir.resolve(entry.getFileName()), StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
            Files.deleteIfExists(backupDir);
        }
    }

    public static List<String> extractManageScreenNames(Object screen) throws Exception {
        if (screen == null) throw new IllegalStateException("gallery screen is null");
        Field listField = findField(screen.getClass(), "list");
        Object list = listField.get(screen);
        if (list == null) throw new IllegalStateException("gallery list is null on " + screen.getClass().getName());
        Field widgetsField = findField(list.getClass(), "screenshotWidgets");
        List<?> widgets = (List<?>) widgetsField.get(list);
        List<String> names = new ArrayList<String>();
        Method getFile = null;
        for (Object w : widgets) {
            if (getFile == null) {
                getFile = w.getClass().getDeclaredMethod("getScreenshotFile");
                getFile.setAccessible(true);
            }
            File f = (File) getFile.invoke(w);
            names.add(f.getName());
        }
        return names;
    }

    public static void assertNewestFirst(List<String> actual) {
        if (!EXPECTED_NEWEST_FIRST.equals(actual)) {
            throw new IllegalStateException("Expected newest-first " + EXPECTED_NEWEST_FIRST + " but got " + actual);
        }
    }

    private static Field findField(Class<?> type, String name) throws NoSuchFieldException {
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException ignored) {}
        }
        throw new NoSuchFieldException(name + " on " + type.getName());
    }

    private static void writeCard(Path path, int bgRgb, int accentRgb, int bars, long mtimeMillis) throws IOException {
        BufferedImage img = new BufferedImage(320, 180, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        try {
            g.setColor(new Color(bgRgb));
            g.fillRect(0, 0, 320, 180);
            g.setColor(new Color(accentRgb));
            g.fillRect(0, 0, 320, 26);
            g.drawRect(8, 34, 303, 137);
            for (int i = 0; i < bars; i++) {
                g.fillRect(24 + i * 92, 62, 72, 84);
            }
        } finally {
            g.dispose();
        }
        ImageIO.write(img, "png", path.toFile());
        img.flush();
        Files.setLastModifiedTime(path, FileTime.fromMillis(mtimeMillis));
    }
}
