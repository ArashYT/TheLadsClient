package com.thelads.core.shared;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Merge rules for the shared servers.dat when several games (versions) run at once. Each game remembers every server it
 * last read or wrote (address plus a fingerprint of its stored fields and hidden flag); right before it saves, the list on
 * disk is compared with that snapshot. Servers another game added, removed or edited are taken over when this game left
 * them alone; this game's own additions, edits and deletions win.
 */
public final class ServerListMerge {
    private ServerListMerge() {
    }

    /**
     * Disk entries to add to this game's list, this game's entries to drop, and this game's entries to overwrite with the
     * disk version (fields and hidden flag).
     */
    public record Plan<T>(List<T> toAdd, List<T> toRemove, List<Adopt<T>> toAdopt) {
        public boolean isEmpty() {
            return toAdd.isEmpty() && toRemove.isEmpty() && toAdopt.isEmpty();
        }
    }

    /** current is unchanged here since the last load or save; disk is the newer version another game wrote. */
    public record Adopt<T>(T current, T disk) {
    }

    /** Servers are matched by address: trimmed and case-insensitive. */
    public static String key(String ip) {
        return ip == null ? "" : ip.trim().toLowerCase(Locale.ROOT);
    }

    /** Address key to fingerprint; of several entries with one address the first counts. */
    public static <T> Map<String, String> snapshot(Collection<T> entries, Function<T, String> ip, Function<T, String> fingerprint) {
        Map<String, String> snapshot = new LinkedHashMap<>();
        for (T entry : entries) {
            snapshot.putIfAbsent(key(ip.apply(entry)), fingerprint.apply(entry));
        }
        return snapshot;
    }

    /**
     * @param loaded       snapshot of what this game last read from or wrote to disk
     * @param current      this game's entries in memory (visible and hidden)
     * @param disk         the entries on disk right now (visible and hidden)
     * @param diskReadable servers.dat exists and parsed. A missing or unreadable file reads as an empty list, which is no
     *                     evidence that servers were removed elsewhere; a readable empty list is.
     * @param fingerprint  every stored field of an entry plus its hidden flag
     */
    public static <T> Plan<T> plan(Map<String, String> loaded, List<T> current, List<T> disk, boolean diskReadable,
                                   Function<T, String> ip, Function<T, String> fingerprint) {
        if (!diskReadable) {
            return new Plan<>(List.of(), List.of(), List.of());
        }
        Set<String> currentKeys = new HashSet<>();
        for (T entry : current) {
            currentKeys.add(key(ip.apply(entry)));
        }
        Map<String, T> diskByKey = new LinkedHashMap<>();
        for (T entry : disk) {
            diskByKey.putIfAbsent(key(ip.apply(entry)), entry);
        }
        // Added elsewhere: on disk, unknown here and never seen by this game (otherwise this game deleted it).
        List<T> toAdd = disk.stream()
            .filter(entry -> !currentKeys.contains(key(ip.apply(entry))) && !loaded.containsKey(key(ip.apply(entry))))
            .toList();
        List<T> toRemove = new ArrayList<>();
        List<Adopt<T>> toAdopt = new ArrayList<>();
        for (T entry : current) {
            String before = loaded.get(key(ip.apply(entry)));
            // Added here, or edited here since the last load or save (address, fields or hidden flag): this game's version wins,
            // even over a removal elsewhere.
            if (before == null || !before.equals(fingerprint.apply(entry))) {
                continue;
            }
            T onDisk = diskByKey.get(key(ip.apply(entry)));
            if (onDisk == null) {
                toRemove.add(entry);
            } else if (!before.equals(fingerprint.apply(onDisk))) {
                toAdopt.add(new Adopt<>(entry, onDisk));
            }
        }
        return new Plan<>(toAdd, List.copyOf(toRemove), List.copyOf(toAdopt));
    }

    /** The snapshot after a save is what a re-read finds on disk; when that re-read fails the previous one is kept. */
    public static Map<String, String> snapshotAfterSave(Map<String, String> previous, Map<String, String> disk, boolean diskReadable) {
        return diskReadable ? disk : previous;
    }
}
