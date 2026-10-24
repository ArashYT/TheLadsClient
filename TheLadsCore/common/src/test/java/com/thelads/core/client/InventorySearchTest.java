package com.thelads.core.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

public class InventorySearchTest {
    @BeforeEach
    public void setup() {
        InventorySearch.setActive(false);
        InventorySearch.setQuery("");
        com.thelads.core.config.Module mod = com.thelads.core.config.ModuleManager.getInstance().getModule("InventorySearch");
        if (mod != null) mod.setEnabled(true);
    }

    @Test
    public void testSearchMatching() {
        assertTrue(InventorySearch.matches("Diamond Sword", null));

        InventorySearch.setActive(true);
        InventorySearch.setQuery("diamond");

        assertTrue(InventorySearch.matches("Diamond Sword", null));
        assertFalse(InventorySearch.matches("Iron Ingot", null));
        assertTrue(InventorySearch.matches("Random Item", List.of("A sparkling diamond inside")));
        assertFalse(InventorySearch.matches("Random Item", List.of("Just an ordinary stick")));
    }
}
