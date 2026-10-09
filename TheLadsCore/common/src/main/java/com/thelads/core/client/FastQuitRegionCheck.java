package com.thelads.core.client;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

/**
 * QA (FastQuit probes): every chunk in every region file of a world reads back, the way Minecraft reads it: a sector
 * table, then per chunk its length, compression and an NBT compound. Same format on 1.8.9, 1.20.1 and 26.x.
 */
public final class FastQuitRegionCheck {
    /** Region files, chunks read and chunks that did not read back, with the first problem. */
    public record Result(int files, int chunks, int bad, String firstProblem) {
        public boolean ok() { return bad == 0; }
        @Override public String toString() { return files + " region files, " + chunks + " chunks, " + bad + " unreadable" + (firstProblem == null ? "" : " (" + firstProblem + ")"); }
    }

    private FastQuitRegionCheck() {}

    public static Result scan(Path worldDir) throws IOException {
        List<Path> regions;
        try (Stream<Path> walk = Files.walk(worldDir)) {
            regions = walk.filter(p -> p.getFileName().toString().endsWith(".mca")).sorted().collect(Collectors.toList());
        }
        int chunks = 0, bad = 0;
        String first = null;
        for (Path region : regions) {
            try (RandomAccessFile file = new RandomAccessFile(region.toFile(), "r")) {
                if (file.length() == 0) continue; // a region Minecraft created but has not written yet
                if (file.length() < 8192) { bad++; if (first == null) first = region.getFileName() + ": header cut short"; continue; }
                int[] offsets = new int[1024];
                for (int i = 0; i < 1024; i++) offsets[i] = file.readInt();
                for (int i = 0; i < 1024; i++) {
                    if (offsets[i] == 0) continue;
                    chunks++;
                    String problem = chunk(file, region, i, offsets[i]);
                    if (problem != null) { bad++; if (first == null) first = region.getFileName() + " chunk " + i + ": " + problem; }
                }
            }
        }
        return new Result(regions.size(), chunks, bad, first);
    }

    private static String chunk(RandomAccessFile file, Path region, int index, int entry) {
        try {
            long start = (long) (entry >>> 8) * 4096;
            int sectors = entry & 0xFF;
            if (start < 8192 || start + 5 > file.length()) return "outside the file";
            file.seek(start);
            int length = file.readInt();
            int type = file.readUnsignedByte();
            byte[] data;
            if ((type & 0x80) != 0) { // stored next to the region file (oversized chunk)
                int x = index & 31, z = index >> 5;
                String[] name = region.getFileName().toString().split("\\.");
                Path external = region.resolveSibling("c." + (Integer.parseInt(name[1]) * 32 + x) + "." + (Integer.parseInt(name[2]) * 32 + z) + ".mcc");
                data = Files.readAllBytes(external);
                type &= 0x7F;
            } else {
                if (length <= 1 || length > sectors * 4096 || start + 4 + length > file.length()) return "length " + length + " outside its " + sectors + " sectors";
                data = new byte[length - 1];
                file.readFully(data);
            }
            InputStream in;
            switch (type) {
                case 1: in = new GZIPInputStream(new ByteArrayInputStream(data)); break;
                case 2: in = new InflaterInputStream(new ByteArrayInputStream(data)); break;
                case 3: in = new ByteArrayInputStream(data); break;
                default: return null; // LZ4 or custom: not checked here (Minecraft defaults to zlib)
            }
            try (InputStream nbt = in) {
                byte[] all = nbt.readAllBytes();
                if (all.length < 3 || all[0] != 10) return "not an NBT compound";
            }
            return null;
        } catch (IOException | RuntimeException e) {
            return e.toString();
        }
    }
}
