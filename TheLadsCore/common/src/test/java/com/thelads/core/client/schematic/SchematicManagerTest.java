package com.thelads.core.client.schematic;

import static org.junit.jupiter.api.Assertions.*;

import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
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

    private interface Tags { void write(DataOutputStream dos) throws java.io.IOException; }

    /** A gzipped classic schematic: the root compound around {@code tags}. */
    private static byte[] schematic(Tags tags) throws java.io.IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (DataOutputStream dos = new DataOutputStream(new GZIPOutputStream(baos))) {
            dos.writeByte(10);
            dos.writeUTF("Schematic");
            tags.write(dos);
            dos.writeByte(0);
        }
        return baos.toByteArray();
    }

    private static void size(DataOutputStream dos, int w, int h, int l) throws java.io.IOException {
        dos.writeByte(2); dos.writeUTF("Width"); dos.writeShort(w);
        dos.writeByte(2); dos.writeUTF("Height"); dos.writeShort(h);
        dos.writeByte(2); dos.writeUTF("Length"); dos.writeShort(l);
    }

    @Test
    @org.junit.jupiter.api.Timeout(20)
    void malformedFilesAreRefusedWithoutHangingOrRunningOutOfMemory() throws Exception {
        // A 2 GB array: refused before it is allocated (an OutOfMemoryError slipped past catch (Exception)).
        assertFalse(SchematicManager.loadFromBytes("huge-array", schematic(dos -> {
            dos.writeByte(7); dos.writeUTF("Blocks"); dos.writeInt(Integer.MAX_VALUE);
        })));
        // 32767^3 blocks with 4 bytes of data: refused, not looped over for days.
        assertFalse(SchematicManager.loadFromBytes("huge-size", schematic(dos -> {
            size(dos, 32767, 32767, 32767);
            dos.writeByte(7); dos.writeUTF("Blocks"); dos.writeInt(4); dos.write(new byte[]{1, 1, 1, 1});
        })));
        // A list claiming millions of entries in a few bytes: the end of the file stops it.
        assertFalse(SchematicManager.loadFromBytes("endless-list", schematic(dos -> {
            dos.writeByte(9); dos.writeUTF("Entities"); dos.writeByte(4); dos.writeInt(60_000_000);
        })));
        assertFalse(SchematicManager.loadFromBytes("end-list", schematic(dos -> {
            dos.writeByte(9); dos.writeUTF("Entities"); dos.writeByte(0); dos.writeInt(Integer.MAX_VALUE);
        })));
        assertFalse(SchematicManager.isLoaded());

        // Fine files still load, block by block where the nested loops put them: index = (y * length + z) * width + x.
        assertTrue(SchematicManager.loadFromBytes("cube", schematic(dos -> {
            size(dos, 2, 2, 2);
            dos.writeByte(7); dos.writeUTF("Blocks"); dos.writeInt(8); dos.write(new byte[]{1, 2, 3, 4, 5, 6, 7, 8});
        })));
        assertEquals(8, SchematicManager.getBlocks().size());
        assertEquals(new SchematicBlock(1, 1, 0, 6, "block_6", 0x8055FFFF), SchematicManager.getBlocks().get(5));
    }

    @Test
    void testLoadSchematicFileFromDisk(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("disk_test.schematic");
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (GZIPOutputStream gzos = new GZIPOutputStream(baos);
             DataOutputStream dos = new DataOutputStream(gzos)) {
            dos.writeByte(10);
            dos.writeUTF("Schematic");
            dos.writeByte(2); dos.writeUTF("Width"); dos.writeShort(1);
            dos.writeByte(2); dos.writeUTF("Height"); dos.writeShort(1);
            dos.writeByte(2); dos.writeUTF("Length"); dos.writeShort(1);
            dos.writeByte(7); dos.writeUTF("Blocks"); dos.writeInt(1);
            dos.write(new byte[]{1});
            dos.writeByte(0);
        }
        Files.write(file, baos.toByteArray());

        // Must succeed without mark/reset exception on FileInputStream
        boolean loaded = SchematicManager.loadSchematicFile(file.toFile());
        assertTrue(loaded, "FileInputStream wrapped in BufferedInputStream must load successfully");
        assertEquals(1, SchematicManager.getBlocks().size());
        assertEquals("disk_test.schematic", SchematicManager.getLoadedName());
    }
}
