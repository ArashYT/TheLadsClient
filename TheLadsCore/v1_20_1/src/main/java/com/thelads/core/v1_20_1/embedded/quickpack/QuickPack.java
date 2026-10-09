// Derived from quick-pack 1.4.0 by Drex (commit b80dac1, MIT); see META-INF/lads-sources/quickpack/LICENSE.
// Modified by The Lads: ported to Minecraft 1.20.1 from the Lads 26.3 embedded copy; see META-INF/lads-sources/quickpack/NOTICE.txt.
package com.thelads.core.v1_20_1.embedded.quickpack;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.FilePackResources;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.IoSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Indexes a pack zip once so resource listing and namespace lookups no longer scan every entry. Vanilla's FilePackResources walks
 * the whole zip for every {@code listResources} (one per namespace and resource directory on every reload) and every
 * {@code getNamespaces}; with the index each of those is a sorted-set range lookup.
 */
public class QuickPack {
    public static final String MOD_ID = "quick-pack";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private static final boolean QA = Boolean.getBoolean("thelads.verifyAutoWorld") || com.thelads.core.v1_20_1.perf.SmallModsProbe1201.enabled("quickpack");

    private static final AtomicInteger INDEXED_PACKS = new AtomicInteger();
    private static final AtomicInteger INDEXED_LISTS = new AtomicInteger();
    private static final AtomicInteger INDEXED_NAMESPACE_LOOKUPS = new AtomicInteger();

    /** What a zip holds, read in one pass: the names of its files (sorted, directories left out) and its namespaces per pack type. */
    public static final class Index {
        private final TreeSet<String> files = new TreeSet<>();
        private final Map<PackType, Set<String>> namespaces = new HashMap<>();

        public int fileCount() {
            return this.files.size();
        }
    }

    /** Counters for the QA probe: how many packs were indexed and how many calls the index answered. */
    public static int indexedPacks() { return INDEXED_PACKS.get(); }
    public static int indexedLists() { return INDEXED_LISTS.get(); }
    public static int indexedNamespaceLookups() { return INDEXED_NAMESPACE_LOOKUPS.get(); }

    public static Index build(ZipFile zip) {
        Index index = new Index();
        for (PackType type : PackType.values()) index.namespaces.put(type, new HashSet<>());
        List<String> ignored = new ArrayList<>();
        Enumeration<? extends ZipEntry> entries = zip.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            String name = entry.getName();
            if (!entry.isDirectory()) index.files.add(name);
            for (PackType type : PackType.values()) {
                if (!name.startsWith(type.getDirectory() + "/")) continue;
                // Same rule as vanilla getNamespaces: the second path segment, ignored unless it is lower case.
                List<String> parts = new ArrayList<>(FilePackResources.SPLITTER.splitToList(name));
                if (parts.size() > 1) {
                    String namespace = parts.get(1);
                    if (namespace.equals(namespace.toLowerCase(Locale.ROOT))) index.namespaces.get(type).add(namespace);
                    else if (!ignored.contains(namespace)) ignored.add(namespace);
                }
            }
        }
        for (String namespace : ignored) LOGGER.warn("Ignored non-lowercase namespace: {} in {}", namespace, zip.getName());
        INDEXED_PACKS.incrementAndGet();
        if (QA) LOGGER.info("quick-pack: indexed {} files of {}", index.files.size(), zip.getName());
        return index;
    }

    public static Set<String> namespaces(Index index, PackType type) {
        INDEXED_NAMESPACE_LOOKUPS.incrementAndGet();
        return new HashSet<>(index.namespaces.getOrDefault(type, Set.of()));
    }

    /** Vanilla's listResources over the index: every file under {@code <type>/<namespace>/<path>/}. */
    public static void list(Index index, ZipFile zip, PackType type, String namespace, String path, PackResources.ResourceOutput output) {
        INDEXED_LISTS.incrementAndGet();
        String root = type.getDirectory() + "/" + namespace + "/";
        String prefix = root + path + "/";
        for (String name : index.files.subSet(prefix, prefix + Character.MAX_VALUE)) {
            String relative = name.substring(root.length());
            ResourceLocation id = ResourceLocation.tryBuild(namespace, relative);
            if (id == null) {
                LOGGER.warn("Invalid path in datapack: {}:{}, ignoring", namespace, relative);
                continue;
            }
            ZipEntry entry = zip.getEntry(name);
            if (entry != null) output.accept(id, IoSupplier.create(zip, entry));
        }
    }
}
