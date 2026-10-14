package com.thelads.core.client.pack;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Inspects and caches which asset domains a resource pack touches, and tracks
 * the active asset diff mask during a resource-pack switch reload so unaffected
 * heavy subsystems (e.g. SoundEngine, LanguageManager, FontRenderer, Shaders)
 * can be skipped safely.
 */
public final class PackAssetMask {
    public static final int SOUNDS              = 1 << 0;
    public static final int LANG                = 1 << 1;
    public static final int FONTS               = 1 << 2;
    public static final int MODELS_BLOCKSTATES  = 1 << 3;
    public static final int SHADERS             = 1 << 4;
    public static final int CEM_RANDOM_ENTITIES = 1 << 5;
    public static final int ALL                 = SOUNDS | LANG | FONTS | MODELS_BLOCKSTATES | SHADERS | CEM_RANDOM_ENTITIES;

    private static final int MAX_CACHE_ENTRIES = 4096;
    private static final ConcurrentHashMap<String, Integer> MASK_CACHE = new ConcurrentHashMap<>();

    private static volatile int pendingPackSwitchMask = -1;
    private static volatile int pendingNewSelectedMask = 0;
    private static volatile int activeReloadMask = ALL;
    private static volatile int activeNewSelectedMask = ALL;
    private static volatile boolean activeIsPackSwitch = false;

    private PackAssetMask() {}

    /**
     * Marks that a resource-pack screen switch is about to trigger a resource reload
     * with the given union of changed asset domains.
     */
    public static void beginPackSwitchReload(int diffMask) {
        beginPackSwitchReload(diffMask, 0);
    }

    /**
     * Marks that a resource-pack screen switch is about to trigger a resource reload
     * with the given union of changed asset domains and union of newly selected file-pack domains.
     */
    public static void beginPackSwitchReload(int diffMask, int newSelectedMask) {
        int masked = diffMask & ALL;
        int selectedMasked = newSelectedMask & ALL;
        pendingPackSwitchMask = masked;
        pendingNewSelectedMask = selectedMasked;
        activeReloadMask = masked;
        activeNewSelectedMask = selectedMasked;
        activeIsPackSwitch = true;
    }

    /**
     * Called at the start of {@code Minecraft.refreshResources()} (1.8.9) or
     * {@code Minecraft.reloadResourcePacks()} (1.20.1 / 26.3).
     * If a pack-switch diff mask is pending, activates it for this reload;
     * otherwise (e.g. F3+T or startup) forces a full reload of all subsystems.
     */
    public static void onReloadStart() {
        int pending = pendingPackSwitchMask;
        if (pending != -1) {
            int pendingSel = pendingNewSelectedMask;
            pendingPackSwitchMask = -1;
            pendingNewSelectedMask = 0;
            activeReloadMask = pending;
            activeNewSelectedMask = pendingSel;
            activeIsPackSwitch = true;
        } else {
            activeReloadMask = ALL;
            activeNewSelectedMask = ALL;
            activeIsPackSwitch = false;
        }
    }

    /**
     * Called when {@code PackSelectionModel.commit()} returns. If {@code onReloadStart()}
     * was never triggered (e.g. the user clicked Done without changing any packs),
     * clears the pending pack-switch state so a later F3+T performs a full reload.
     */
    public static void clearPendingIfNotStarted() {
        if (pendingPackSwitchMask != -1) {
            endReload();
        }
    }

    /**
     * Resets the reload context after a resource reload completes.
     */
    public static void endReload() {
        pendingPackSwitchMask = -1;
        pendingNewSelectedMask = 0;
        activeReloadMask = ALL;
        activeNewSelectedMask = ALL;
        activeIsPackSwitch = false;
    }

    public static boolean isPackSwitchReload() {
        return activeIsPackSwitch;
    }

    public static int getActiveReloadMask() {
        return activeReloadMask;
    }

    public static boolean anySelectedPackHasSounds() {
        return (activeNewSelectedMask & SOUNDS) != 0;
    }

