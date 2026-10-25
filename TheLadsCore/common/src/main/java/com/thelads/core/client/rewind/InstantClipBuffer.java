package com.thelads.core.client.rewind;

import com.thelads.core.client.HypixelSafetyManager;
import com.thelads.core.client.bridge.LadsGameBridge;
import com.thelads.core.config.BoolOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.SliderOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Date;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Instant clip / rewind buffer: circular in-memory network packet ring buffer (parity with Lunar Rewind).
 * Maintains recent packets up to a configurable duration (30-60s) with a hard memory cap (default 64 MB, capped at 128 MB).
 * Serializes buffered network packet traces into .mcpr container archives (metaData.json + recording.tmcpr) when triggered.
 */
public final class InstantClipBuffer {
    private static final Logger LOGGER = LoggerFactory.getLogger("InstantClip");
    public static final String NAME = "Instant Clip";
    public static final String DURATION_OPTION = "Buffer Duration";
    public static final String MEMORY_CAP_OPTION = "Memory Cap";

    public static final long DEFAULT_MEMORY_CAP_BYTES = 64 * 1024 * 1024L; // 64 MB
    public static final int DEFAULT_DURATION_SECONDS = 30;

    public record PacketRecord(long timestampMs, int packetId, byte[] data) {
        public int size() {
            return 24 + (data != null ? data.length : 0);
        }
    }

    private static final Deque<PacketRecord> BUFFER = new ArrayDeque<>();
    private static long currentMemoryBytes = 0;
    private static long memoryCapBytes = DEFAULT_MEMORY_CAP_BYTES;
    private static int durationSeconds = DEFAULT_DURATION_SECONDS;
    private static Module module;

    private InstantClipBuffer() {}

    private static Module getModule() {
        if (module == null) module = ModuleManager.getInstance().getModule(NAME);
        return module;
    }

    public static boolean isEnabled() {
        Module m = getModule();
        if (m == null || !m.isEnabled()) return false;
        return !HypixelSafetyManager.isDisallowed(NAME);
    }

    public static synchronized void setMemoryCapBytes(long bytes) {
        memoryCapBytes = Math.max(1024L, Math.min(128 * 1024 * 1024L, bytes));
        Module m = getModule();
        if (m != null && m.getOption(MEMORY_CAP_OPTION) instanceof SliderOption s) {
            s.setValue(Math.max(1, (int) (bytes / (1024 * 1024L))));
        }
        pruneMemory();
    }

    public static synchronized long getMemoryCapBytes() {
        Module m = getModule();
        if (m != null && m.getOption(MEMORY_CAP_OPTION) instanceof SliderOption s) {
            long sliderBytes = (long) s.getValue() * 1024 * 1024L;
            if (memoryCapBytes >= 1024 * 1024L) {
                return sliderBytes;
            }
        }
        return memoryCapBytes;
    }

    public static synchronized void setDurationSeconds(int seconds) {
        durationSeconds = Math.max(1, Math.min(120, seconds));
        Module m = getModule();
        if (m != null && m.getOption(DURATION_OPTION) instanceof SliderOption s) {
            s.setValue(durationSeconds);
        }
        if (!BUFFER.isEmpty()) {
            pruneTime(System.currentTimeMillis());
        }
    }

    public static synchronized int getDurationSeconds() {
        Module m = getModule();
        if (m != null && m.getOption(DURATION_OPTION) instanceof SliderOption s) {
            return (int) s.getValue();
        }
        return durationSeconds;
    }

    public static synchronized int getPacketCount() {
        return BUFFER.size();
    }

    public static synchronized long getCurrentMemoryBytes() {
        return currentMemoryBytes;
    }

    public static synchronized void clear() {
        BUFFER.clear();
        currentMemoryBytes = 0;
    }

    /**
     * Records an incoming network packet sample into the circular buffer.
     */
    public static synchronized void recordPacket(long timestampMs, int packetId, byte[] data) {
        if (!isEnabled()) {
            if (currentMemoryBytes > 0) clear();
            return;
        }

        long capBytes = getMemoryCapBytes();

        byte[] payload = data != null ? data : new byte[0];
        PacketRecord record = new PacketRecord(timestampMs, packetId, payload);
        int recordSize = record.size();

        // Evict packets older than duration
        pruneTime(timestampMs);

        // Evict oldest packets until below memory cap
        while (!BUFFER.isEmpty() && (currentMemoryBytes + recordSize > capBytes)) {
            PacketRecord removed = BUFFER.pollFirst();
            if (removed != null) {
                currentMemoryBytes -= removed.size();
            }
        }

        if (currentMemoryBytes + recordSize <= capBytes) {
            BUFFER.addLast(record);
            currentMemoryBytes += recordSize;
        }
    }

    private static void pruneTime(long nowMs) {
        long cutoffMs = nowMs - (getDurationSeconds() * 1000L);
        while (!BUFFER.isEmpty()) {
            PacketRecord first = BUFFER.peekFirst();
            if (first != null && first.timestampMs() < cutoffMs) {
                BUFFER.pollFirst();
                currentMemoryBytes -= first.size();
            } else {
                break;
            }
        }
    }

