package com.thelads.core.v1_8_9.feature.quickpack;

import com.google.common.collect.MapMaker;
import com.thelads.core.perf.ZipPackIndex;
import java.util.Map;
import java.util.zip.ZipFile;
import net.minecraft.client.resources.FileResourcePack;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * 1.8.9 QuickPack: caches the zip index of each active FileResourcePack to avoid
 * re-scanning thousands of entries during resource reload and resource lookups.
 */
public final class QuickPack189 {
    private static final Logger LOGGER = LogManager.getLogger("TheLadsCore-1.8.9");
    private static final Map<FileResourcePack, ZipPackIndex> INDEX_MAP =
        new MapMaker().weakKeys().makeMap();

    private QuickPack189() {}

    public static ZipPackIndex getOrBuild(FileResourcePack pack, ZipFile zip) {
        if (pack == null || zip == null) return null;
        ZipPackIndex index = INDEX_MAP.get(pack);
        if (index == null) {
            long start = System.nanoTime();
            index = ZipPackIndex.build(zip);
            INDEX_MAP.put(pack, index);
            long tookUs = (System.nanoTime() - start) / 1000L;
            LOGGER.debug("QuickPack 1.8.9: indexed {} files in {} ({} us)", index.entryCount(), zip.getName(), tookUs);
        }
        return index;
    }

    public static Boolean hasEntry(FileResourcePack pack, String name) {
        if (pack == null || name == null) return null;
        ZipPackIndex index = INDEX_MAP.get(pack);
        if (index == null) return null;
        return index.hasEntry(name);
    }

    public static void onClose(FileResourcePack pack) {
        if (pack != null) {
            INDEX_MAP.remove(pack);
        }
    }
}
