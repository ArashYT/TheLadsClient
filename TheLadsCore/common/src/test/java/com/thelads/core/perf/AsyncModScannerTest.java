package com.thelads.core.perf;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class AsyncModScannerTest {
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
    void handlesEmptyAndSingleItemList() {
        assertTrue(AsyncModScanner.mapParallel(List.of(), Object::toString).isEmpty());
        assertEquals(List.of("single"), AsyncModScanner.mapParallel(List.of("single"), String::toLowerCase));
    }
}
