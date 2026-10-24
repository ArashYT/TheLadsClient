package com.thelads.core.perf;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class AsyncModScannerTest {
    @AfterEach
    void cleanup() {
        AsyncModScanner.clearCache();
    }

    @Test
    void parallelMappingPreservesOrder() {
        List<Integer> inputs = List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
        List<String> mapped = AsyncModScanner.mapParallel(inputs, i -> "item-" + i);

        assertEquals(inputs.size(), mapped.size());
        for (int i = 0; i < inputs.size(); i++) {
            assertEquals("item-" + inputs.get(i), mapped.get(i));
        }
    }

    @Test
    void nonModCacheTracksFileFingerprint() throws IOException {
        File temp = Files.createTempFile("lads_test_lib", ".jar").toFile();
        try {
            assertFalse(AsyncModScanner.isKnownNonMod(temp));
            AsyncModScanner.markNonMod(temp);
            assertTrue(AsyncModScanner.isKnownNonMod(temp));
        } finally {
            temp.delete();
        }
    }
}
