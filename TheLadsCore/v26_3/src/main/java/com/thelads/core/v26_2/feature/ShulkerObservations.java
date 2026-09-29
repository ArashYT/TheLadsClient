package com.thelads.core.v26_2.feature;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.LoggerFactory;

/** Last-observed first items, never an assertion that a remote closed container is currently unchanged. */
final class ShulkerObservations {
    static final int MAX_ENTRIES = 1024, MAX_BYTES = 2 * 1024 * 1024;
    static final long MAX_AGE_MILLIS = TimeUnit.DAYS.toMillis(7);
    private static final ThreadPoolExecutor IO = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
        new ArrayBlockingQueue<>(16), task -> { var thread = new Thread(task, "Lads shulker cache"); thread.setDaemon(true); return thread; },
        new ThreadPoolExecutor.DiscardPolicy());
    private static final LinkedHashMap<Long, Observation> ENTRIES = new LinkedHashMap<>();
    private static Scope scope;
    private static boolean dirty;
    private static final java.util.Set<Long> TOUCHED_DURING_LOAD = new java.util.HashSet<>();
    private static boolean loading, loadOverflow;
    private static long lastFlush;

    static {
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.CLIENT_STOPPING.register(minecraft -> {
            flush();
            IO.shutdown();
            try { IO.awaitTermination(1500, TimeUnit.MILLISECONDS); }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
        });
    }

    record Observation(ItemStack first, boolean uniform, String block, long seen) {}
    private record Scope(Path file, String key, RegistryOps<com.google.gson.JsonElement> ops) {}
    private ShulkerObservations() {}

    private static boolean persist() {
        return NativeQualityOfLife.bool("ShulkerBoxUtils", "Remember Observed Contents", true)
            && NativeQualityOfLife.bool("ShulkerBoxUtils", "Persist Observed Contents", true);
    }

    static String scopeKey(String world, String dimension, String player) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(
                (world + "\n" + dimension + "\n" + player).getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    static void switchLevel(ClientLevel level) {
        flush();
        scope = null;
        ENTRIES.clear(); dirty = false;
        TOUCHED_DURING_LOAD.clear(); loading = loadOverflow = false;
        if (level == null) return;
        Minecraft minecraft = Minecraft.getInstance();
        var server = minecraft.getSingleplayerServer();
        var remote = minecraft.getCurrentServer();
        String world = server != null ? "local:" + server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize()
            : remote != null ? "server:" + remote.ip.toLowerCase(java.util.Locale.ROOT) : null;
        if (world == null) return;
        String key = scopeKey(world, level.dimension().identifier().toString(), minecraft.getUser().getProfileId().toString());
        Scope request = new Scope(FabricLoader.getInstance().getConfigDir().resolve("theladscore/shulker-observations").resolve(key + ".json"),
            key, RegistryOps.create(JsonOps.INSTANCE, level.registryAccess()));
        scope = request;
        if (!persist()) return;
        loading = true;
        IO.execute(() -> {
            Map<Long, Observation> loaded = read(request.file, request.key, request.ops, System.currentTimeMillis());
            minecraft.execute(() -> {
                if (scope != request || !persist()) return;
                for (var entry : (loadOverflow ? Map.<Long, Observation>of() : loaded).entrySet()) {
                    if (ENTRIES.size() >= MAX_ENTRIES) break;
                    if (!TOUCHED_DURING_LOAD.contains(entry.getKey())) ENTRIES.putIfAbsent(entry.getKey(), entry.getValue());
                }
                loading = false; TOUCHED_DURING_LOAD.clear();
            });
        });
    }

    static Observation get(ShulkerBoxBlockEntity box) {
        if (!persist()) return null;
        Observation saved = ENTRIES.get(box.getBlockPos().asLong());
        if (saved == null) return null;
        if (!saved.block.equals(BuiltInRegistries.BLOCK.getKey(box.getBlockState().getBlock()).toString())
            || !fresh(saved.seen, System.currentTimeMillis())) {
            invalidate(box.getBlockPos());
            return null;
        }
        return saved;
    }

    static boolean fresh(long seen, long now) { return seen > 0 && seen <= now && now - seen <= MAX_AGE_MILLIS; }

    static void observed(ShulkerBoxBlockEntity box, ItemStack first, boolean uniform) {
        if (scope == null) return;
        long key = box.getBlockPos().asLong();
        touch(key);
        if (first.isEmpty()) { invalidate(box.getBlockPos()); return; }
        Observation before = ENTRIES.get(key);
        long now = System.currentTimeMillis();
        String block = BuiltInRegistries.BLOCK.getKey(box.getBlockState().getBlock()).toString();
        // Refresh history at most once per minute when the real contents did not change.
        if (before != null && before.uniform == uniform && before.block.equals(block)
            && ItemStack.matches(before.first, first) && now - before.seen < 60_000) return;
        ENTRIES.remove(key);
        while (ENTRIES.size() >= MAX_ENTRIES) ENTRIES.remove(ENTRIES.keySet().iterator().next());
        ENTRIES.put(key, new Observation(first.copy(), uniform, block, now));
        dirty = true;
    }

    private static void touch(long pos) {
        if (!loading) return;
        if (TOUCHED_DURING_LOAD.size() >= MAX_ENTRIES * 2) loadOverflow = true;
        else TOUCHED_DURING_LOAD.add(pos);
    }
    static void invalidate(BlockPos pos) { touch(pos.asLong()); if (ENTRIES.remove(pos.asLong()) != null) dirty = true; }
    static void tick() { if (System.nanoTime() - lastFlush >= TimeUnit.SECONDS.toNanos(10)) flush(); }

    private static void flush() {
        lastFlush = System.nanoTime();
        if (!dirty || scope == null || !persist()) return;
        Scope request = scope;
        var snapshot = new LinkedHashMap<>(ENTRIES);
        dirty = false;
        IO.execute(() -> write(request.file, request.key, request.ops, snapshot));
    }

    static Map<Long, Observation> read(Path file, String key, RegistryOps<com.google.gson.JsonElement> ops, long now) {
        var result = new LinkedHashMap<Long, Observation>();
        try {
            if (!Files.isRegularFile(file) || Files.size(file) > MAX_BYTES) return result;
            JsonObject root = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            if (root.get("format").getAsInt() != 1 || !key.equals(root.get("scope").getAsString())) return result;
            for (var raw : root.getAsJsonArray("entries")) {
                if (result.size() >= MAX_ENTRIES) break;
                try {
                    JsonObject entry = raw.getAsJsonObject();
                    long seen = entry.get("seen").getAsLong(), pos = entry.get("pos").getAsLong();
                    if (!fresh(seen, now) || entry.get("item").toString().length() > 16_384) continue;
                    var parsed = ItemStack.CODEC.parse(ops, entry.get("item")).result();
                    if (parsed.isPresent() && !parsed.get().isEmpty()) result.put(pos, new Observation(parsed.get(),
                        entry.get("uniform").getAsBoolean(), entry.get("block").getAsString(), seen));
                } catch (RuntimeException invalidEntry) { /* One invalid item must not discard valid observations. */ }
            }
        } catch (IOException | RuntimeException invalidCache) {
            LoggerFactory.getLogger("TheLadsCore").debug("Shulker observation cache unavailable: {}", invalidCache.toString());
        }
        return result;
    }

    static void write(Path file, String key, RegistryOps<com.google.gson.JsonElement> ops, Map<Long, Observation> observations) {
        Path temporary = null;
        try {
            JsonObject root = new JsonObject();
            root.addProperty("format", 1); root.addProperty("scope", key);
            JsonArray entries = new JsonArray();
            int bytes = 100;
            for (var row : observations.entrySet()) {
                if (entries.size() >= MAX_ENTRIES) break;
                Observation value = row.getValue();
                if (!fresh(value.seen, System.currentTimeMillis())) continue;
                var encoded = ItemStack.CODEC.encodeStart(ops, value.first).result();
                if (encoded.isEmpty() || encoded.get().toString().length() > 16_384) continue;
                JsonObject entry = new JsonObject();
                entry.addProperty("pos", row.getKey()); entry.addProperty("seen", value.seen);
                entry.addProperty("uniform", value.uniform); entry.addProperty("block", value.block);
                entry.add("item", encoded.get());
                int length = entry.toString().getBytes(StandardCharsets.UTF_8).length;
                if (bytes + length >= MAX_BYTES - 256) break;
                bytes += length + 1; entries.add(entry);
            }
            root.add("entries", entries);
            Files.createDirectories(file.getParent());
            temporary = Files.createTempFile(file.getParent(), "observed-", ".tmp");
            Files.writeString(temporary, root.toString(), StandardCharsets.UTF_8);
            try { Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (java.nio.file.AtomicMoveNotSupportedException unavailable) { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING); }
        } catch (IOException | RuntimeException unavailable) {
            LoggerFactory.getLogger("TheLadsCore").debug("Could not save shulker observations: {}", unavailable.toString());
        } finally {
            if (temporary != null) try { Files.deleteIfExists(temporary); } catch (IOException ignored) {}
        }
    }
}
