package com.thelads.core.client;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.DeflaterOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FastQuitRegionCheckTest {
    @Test void readsGoodChunksAndCountsBrokenOnes(@TempDir Path world) throws Exception {
        ByteArrayOutputStream nbt = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(new DeflaterOutputStream(nbt))) {
            out.writeByte(10); out.writeShort(0); out.writeByte(0); // empty root compound
        }
        byte[] chunk = nbt.toByteArray();
        ByteArrayOutputStream file = new ByteArrayOutputStream();
        DataOutputStream region = new DataOutputStream(file);
        region.writeInt(2 << 8 | 1);  // chunk 0: sector 2, 1 sector
        region.writeInt(3 << 8 | 1);  // chunk 1: sector 3, its data cut off below
        for (int i = 2; i < 2048; i++) region.writeInt(0);
        region.writeInt(chunk.length + 1); region.writeByte(2); region.write(chunk);
        while (file.size() < 3 * 4096) region.writeByte(0);
        region.writeInt(4000); region.writeByte(2); // claims 4000 bytes, the file ends
        Files.createDirectories(world.resolve("region"));
        Files.write(world.resolve("region/r.0.0.mca"), file.toByteArray());
        Files.write(world.resolve("region/r.1.0.mca"), new byte[0]); // created, never written

        FastQuitRegionCheck.Result result = FastQuitRegionCheck.scan(world);
        assertEquals(2, result.files());
        assertEquals(2, result.chunks());
        assertEquals(1, result.bad(), result.toString());
        assertTrue(result.firstProblem().startsWith("r.0.0.mca chunk 1"), result.toString());
    }
}