    public static boolean shouldSkipSounds() {
        return activeIsPackSwitch && (activeReloadMask & SOUNDS) == 0;
    }

    public static boolean shouldSkipLang() {
        return activeIsPackSwitch && (activeReloadMask & LANG) == 0;
    }

    public static boolean shouldSkipFonts() {
        return activeIsPackSwitch && (activeReloadMask & FONTS) == 0;
    }

    public static boolean shouldSkipModelsBlockstates() {
        return activeIsPackSwitch && (activeReloadMask & MODELS_BLOCKSTATES) == 0;
    }

    public static boolean shouldSkipShaders() {
        return activeIsPackSwitch && (activeReloadMask & SHADERS) == 0;
    }

    public static boolean shouldSkipCemRandomEntities() {
        return activeIsPackSwitch && (activeReloadMask & CEM_RANDOM_ENTITIES) == 0;
    }

    /**
     * Computes the union of asset masks for all packs that were added, removed,
     * or changed relative order between {@code oldOrderIds} and {@code newOrderIds}.
     */
    public static int computeDiffMask(
            List<String> oldOrderIds,
            List<String> newOrderIds,
            Function<String, Integer> maskResolver) {
        if (oldOrderIds == null || newOrderIds == null) {
            return ALL;
        }
        Set<String> oldSet = new HashSet<>(oldOrderIds);
        Set<String> newSet = new HashSet<>(newOrderIds);

        int diff = 0;

        for (String id : oldOrderIds) {
            if (!newSet.contains(id)) {
                diff |= maskResolver.apply(id);
                if ((diff & ALL) == ALL) return ALL;
            }
        }
        for (String id : newOrderIds) {
            if (!oldSet.contains(id)) {
                diff |= maskResolver.apply(id);
                if ((diff & ALL) == ALL) return ALL;
            }
        }

        List<String> commonOld = new ArrayList<>(oldOrderIds.size());
        for (String id : oldOrderIds) {
            if (newSet.contains(id)) {
                commonOld.add(id);
            }
        }
        List<String> commonNew = new ArrayList<>(newOrderIds.size());
        for (String id : newOrderIds) {
            if (oldSet.contains(id)) {
                commonNew.add(id);
            }
        }

        int size = Math.min(commonOld.size(), commonNew.size());
        for (int i = 0; i < size; i++) {
            String a = commonOld.get(i);
            String b = commonNew.get(i);
            if (!a.equals(b)) {
                diff |= maskResolver.apply(a);
                diff |= maskResolver.apply(b);
                if ((diff & ALL) == ALL) return ALL;
            }
        }

        return diff & ALL;
    }

    public static int getMask(Path packPath) {
        if (packPath == null) return ALL;
        return getMask(packPath.toFile());
    }

    public static int getMask(File packFile) {
        if (packFile == null || !packFile.exists()) {
            return ALL;
        }
        String cacheKey = buildCacheKey(packFile);
        Integer cached = MASK_CACHE.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        int mask = inspectPack(packFile);
        if (MASK_CACHE.size() >= MAX_CACHE_ENTRIES) {
            MASK_CACHE.clear();
        }
        MASK_CACHE.put(cacheKey, mask);
        return mask;
    }

    private static String buildCacheKey(File file) {
        String abs = file.getAbsolutePath();
        long lastMod = file.lastModified();
        if (file.isFile()) {
            return abs + ":" + lastMod + ":" + file.length();
        }
        File assetsDir = new File(file, "assets");
        long assetsMod = assetsDir.exists() ? assetsDir.lastModified() : 0L;
        return abs + ":dir:" + lastMod + ":" + assetsMod;
    }

    private static int inspectPack(File file) {
        try {
            if (file.isFile()) {
                return inspectZipPack(file);
            }
            if (file.isDirectory()) {
                return inspectDirectoryPack(file);
            }
        } catch (Exception ignored) {
            return ALL;
        }
        return ALL;
    }

