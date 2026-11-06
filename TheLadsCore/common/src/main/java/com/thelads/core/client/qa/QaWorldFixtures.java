package com.thelads.core.client.qa;

import com.thelads.core.client.ServerListColumns;
import com.thelads.core.client.ServerListDrag;
import com.thelads.core.client.WorldListOrder;
import com.thelads.core.client.util.ClientPaths;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.SliderOption;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

/**
 * QA fixture helper for Singleplayer world list columns & drag reordering (Java 8 compatible).
 * Seeds deterministic save folders with minimal valid level.dat files, verifies level.dat bytes & mtimes are never touched
 * when reordering worlds, and restores original saves & world order afterward.
 */
public final class QaWorldFixtures {
    public static final String W_ALPHA = "qa_b3_alpha";
    public static final String W_BRAVO = "qa_b3_bravo";
    public static final String W_CHARLIE = "qa_b3_charlie";
    public static final String W_DELTA = "qa_b3_delta";
    public static final String W_ECHO = "qa_b3_echo";

    private static byte[] priorOrderBytes;
    private static boolean hadPriorOrder;
    private static Path backupDir;
    private static final Map<String, byte[]> seededBytes = new HashMap<String, byte[]>();
    private static final Map<String, Long> seededMtimes = new HashMap<String, Long>();

    private QaWorldFixtures() {}

