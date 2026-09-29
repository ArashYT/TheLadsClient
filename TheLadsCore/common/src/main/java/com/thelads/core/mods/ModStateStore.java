package com.thelads.core.mods;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.thelads.core.shared.FileLocks;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Per-profile mod preferences shared with The Lads Launcher: {@code <gameDir>/lads-mod-state.json}.
 * Keys are Fabric mod ids; an absent key means "keep the current disk state". The game only records next-launch
 * requests here (source "game"); the launcher renames jars before the next process starts.
 */
public final class ModStateStore {
    public static final String FILE_NAME = "lads-mod-state.json";
    private static final Logger LOG = LoggerFactory.getLogger("TheLadsCore");
    // serializeNulls: a null-valued key another writer put in the file is still one of its keys and must survive our rewrite.
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().serializeNulls().create();
    private static final long LOCK_TIMEOUT_MS = 3000;

    public record Request(boolean enabled, String projectId) {}

    /** Explicit requests; error is set when the file exists but could not be used (every mod then keeps its disk state). */
    public record State(Map<String, Request> mods, String error) {
        public State {
            mods = Map.copyOf(mods);
        }

        /** The explicit request for id, falling back to a key with the same projectId (mod ids can differ across versions). */
        public Boolean enabled(String id, String projectId) {
            Request request = mods.get(id);
            if (request != null) return request.enabled();
            if (projectId != null && !projectId.isBlank())
                for (Request candidate : mods.values())
                    if (projectId.equals(candidate.projectId())) return candidate.enabled();
            return null;
        }
    }

    private final Path file;
    private final Path lockFile;

    public ModStateStore(Path gameDirectory) {
        file = gameDirectory.resolve(FILE_NAME);
        lockFile = gameDirectory.resolve(FILE_NAME + ".lock");
    }

    public Path file() {
        return file;
    }

    /** Lock-free read: a missing file means no explicit requests; a malformed one is reported, never thrown. */
    public State read() {
        String text;
        try {
            text = Files.readString(file);
        } catch (NoSuchFileException missing) {
            return new State(Map.of(), null);
        } catch (IOException e) {
            return new State(Map.of(), "Could not read " + file + ": " + e.getMessage());
        }
        try {
            return new State(requests(parse(text)), null);
        } catch (JsonParseException corrupt) {
            return new State(Map.of(), FILE_NAME + " is damaged (" + corrupt.getMessage()
                + "). Every mod keeps its current state; the damaged file is kept aside on the next change.");
        }
    }

    /** Change stamp for cheap polling (modification time and size). */
    public String stamp() {
        try {
            return Files.getLastModifiedTime(file).toMillis() + ":" + Files.size(file);
        } catch (NoSuchFileException missing) {
            return "absent";
        } catch (IOException e) {
            // read() reports the same failure to the user; the stamp only has to differ from a readable state.
            return "unreadable:" + e.getMessage();
        }
    }

    /**
     * Records next-launch requests in one locked read-modify-write that keeps every unknown key and field.
     * A damaged file is renamed to {@code lads-mod-state.json.corrupt-<stamp>} first; a file that cannot be read is never replaced.
     */
    public void setRequested(Map<String, Boolean> requests, Map<String, String> projectIds) throws IOException {
        FileLocks.withLock(lockFile, LOCK_TIMEOUT_MS, () -> {
            JsonObject root = new JsonObject();
            if (Files.exists(file)) {
                String text = Files.readString(file);
                try {
                    root = parse(text);
                } catch (JsonParseException corrupt) {
                    Path kept = keepCorruptCopy();
                    LOG.warn("{} was damaged ({}); kept it as {} and wrote a new file", file, corrupt.getMessage(), kept);
                    root = new JsonObject();
                }
            }
            if (!root.has("schema")) root.addProperty("schema", 1);
            JsonObject mods = root.has("mods") ? root.getAsJsonObject("mods") : new JsonObject();
            root.add("mods", mods);
            String now = Instant.now().truncatedTo(ChronoUnit.SECONDS).toString();
            requests.forEach((id, enabled) -> {
                JsonObject entry = mods.get(id) instanceof JsonObject existing ? existing : new JsonObject();
                entry.addProperty("enabled", enabled);
                String projectId = projectIds.get(id);
                if (projectId != null && !projectId.isBlank()) entry.addProperty("projectId", projectId);
                entry.addProperty("updatedAt", now);
                entry.addProperty("source", "game");
                mods.add(id, entry);
            });
            writeAtomically(file, GSON.toJson(root));
            return null;
        });
    }

    private static JsonObject parse(String text) {
        JsonElement root = JsonParser.parseString(text);
        if (!root.isJsonObject()) throw new JsonParseException("the top level is not a JSON object");
        JsonElement mods = root.getAsJsonObject().get("mods");
        if (mods != null && !mods.isJsonObject()) throw new JsonParseException("\"mods\" is not a JSON object");
        return root.getAsJsonObject();
    }

    private static Map<String, Request> requests(JsonObject root) {
        Map<String, Request> result = new LinkedHashMap<>();
        JsonObject mods = root.getAsJsonObject("mods");
        if (mods == null) return result;
        for (var entry : mods.entrySet()) {
            // Entries this version cannot interpret are no preference, but they stay in the file untouched.
            if (!(entry.getValue() instanceof JsonObject value)) continue;
            JsonElement enabled = value.get("enabled");
            if (enabled == null || !enabled.isJsonPrimitive() || !enabled.getAsJsonPrimitive().isBoolean()) continue;
            JsonElement projectId = value.get("projectId");
            String project = projectId != null && projectId.isJsonPrimitive() && projectId.getAsJsonPrimitive().isString()
                ? projectId.getAsString() : null;
            result.put(entry.getKey(), new Request(enabled.getAsBoolean(), project));
        }
        return result;
    }

    private Path keepCorruptCopy() throws IOException {
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        Path target = file.resolveSibling(FILE_NAME + ".corrupt-" + stamp);
        for (int n = 2; Files.exists(target); n++) target = file.resolveSibling(FILE_NAME + ".corrupt-" + stamp + "-" + n);
        Files.move(file, target);
        return target;
    }

    /**
     * Temp file in the same directory, then an atomic replace. The launcher may hold the target open for a moment
     * (Windows then denies the replace), so access denials are retried for about a second.
     */
    static void writeAtomically(Path target, String text) throws IOException {
        Path directory = target.toAbsolutePath().getParent();
        Files.createDirectories(directory);
        Path temporary = Files.createTempFile(directory, "." + target.getFileName() + "-", ".tmp");
        try {
            // Flushed to disk before the rename, so a power loss never leaves an empty file behind the new name.
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                ByteBuffer bytes = ByteBuffer.wrap(text.getBytes(StandardCharsets.UTF_8));
                while (bytes.hasRemaining()) channel.write(bytes);
                channel.force(true);
            }
            for (int attempt = 0; ; attempt++) {
                try {
                    try {
                        Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                    } catch (AtomicMoveNotSupportedException unsupported) {
                        Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
                    }
                    return;
                } catch (AccessDeniedException busy) {
                    if (attempt >= 20) throw busy;
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw new InterruptedIOException("Interrupted while replacing " + target);
                    }
                }
            }
        } catch (IOException | RuntimeException failure) {
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException cleanup) {
                failure.addSuppressed(cleanup);
            }
            throw failure;
        }
    }
}
