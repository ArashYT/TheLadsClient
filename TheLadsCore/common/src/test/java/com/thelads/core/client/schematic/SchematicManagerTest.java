package com.thelads.core.client.schematic;

import static org.junit.jupiter.api.Assertions.*;

import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPOutputStream;

public class SchematicManagerTest {
    @BeforeEach
    void setUp() {
        Module mod = ModuleManager.getInstance().getModule(SchematicManager.NAME);
        if (mod != null) mod.setEnabled(true);
        SchematicManager.clear();
        SchematicManager.setOrigin(100, 64, 200);
    }

    @AfterEach
    void tearDown() {
        SchematicManager.clear();
    }

    @Test
    void testLoadAndOrigin() {
        List<SchematicBlock> blocks = new ArrayList<>();
        blocks.add(new SchematicBlock(0, 0, 0, 1, "stone", 0x8055FFFF));
        blocks.add(new SchematicBlock(1, 0, 0, 4, "cobblestone", 0x8055FFFF));

        SchematicManager.load("test_house", 2, 1, 1, blocks);
        assertTrue(SchematicManager.isLoaded());
        assertEquals("test_house", SchematicManager.getLoadedName());
        assertEquals(2, SchematicManager.getWidth());
        assertEquals(1, SchematicManager.getHeight());
        assertEquals(1, SchematicManager.getLength());
        assertEquals(2, SchematicManager.getBlocks().size());
        assertEquals(100, SchematicManager.getOriginX());
        assertEquals(64, SchematicManager.getOriginY());
        assertEquals(200, SchematicManager.getOriginZ());
    }

    @Test
    void testDrawGeneratesCorrectLines() {
        List<SchematicBlock> blocks = new ArrayList<>();
        blocks.add(new SchematicBlock(0, 0, 0, 1, "stone", 0x8055FFFF));
        SchematicManager.load("single_block", 1, 1, 1, blocks);

        List<String> renderedLines = new ArrayList<>();
        SchematicManager.Lines collector = (x0, y0, z0, x1, y1, z1, argb) -> {
            renderedLines.add(String.format("%.1f,%.1f,%.1f -> %.1f,%.1f,%.1f", x0, y0, z0, x1, y1, z1));
        };

        // Draw near camera (camera at 100, 64, 200)
        SchematicManager.draw(collector, 100.0, 64.0, 200.0);
        // Bounding box of schematic (12 lines) + 1 block (12 lines) = 24 lines
        assertEquals(24, renderedLines.size());
    }

    @Test
    void testParseClassicNbtSchematic() throws Exception {
        // Construct a synthetic classic .schematic NBT payload (GZIP Tag_Compound)
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (GZIPOutputStream gzos = new GZIPOutputStream(baos);
             DataOutputStream dos = new DataOutputStream(gzos)) {
            dos.writeByte(10); // Tag_Compound
            dos.writeUTF("Schematic");

            // Width: short 2
            dos.writeByte(2); // Tag_Short
            dos.writeUTF("Width");
            dos.writeShort(2);

            // Height: short 1
            dos.writeByte(2);
            dos.writeUTF("Height");
            dos.writeShort(1);

            // Length: short 2
            dos.writeByte(2);
            dos.writeUTF("Length");
            dos.writeShort(2);

            // Materials: string Alpha
            dos.writeByte(8); // Tag_String
            dos.writeUTF("Materials");
            dos.writeUTF("Alpha");

            // Blocks: byte array (2*1*2 = 4 blocks)
            dos.writeByte(7); // Tag_ByteArray
            dos.writeUTF("Blocks");
            dos.writeInt(4);
            dos.write(new byte[]{1, 0, 4, 1}); // 3 non-air blocks, 1 air block

            // End tag
            dos.writeByte(0); // Tag_End
        }

        byte[] schematicBytes = baos.toByteArray();
        boolean loaded = SchematicManager.loadFromBytes("synthetic.schematic", schematicBytes);
        assertTrue(loaded);
        assertEquals(3, SchematicManager.getBlocks().size());
        assertEquals(2, SchematicManager.getWidth());
        assertEquals(1, SchematicManager.getHeight());
        assertEquals(2, SchematicManager.getLength());
    }
}