    public static void seed(Path savesDir, int dataVersion, String versionName) throws IOException {
        Files.createDirectories(savesDir);
        File orderFile = ClientPaths.getWorldOrderFile();
        hadPriorOrder = orderFile != null && orderFile.isFile();
        priorOrderBytes = hadPriorOrder ? Files.readAllBytes(orderFile.toPath()) : null;
        if (orderFile != null) {
            for (File mirror : ClientPaths.getMirrorTargetsForSharedFile(WorldListOrder.FILE_NAME)) {
                Files.deleteIfExists(mirror.toPath());
            }
            Files.deleteIfExists(orderFile.toPath());
        }

        backupDir = savesDir.getParent().resolve(".qa-b3-saves-backup");
        deleteTree(backupDir);
        Files.createDirectories(backupDir);
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(savesDir)) {
            for (Path entry : ds) {
                String name = entry.getFileName().toString();
                if (name.startsWith("qa_b3_")) {
                    deleteTree(entry);
                } else {
                    Files.move(entry, backupDir.resolve(name), StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }

        seededBytes.clear();
        seededMtimes.clear();
        writeWorld(savesDir, W_ALPHA, "Alpha Oldest World", 1700000000000L, dataVersion, versionName);
        writeWorld(savesDir, W_BRAVO, "Bravo Older World", 1710000000000L, dataVersion, versionName);
        writeWorld(savesDir, W_CHARLIE, "Charlie Newer World", 1720000000000L, dataVersion, versionName);
        writeWorld(savesDir, W_DELTA, "Delta Newest World", 1730000000000L, dataVersion, versionName);
    }

    public static void addExtraUnsavedWorld(Path savesDir, int dataVersion, String versionName) throws IOException {
        writeWorld(savesDir, W_ECHO, "Echo Fresh World", 1600000000000L, dataVersion, versionName);
    }

    public static void removeExtraUnsavedWorld(Path savesDir) throws IOException {
        seededBytes.remove(W_ECHO);
        seededMtimes.remove(W_ECHO);
        deleteTree(savesDir.resolve(W_ECHO));
    }

    public static void assertLevelDatUntouched(Path savesDir) throws IOException {
        for (Map.Entry<String, byte[]> e : seededBytes.entrySet()) {
            Path levelDat = savesDir.resolve(e.getKey()).resolve("level.dat");
            if (!Files.isRegularFile(levelDat)) {
                throw new IllegalStateException("Missing level.dat for " + e.getKey());
            }
            byte[] nowBytes = Files.readAllBytes(levelDat);
            long nowMtime = Files.getLastModifiedTime(levelDat).toMillis();
            if (!Arrays.equals(e.getValue(), nowBytes)) {
                throw new IllegalStateException("level.dat bytes were modified for " + e.getKey());
            }
            long expectedMtime = seededMtimes.get(e.getKey()).longValue();
            if (Math.abs(nowMtime - expectedMtime) > 5L) {
                throw new IllegalStateException("level.dat mtime changed for " + e.getKey() + ": " + expectedMtime + " -> " + nowMtime);
            }
        }
    }

    public static void assertOrder(List<String> actualIds, String[] expectedIds, Path savesDir) throws IOException {
        List<String> expected = Arrays.asList(expectedIds);
        if (!expected.equals(actualIds)) {
            throw new IllegalStateException("World list order mismatch: expected " + expected + " but got " + actualIds);
        }
        assertLevelDatUntouched(savesDir);
    }

    public static void assertSavedFileEquals(String[] expectedIds) {
        List<String> saved = WorldListOrder.loadOrder();
        List<String> expected = Arrays.asList(expectedIds);
        if (!expected.equals(saved)) {
            throw new IllegalStateException("thelads_world_order.json mismatch: expected " + expected + " but got " + saved);
        }
    }

    public static void restore(Path savesDir) throws IOException {
        if (savesDir != null && Files.isDirectory(savesDir)) {
            try (DirectoryStream<Path> ds = Files.newDirectoryStream(savesDir)) {
                for (Path entry : ds) {
                    if (entry.getFileName().toString().startsWith("qa_b3_")) {
                        deleteTree(entry);
                    }
                }
            }
            if (backupDir != null && Files.isDirectory(backupDir)) {
                try (DirectoryStream<Path> ds = Files.newDirectoryStream(backupDir)) {
                    for (Path entry : ds) {
                        Files.move(entry, savesDir.resolve(entry.getFileName().toString()), StandardCopyOption.REPLACE_EXISTING);
                    }
                }
                deleteTree(backupDir);
            }
        }
        File orderFile = ClientPaths.getWorldOrderFile();
        if (orderFile != null) {
            if (hadPriorOrder && priorOrderBytes != null) {
                Files.write(orderFile.toPath(), priorOrderBytes);
            } else {
                for (File mirror : ClientPaths.getMirrorTargetsForSharedFile(WorldListOrder.FILE_NAME)) {
                    Files.deleteIfExists(mirror.toPath());
                }
                Files.deleteIfExists(orderFile.toPath());
            }
        }
        SliderOption opt = (SliderOption) ModuleManager.getInstance().getModule(ServerListDrag.NAME).getOption(ServerListColumns.OPTION);
        if (opt != null) {
            opt.setValue(1);
        }
    }

    private static void writeWorld(Path savesDir, String folder, String displayName, long lastPlayed, int dataVersion, String versionName) throws IOException {
        Path dir = savesDir.resolve(folder);
        Files.createDirectories(dir);
        Path levelDat = dir.resolve("level.dat");
        byte[] bytes = buildLevelDat(displayName, lastPlayed, dataVersion, versionName);
        Files.write(levelDat, bytes);
        FileTime fixedTime = FileTime.fromMillis(lastPlayed);
        Files.setLastModifiedTime(levelDat, fixedTime);
        seededBytes.put(folder, Files.readAllBytes(levelDat));
        seededMtimes.put(folder, Long.valueOf(Files.getLastModifiedTime(levelDat).toMillis()));
    }

    private static byte[] buildLevelDat(String levelName, long lastPlayed, int dataVersion, String versionName) throws IOException {
        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        try (GZIPOutputStream gz = new GZIPOutputStream(raw); DataOutputStream out = new DataOutputStream(gz)) {
            out.writeByte(10); // root TAG_Compound
            out.writeUTF("");

            out.writeByte(10); // Data TAG_Compound
            out.writeUTF("Data");

            writeString(out, "LevelName", levelName);
            writeLong(out, "LastPlayed", lastPlayed);
            writeInt(out, "version", 19133);
            writeInt(out, "GameType", 0);
            writeByte(out, "Difficulty", 2);
            writeByte(out, "hardcore", 0);
            writeByte(out, "allowCommands", 1);
            writeByte(out, "initialized", 1);
            if (dataVersion > 0) {
                writeInt(out, "DataVersion", dataVersion);
                out.writeByte(10); // Version TAG_Compound
                out.writeUTF("Version");
                writeInt(out, "Id", dataVersion);
                writeString(out, "Name", versionName != null ? versionName : "");
                writeString(out, "Series", "main");
                writeByte(out, "Snapshot", 0);
                out.writeByte(0); // end Version
            }

            out.writeByte(0); // end Data
            out.writeByte(0); // end root
        }
        return raw.toByteArray();
    }

    private static void writeString(DataOutputStream out, String name, String val) throws IOException {
        out.writeByte(8);
        out.writeUTF(name);
        out.writeUTF(val);
    }

    private static void writeLong(DataOutputStream out, String name, long val) throws IOException {
        out.writeByte(4);
        out.writeUTF(name);
        out.writeLong(val);
    }

    private static void writeInt(DataOutputStream out, String name, int val) throws IOException {
        out.writeByte(3);
        out.writeUTF(name);
        out.writeInt(val);
    }

    private static void writeByte(DataOutputStream out, String name, int val) throws IOException {
        out.writeByte(1);
        out.writeUTF(name);
        out.writeByte(val);
    }

    private static void deleteTree(Path root) throws IOException {
        if (root == null || !Files.exists(root)) return;
        if (Files.isDirectory(root)) {
            List<Path> children = new ArrayList<Path>();
            try (DirectoryStream<Path> ds = Files.newDirectoryStream(root)) {
                for (Path c : ds) children.add(c);
            }
            for (Path c : children) deleteTree(c);
        }
        Files.deleteIfExists(root);
    }
}
