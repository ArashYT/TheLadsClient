package com.thelads.core.perf;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ZipPackIndexTest {
    @Test
    void extractsValidDomainsAndEntries() {
        List<String> mockEntries = List.of(
            "pack.mcmeta",
            "pack.png",
            "assets/minecraft/textures/blocks/dirt.png",
            "assets/minecraft/sounds.json",
            "assets/custom_pack/textures/gui/bars.png",
            "assets/INVALID_UPPERCASE/texture.png"
        );

        ZipPackIndex index = ZipPackIndex.fromEntries(mockEntries);

        Set<String> domains = index.domains();
        assertEquals(2, domains.size(), "Only lowercase domains are extracted");
        assertTrue(domains.contains("minecraft"));
        assertTrue(domains.contains("custom_pack"));
        assertFalse(domains.contains("INVALID_UPPERCASE"));

        assertTrue(index.hasEntry("pack.mcmeta"));
        assertTrue(index.hasEntry("assets/minecraft/textures/blocks/dirt.png"));
        assertFalse(index.hasEntry("assets/minecraft/textures/blocks/stone.png"));
        assertEquals(6, index.entryCount());
    }

    @Test
    void unmodifiableViews() {
        ZipPackIndex index = ZipPackIndex.fromEntries(List.of("assets/minecraft/texture.png"));
        assertThrows(UnsupportedOperationException.class, () -> index.domains().add("bad"));
    }
}