    private static int inspectZipPack(File zipFile) throws IOException {
        int mask = 0;
        try (ZipFile zip = new ZipFile(zipFile)) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory()) {
                    continue;
                }
                mask |= classifyEntryPath(entry.getName());
                if ((mask & ALL) == ALL) {
                    return ALL;
                }
            }
        }
        return mask;
    }

    private static int inspectDirectoryPack(File dir) {
        File assetsDir = new File(dir, "assets");
        if (!assetsDir.isDirectory()) {
            return 0;
        }
        File[] namespaces = assetsDir.listFiles(File::isDirectory);
        if (namespaces == null || namespaces.length == 0) {
            return 0;
        }
        int mask = 0;
        for (File ns : namespaces) {
            if ((mask & SOUNDS) == 0) {
                if (new File(ns, "sounds.json").isFile() || hasAnyFile(new File(ns, "sounds"))) {
                    mask |= SOUNDS;
                }
            }
            if ((mask & LANG) == 0) {
                if (hasAnyFile(new File(ns, "lang"))) {
                    mask |= LANG;
                }
            }
            if ((mask & FONTS) == 0) {
                if (hasAnyFile(new File(ns, "font")) || hasAnyFile(new File(new File(ns, "textures"), "font"))) {
                    mask |= FONTS;
                }
            }
            if ((mask & MODELS_BLOCKSTATES) == 0) {
                if (hasAnyFile(new File(ns, "models")) || hasAnyFile(new File(ns, "blockstates"))) {
                    mask |= MODELS_BLOCKSTATES;
                }
            }
            if ((mask & SHADERS) == 0) {
                if (hasAnyFile(new File(ns, "shaders"))) {
                    mask |= SHADERS;
                }
            }
            if ((mask & CEM_RANDOM_ENTITIES) == 0) {
                File optifine = new File(ns, "optifine");
                File emf = new File(ns, "emf");
                if (hasAnyFile(new File(optifine, "cem"))
                        || hasAnyFile(new File(optifine, "random"))
                        || hasAnyFile(new File(optifine, "mob"))
                        || hasAnyFile(new File(emf, "cem"))) {
                    mask |= CEM_RANDOM_ENTITIES;
                }
            }
            if ((mask & ALL) == ALL) {
                return ALL;
            }
        }
        return mask;
    }

    private static boolean hasAnyFile(File dir) {
        if (!dir.isDirectory()) {
            return false;
        }
        File[] children = dir.listFiles();
        if (children == null || children.length == 0) {
            return false;
        }
        for (File child : children) {
            if (child.isFile()) {
                return true;
            }
            if (child.isDirectory() && hasAnyFile(child)) {
                return true;
            }
        }
        return false;
    }

    static int classifyEntryPath(String rawPath) {
        if (rawPath == null || rawPath.length() <= 7) {
            return 0;
        }
        String path = rawPath.indexOf('\\') >= 0 ? rawPath.replace('\\', '/') : rawPath;
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        if (!path.regionMatches(true, 0, "assets/", 0, 7)) {
            return 0;
        }
        int nsEnd = path.indexOf('/', 7);
        if (nsEnd < 0 || nsEnd + 1 >= path.length()) {
            return 0;
        }
        String rel = path.substring(nsEnd + 1).toLowerCase(Locale.ROOT);
        if (rel.equals("sounds.json") || rel.startsWith("sounds/")) {
            return SOUNDS;
        }
        if (rel.startsWith("lang/")) {
            return LANG;
        }
        if (rel.startsWith("font/") || rel.startsWith("textures/font/")) {
            return FONTS;
        }
        if (rel.startsWith("models/") || rel.startsWith("blockstates/")) {
            return MODELS_BLOCKSTATES;
        }
        if (rel.startsWith("shaders/")) {
            return SHADERS;
        }
        if (rel.startsWith("optifine/cem/")
                || rel.startsWith("emf/cem/")
                || rel.startsWith("optifine/random/")
                || rel.startsWith("optifine/mob/")) {
            return CEM_RANDOM_ENTITIES;
        }
        return 0;
    }
}
