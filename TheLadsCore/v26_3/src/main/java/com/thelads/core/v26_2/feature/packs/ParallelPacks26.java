package com.thelads.core.v26_2.feature.packs;

import com.google.common.hash.HashCode;
import com.thelads.core.perf.ParallelPacks;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import net.minecraft.server.packs.DownloadQueue;
import net.minecraft.util.HttpUtil;
import org.slf4j.LoggerFactory;

/**
 * Parallel server resource pack downloads on 26.3 (Lads remake of MultiThreadedFileDownload): one batch of packs is downloaded up to
 * four at a time instead of one after another. Each pack is run through vanilla's own single-entry download (so hash check, cache hit,
 * event log and failure reporting are vanilla's), and the results are merged in the batch's order. Batches still run one after
 * another on vanilla's queue thread, so one pack id is never downloaded by two batches at once.
 */
public final class ParallelPacks26 {
    private ParallelPacks26() {}

    private record Item(UUID id, DownloadQueue.DownloadRequest request) {}

    /** Replaces DownloadQueue.runDownload for a batch of two or more packs; single is vanilla's runDownload bound to the queue. */
    public static DownloadQueue.BatchResult run(DownloadQueue.BatchConfig config, Map<UUID, DownloadQueue.DownloadRequest> requests, Path cacheDir,
                                                BiFunction<DownloadQueue.BatchConfig, Map<UUID, DownloadQueue.DownloadRequest>, DownloadQueue.BatchResult> single) {
        List<Item> items = new ArrayList<>();
        requests.forEach((id, request) -> items.add(new Item(id, request)));
        DownloadQueue.BatchResult[] results = new DownloadQueue.BatchResult[items.size()];
        ConcurrentHashMap<String, Path> firstFile = new ConcurrentHashMap<>();
        ParallelPacks.Aggregate progress = new ParallelPacks.Aggregate(adapt(config.listener()), items.size());
        LoggerFactory.getLogger("TheLadsCore").info("[Lads] parallel pack download: {} packs, {} at once", items.size(),
            ParallelPacks.concurrency(items, ParallelPacks26::key));
        ParallelPacks.run(items, ParallelPacks26::key, ParallelPacks.MAX_AT_ONCE, item -> {
            ParallelPacks.Aggregate.PackProgress mine = progress.forPack();
            try {
                String key = key(item);
                Path reuse = key == null ? null : firstFile.get(key);
                if (reuse != null) seed(cacheDir, item, reuse);
                DownloadQueue.BatchConfig own = new DownloadQueue.BatchConfig(config.hashFunction(), config.maxSize(), config.headers(), config.proxy(), adapt(mine));
                DownloadQueue.BatchResult result = single.apply(own, Map.of(item.id, item.request));
                results[items.indexOf(item)] = result; // each slot is written by exactly one task; read after run() joined them
                Path file = result.downloaded().get(item.id);
                if (key != null && file != null) firstFile.putIfAbsent(key, file);
            } finally {
                mine.done();
            }
        });
        DownloadQueue.BatchResult merged = new DownloadQueue.BatchResult();
        for (DownloadQueue.BatchResult r : results) {
            if (r == null) continue;
            merged.downloaded().putAll(r.downloaded());
            merged.failed().addAll(r.failed());
        }
        return merged;
    }

    /** Same URL and hash: one download, the rest reuse its file. Without a hash nothing is shared. */
    private static String key(Item item) {
        HashCode hash = item.request.hash();
        return hash == null ? null : item.request.url() + "|" + hash;
    }

    /** Puts the already downloaded file where vanilla's cache check looks for this id's file (cacheDir/id/hash); that check verifies the hash. */
    private static void seed(Path cacheDir, Item item, Path from) {
        try {
            Path target = cacheDir.resolve(item.id.toString()).resolve(item.request.hash().toString());
            if (Files.exists(target)) return;
            Files.createDirectories(target.getParent());
            Files.copy(from, target);
        } catch (Exception ignored) {
            // vanilla then downloads it itself
        }
    }

    private static ParallelPacks.Progress adapt(HttpUtil.DownloadProgressListener l) {
        return new ParallelPacks.Progress() {
            @Override public void requestStart() { l.requestStart(); }
            @Override public void downloadStart(long size) { l.downloadStart(size < 0 ? OptionalLong.empty() : OptionalLong.of(size)); }
            @Override public void downloadedBytes(long bytes) { l.downloadedBytes(bytes); }
            @Override public void requestFinished(boolean success) { l.requestFinished(success); }
        };
    }

    private static HttpUtil.DownloadProgressListener adapt(ParallelPacks.Progress p) {
        return new HttpUtil.DownloadProgressListener() {
            @Override public void requestStart() { p.requestStart(); }
            @Override public void downloadStart(OptionalLong size) { p.downloadStart(size.isPresent() ? size.getAsLong() : -1); }
            @Override public void downloadedBytes(long bytes) { p.downloadedBytes(bytes); }
            @Override public void requestFinished(boolean success) { p.requestFinished(success); }
        };
    }
}
