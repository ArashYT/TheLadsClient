package com.thelads.core.client.hypixel;

import static org.junit.jupiter.api.Assertions.*;

import com.thelads.core.client.hypixel.quickplay.QuickplayCatalog;
import com.thelads.core.client.hypixel.quickplay.QuickplayCategory;
import com.thelads.core.client.hypixel.quickplay.QuickplayMode;
import com.thelads.core.client.hypixel.quickplay.QuickplayModule;
import java.util.List;
import org.junit.jupiter.api.Test;

class QuickplayCatalogTest {
    @Test
    void testCatalogHasAllCategories() {
        for (QuickplayCategory cat : QuickplayCategory.values()) {
            List<QuickplayMode> modes = QuickplayCatalog.getModes(cat);
            assertFalse(modes.isEmpty(), "Category " + cat + " should have modes");
            for (QuickplayMode mode : modes) {
                assertNotNull(mode.id());
                assertNotNull(mode.name());
                assertNotNull(mode.command());
                assertNotNull(mode.category());
                assertNotNull(mode.iconKey());
            }
        }
    }

    @Test
    void testFindById() {
        QuickplayMode bw = QuickplayCatalog.findById("bedwars_eight_one");
        assertNotNull(bw);
        assertEquals("Solo", bw.name());
        assertEquals("/play bedwars_eight_one", bw.command());
        assertEquals(QuickplayCategory.BEDWARS, bw.category());

        QuickplayMode sw = QuickplayCatalog.findById("solo_normal");
        assertNotNull(sw);
        assertEquals("Solo Normal", sw.name());
        assertEquals("/play solo_normal", sw.command());
        assertEquals(QuickplayCategory.SKYWARS, sw.category());

        assertNull(QuickplayCatalog.findById("nonexistent_mode"));
        assertNull(QuickplayCatalog.findById(null));
    }

    @Test
    void testSearch() {
        List<QuickplayMode> results = QuickplayCatalog.search("bedwars");
        assertFalse(results.isEmpty());
        for (QuickplayMode m : results) {
            assertTrue(m.name().toLowerCase().contains("bedwars")
                || m.id().toLowerCase().contains("bedwars")
                || m.category().getDisplayName().toLowerCase().contains("bedwars"));
        }

        List<QuickplayMode> emptySearch = QuickplayCatalog.search("");
        assertEquals(QuickplayCatalog.getAllModes().size(), emptySearch.size());
    }

    @Test
    void testFavoritesLimit() {
        assertEquals(9, QuickplayModule.MAX_FAVORITES);
    }
}