    private static void pruneMemory() {
        long capBytes = getMemoryCapBytes();
        while (!BUFFER.isEmpty() && currentMemoryBytes > capBytes) {
            PacketRecord removed = BUFFER.pollFirst();
            if (removed != null) {
                currentMemoryBytes -= removed.size();
            }
        }
    }

    public static byte[] varIntBytes(int value) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        while ((value & 0xFFFFFF80) != 0) {
            baos.write((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        baos.write(value & 0x7F);
        return baos.toByteArray();
    }

    /**
     * Serializes the current circular buffer into a standard .mcpr replay archive container.
     * Takes an off-lock snapshot so Netty network threads are never blocked during disk I/O.
     */
    public static File saveClip(File targetDir, String serverName, String mcVersion, int protocolVersion) throws IOException {
        List<PacketRecord> snapshot;
        long startMs, endMs;
        int durationMs;
        int packetCount;

        synchronized (InstantClipBuffer.class) {
            if (BUFFER.isEmpty()) return null;
            snapshot = new ArrayList<>(BUFFER);
            PacketRecord first = snapshot.get(0);
            PacketRecord last = snapshot.get(snapshot.size() - 1);
            startMs = first.timestampMs();
            endMs = last.timestampMs();
            durationMs = (int) Math.max(1, endMs - startMs);
            packetCount = snapshot.size();
        }

        if (targetDir == null) {
            targetDir = new File("replay_recordings");
        }
        if (!targetDir.exists()) {
            targetDir.mkdirs();
        }

        String dateStr = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.ROOT).format(new Date(startMs));
        File replayFile = new File(targetDir, "clip_" + dateStr + ".mcpr");

        // Prepare metaData.json (standard ReplayMod metadata naming)
        String effectiveServer = (serverName != null && !serverName.isEmpty()) ? serverName : "Singleplayer";
        String effectiveVersion = (mcVersion != null && !mcVersion.isEmpty()) ? mcVersion : "1.8.9";
        String metaJson = "{\n"
            + "  \"singleplayer\": " + "Singleplayer".equalsIgnoreCase(effectiveServer) + ",\n"
            + "  \"serverName\": \"" + effectiveServer + "\",\n"
            + "  \"duration\": " + durationMs + ",\n"
            + "  \"date\": " + startMs + ",\n"
            + "  \"mcversion\": \"" + effectiveVersion + "\",\n"
            + "  \"fileFormat\": \"MCPR\",\n"
            + "  \"fileFormatVersion\": 14,\n"
            + "  \"protocol\": " + protocolVersion + ",\n"
            + "  \"generator\": \"The Lads Client Instant Clip\"\n"
            + "}\n";

        // Write .mcpr (ZIP archive containing metaData.json and recording.tmcpr)
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(replayFile))) {
            // Entry 1: metaData.json
            ZipEntry metaEntry = new ZipEntry("metaData.json");
            zos.putNextEntry(metaEntry);
            zos.write(metaJson.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            // Entry 2: recording.tmcpr
            ZipEntry dataEntry = new ZipEntry("recording.tmcpr");
            zos.putNextEntry(dataEntry);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);

            for (PacketRecord packet : snapshot) {
                int timestampOffset = (int) Math.max(0, packet.timestampMs() - startMs);
                byte[] varInt = varIntBytes(packet.packetId());
                byte[] raw = packet.data() != null ? packet.data() : new byte[0];
                int packetLength = varInt.length + raw.length;
                dos.writeInt(timestampOffset);
                dos.writeInt(packetLength);
                dos.write(varInt);
                if (raw.length > 0) {
                    dos.write(raw);
                }
            }
            dos.flush();
            zos.write(baos.toByteArray());
            zos.closeEntry();
        }

        LOGGER.info("Instant clip saved: {} ({} packets, {} ms)", replayFile.getName(), packetCount, durationMs);
        return replayFile;
    }

    /**
     * Triggers asynchronous clip save on a background thread and sends a chat notification only upon completion.
     */
    public static void triggerAsyncSave(File targetDir, String serverName, String mcVersion, int protocolVersion) {
        new Thread(() -> {
            try {
                File file = saveClip(targetDir, serverName, mcVersion, protocolVersion);
                if (file != null) {
                    LOGGER.info("Instant clip export complete: {}", file.getAbsolutePath());
                    Module m = getModule();
                    if (m != null && m.getOption("Chat Notification") instanceof BoolOption b && b.get()) {
                        LadsGameBridge.get().displayChatMessage("\u00a7a[Instant Clip] Saved clip to " + file.getName());
                    }
                }
            } catch (Exception e) {
                LOGGER.error("Failed to save instant clip: {}", e.getMessage(), e);
            }
        }, "InstantClip-Saver").start();
    }
}