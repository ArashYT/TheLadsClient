// Derived from quick-pack 1.4.0 by Drex (commit b80dac1, MIT); see META-INF/lads-sources/quickpack/LICENSE.
// Modified by The Lads: new entry point for Lads Core on Minecraft 1.20.1.
package com.thelads.core.v1_20_1.embedded.quickpack;

import com.thelads.core.v1_20_1.embedded.quickpack.config.ConfigManager;
import com.thelads.core.v1_20_1.perf.SmallModsProbe1201;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.packs.FilePackResources;
import net.minecraft.server.packs.PackType;

/** Entry point called by EmbeddedMods1201 while the original quick-pack is not installed. */
public final class QuickPackInit1201 {
    private QuickPackInit1201() {}

    public static void init() {
        ConfigManager.load(FabricLoader.getInstance().getConfigDir());
        SmallModsProbe1201.runOnce("quickpack", false, client -> {
            try {
                probe();
            } catch (IOException failure) {
                SmallModsProbe1201.result("quickpack", false, "threw " + failure);
            }
        });
    }

    /**
     * Builds a small resource pack zip and asks the real FilePackResources (the class the mixin changes) for its namespaces and for
     * listings, comparing every answer with what a scan of the zip gives (vanilla's own rule). Also reads one listed file back.
     */
    private static void probe() throws IOException {
        Path dir = Files.createTempDirectory("quickpack-probe");
        Path zip = dir.resolve("pack.zip");
        String[] files = {
            "pack.mcmeta", "assets/minecraft/textures/a.png", "assets/minecraft/textures/block/b.png", "assets/minecraft/textures2/other.png",
            "assets/minecraft/lang/en_us.json", "assets/probe_ns/textures/c.png", "assets/Upper/textures/ignored.png", "data/probe_data/recipes/r.json"
        };
        try (OutputStream out = Files.newOutputStream(zip); ZipOutputStream zos = new ZipOutputStream(out)) {
            for (String file : files) {
                zos.putNextEntry(new ZipEntry(file));
                zos.write(("content of " + file).getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }
            zos.putNextEntry(new ZipEntry("assets/emptyns/")); // a directory entry alone still makes a namespace in vanilla
            zos.closeEntry();
        }
        int packsBefore = QuickPack.indexedPacks(), listsBefore = QuickPack.indexedLists(), namespacesBefore = QuickPack.indexedNamespaceLookups();
        boolean ok;
        String details;
        try (FilePackResources pack = new FilePackResources("quickpack-probe", zip.toFile(), false)) {
            Set<String> assets = pack.getNamespaces(PackType.CLIENT_RESOURCES);
            Set<String> data = pack.getNamespaces(PackType.SERVER_DATA);
            List<String> textures = new ArrayList<>(), nested = new ArrayList<>(), none = new ArrayList<>();
            String[] content = {null};
            pack.listResources(PackType.CLIENT_RESOURCES, "minecraft", "textures", (id, supplier) -> {
                textures.add(id.toString());
                if (id.getPath().equals("textures/a.png")) {
                    try (InputStream in = supplier.get()) {
                        content[0] = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                    } catch (IOException failure) {
                        content[0] = failure.toString();
                    }
                }
            });
            pack.listResources(PackType.CLIENT_RESOURCES, "minecraft", "textures/block", (id, supplier) -> nested.add(id.toString()));
            pack.listResources(PackType.CLIENT_RESOURCES, "nothing_here", "textures", (id, supplier) -> none.add(id.toString()));
            textures.sort(null);
            boolean namespacesOk = new TreeSet<>(assets).equals(new TreeSet<>(Set.of("minecraft", "probe_ns", "emptyns"))) && data.equals(Set.of("probe_data"));
            boolean listOk = textures.equals(List.of("minecraft:textures/a.png", "minecraft:textures/block/b.png")) && nested.equals(List.of("minecraft:textures/block/b.png")) && none.isEmpty();
            boolean contentOk = "content of assets/minecraft/textures/a.png".equals(content[0]);
            boolean usedIndex = QuickPack.indexedPacks() == packsBefore + 1 && QuickPack.indexedLists() == listsBefore + 3 && QuickPack.indexedNamespaceLookups() == namespacesBefore + 2;
            ok = namespacesOk && listOk && contentOk && usedIndex;
            details = "assetNamespaces=" + new TreeSet<>(assets) + " dataNamespaces=" + data + " textures=" + textures + " block=" + nested + " readBack=" + contentOk
                + " indexedPacks=" + (QuickPack.indexedPacks() - packsBefore) + " indexedLists=" + (QuickPack.indexedLists() - listsBefore)
                + " indexedNamespaceLookups=" + (QuickPack.indexedNamespaceLookups() - namespacesBefore);
        } finally {
            Files.deleteIfExists(zip);
            Files.deleteIfExists(dir);
        }
        SmallModsProbe1201.result("quickpack", ok, details + " removeLoadingOverlayFadeOut=" + ConfigManager.config.removeLoadingOverlayFadeOut);
    }
}
