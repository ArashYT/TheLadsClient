package com.thelads.core.client.pack;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class LargePackWarningTest {
    private static File sized(Path dir, String name, long bytes) throws IOException {
        File f = dir.resolve(name).toFile();
        try (RandomAccessFile r = new RandomAccessFile(f, "rw")) { r.setLength(bytes); }
        return f;
    }

    @Test
    void onlyNewlyAddedLargePacksWarn(@TempDir Path dir) throws IOException {
        sized(dir, "big.zip", LargePackWarning.LARGE_BYTES);
        sized(dir, "old-big.zip", LargePackWarning.LARGE_BYTES + 5);
        sized(dir, "small.zip", 1024);
        String text = LargePackWarning.forAddedPacks(List.of("file/old-big.zip"),
            List.of("file/old-big.zip", "file/big.zip", "file/small.zip", "vanilla"), dir);
        assertTrue(text.startsWith("big.zip (150 MB)"), text);
        assertFalse(text.contains("old-big"));
        assertFalse(text.contains("small"));
    }

    @Test
    void nothingLargeMeansNoWarning(@TempDir Path dir) throws IOException {
        sized(dir, "small.zip", 1024);
        assertNull(LargePackWarning.forAddedPacks(List.of(), List.of("file/small.zip"), dir));
        assertNull(LargePackWarning.forAddedPacks189(List.of(), List.of("missing.zip"), dir.toFile()));
    }
}
