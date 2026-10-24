package com.thelads.core.client.schematic;

import com.thelads.core.modules.SchematicModule;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SchematicManagerTest {
    @BeforeEach
    void setUp() {
        SchematicManager.clear();
    }

    @Test
    void testDefaults() {
        SchematicModule mod = new SchematicModule();
        assertFalse(mod.isEnabled(), "Gated module must default off");
        assertEquals(60, mod.opacity.getIntValue());
        assertTrue(mod.highlightMismatch.get());
    }

    @Test
    void testSchematicOperations() {
        assertFalse(SchematicManager.isLoaded());
        assertNull(SchematicManager.getLoadedName());

        SchematicManager.load("House.schem", List.of(
            new SchematicManager.SchematicBlock(0, 0, 0, "minecraft:oak_planks"),
            new SchematicManager.SchematicBlock(1, 0, 0, "minecraft:stone")
        ));
        SchematicManager.setOrigin(100, 64, 200);

        assertTrue(SchematicManager.isLoaded());
        assertEquals("House.schem", SchematicManager.getLoadedName());
        assertEquals(2, SchematicManager.getBlocks().size());
        assertEquals(100, SchematicManager.getOriginX());
        assertEquals(64, SchematicManager.getOriginY());
        assertEquals(200, SchematicManager.getOriginZ());

        SchematicManager.clear();
        assertFalse(SchematicManager.isLoaded());
        assertNull(SchematicManager.getLoadedName());
        assertTrue(SchematicManager.getBlocks().isEmpty());
    }
}
