package com.thelads.core.v26_2.feature;

import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import com.thelads.core.shared.SharedContentPaths;
import com.thelads.core.shared.SharedContentQa;
import com.thelads.core.v26_2.mixin.ServerListAccessor;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.pack.PackMetadataSection;
import net.minecraft.util.InclusiveRange;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.LevelSummary;

/**
 * Opt-in QA (-Dthelads.verifySharedContent=true, sandbox root only): worlds, resource packs, shader packs and the server
 * list resolve to the shared folder. Role create adds a disposable pack, shader pack, server and world (the open QA
 * world is needed for its level.dat); role observe finds them from another version without opening that world. Every role
 * then runs the stale-writer check against the real ServerListSharedMixin and restores the list it found.
 */
public final class NativeSharedContentProbe {
    private static boolean done;
    private NativeSharedContentProbe() {}

    static void tick() {
        if (done || !SharedContentQa.requested()) return;
        Minecraft mc = Minecraft.getInstance();
        if (!mc.isGameLoadFinished() || mc.gui.overlay() != null) return;
        if ("create".equals(System.getProperty(SharedContentQa.ROLE)) && (mc.level == null || mc.getSingleplayerServer() == null)) return;
        done = true;
        SharedContentQa.run(qa -> run(mc, qa));
    }

    private static void run(Minecraft mc, SharedContentQa qa) throws Exception {
        qa.requireSandbox(FabricLoader.getInstance().getGameDir());
        WorldFoldersProbe.run(qa);
        Path saves = qa.requireShared("world folder", mc.getLevelSource().getBaseDir(), SharedContentPaths.savesDir());
        Path packs = qa.requireShared("resource pack folder", mc.getResourcePackDirectory(), SharedContentPaths.resourcePacksDir());
        Path shaders = qa.requireShared("shader pack folder", shaderPacks(), SharedContentPaths.shaderPacksDir());
        qa.requireSharedServersFile();
        ServerList servers = new ServerList(mc);
        servers.load();
        qa.info("server list {} loaded {} visible server(s)", SharedContentPaths.serversFile(), servers.size());
        switch (qa.role()) {
            case CREATE -> create(mc, qa, saves, packs, shaders, servers);
            case OBSERVE -> observe(mc, qa, packs, shaders);
            case CHECK -> { }
        }
        staleWriter(mc, qa);
    }

    /**
     * Separate ServerList objects stand in for games open at once. A loads, B adds and saves, then A (stale) adds and saves:
     * both servers and every earlier one must survive. D renames B's server and A saves again unchanged: the rename must
     * survive (A left the entry alone, so it takes D's version). Finally both servers are removed and the list must be
     * exactly what it was before. A failure leaves the two lads-stale-*.invalid entries in the sandbox list.
     */
    private static void staleWriter(Minecraft mc, SharedContentQa qa) {
        int checksBefore = qa.passed();
        String id = (qa.runId().isEmpty() ? "check" : qa.runId()) + "-" + System.currentTimeMillis();
        String nameA = "Lads Stale A " + id, ipA = "lads-stale-a-" + id + ".invalid";
        String nameB = "Lads Stale B " + id, ipB = "lads-stale-b-" + id + ".invalid", renamed = "Lads Stale B renamed " + id;
        List<String> original = entries(loaded(mc));
        qa.info("stale-writer 0: {} entries before: {}", original.size(), original);
        ServerList a = loaded(mc);
        qa.info("stale-writer 1: A loaded {} entries", entries(a).size());
        ServerList b = loaded(mc);
        b.add(new ServerData(nameB, ipB, ServerData.Type.OTHER), false);
        b.save();
        qa.info("stale-writer 2: B loaded, added '{}' ({}) and saved", nameB, ipB);
        a.add(new ServerData(nameA, ipA, ServerData.Type.OTHER), false);
        a.save();
        qa.info("stale-writer 3: stale A added '{}' ({}) and saved", nameA, ipA);
        List<String> merged = entries(loaded(mc));
        qa.info("stale-writer 4: C reloaded {} entries: {}", merged.size(), merged);
        qa.check(merged.contains(nameB + " | " + ipB), "stale-writer: B's server survives A's stale save");
        qa.check(merged.contains(nameA + " | " + ipA), "stale-writer: stale A's own server is saved");
        qa.check(merged.size() == original.size() + 2
                && merged.stream().filter(e -> !e.endsWith(" | " + ipA) && !e.endsWith(" | " + ipB)).toList().equals(original),
            "stale-writer: every earlier server is still there, in order");
        ServerList d = loaded(mc);
        d.get(ipB).name = renamed;
        d.save();
        a.save();
        String reloadedName = loaded(mc).get(ipB).name;
        qa.info("stale-writer 5: D renamed B's server to '{}' and saved; A saved again with its unchanged copy; A now holds '{}', reload has '{}'",
            renamed, a.get(ipB).name, reloadedName);
        qa.check(renamed.equals(a.get(ipB).name), "stale-writer: A took D's rename into its own copy");
        qa.check(renamed.equals(reloadedName), "stale-writer: D's rename survives A's second stale save");
        ServerList cleanup = loaded(mc);
        cleanup.remove(cleanup.get(ipA));
        cleanup.remove(cleanup.get(ipB));
        cleanup.save();
        List<String> after = entries(loaded(mc));
        qa.info("stale-writer 6: removed both and saved; reload has {} entries: {}", after.size(), after);
        qa.check(after.equals(original), "stale-writer: the list equals the one before step 1 (names, addresses, order)");
        qa.info("stale-writer PASS: {} checks", qa.passed() - checksBefore);
    }

    private static ServerList loaded(Minecraft mc) {
        ServerList list = new ServerList(mc);
        list.load();
        return list;
    }

    /** "name | address" of each visible server in order, then of each hidden one. */
    private static List<String> entries(ServerList list) {
        List<String> all = new ArrayList<>();
        for (ServerData data : ((ServerListAccessor) list).ladsServers()) all.add(data.name + " | " + data.ip);
        for (ServerData data : ((ServerListAccessor) list).ladsHiddenServers()) all.add("hidden " + data.name + " | " + data.ip);
        return all;
    }

    private static void create(Minecraft mc, SharedContentQa qa, Path saves, Path packs, Path shaders, ServerList servers) throws Exception {
        var metadata = PackMetadataSection.forPackType(PackType.CLIENT_RESOURCES);
        var format = SharedConstants.getCurrentVersion().packVersion(PackType.CLIENT_RESOURCES);
        JsonObject mcmeta = new JsonObject();
        mcmeta.add(metadata.name(), metadata.codec().encodeStart(JsonOps.INSTANCE,
            new PackMetadataSection(Component.literal("Lads shared content QA " + qa.runId()), new InclusiveRange<>(format))).getOrThrow());
        qa.check(metadata.codec().parse(JsonOps.INSTANCE, mcmeta.get(metadata.name())).result().isPresent(), "pack.mcmeta is valid for this version");
        Path pack = packs.resolve(qa.packFileName());
        writeZip(pack, "pack.mcmeta", mcmeta.toString());
        Path shader = shaders.resolve(qa.packFileName());
        writeZip(shader, "shaders/lads-shared-qa.txt", "Placeholder for Lads shared-content QA; not a working shader pack.");

        servers.add(new ServerData(qa.serverName(), qa.serverIp(), ServerData.Type.OTHER), false);
        servers.save();
        ServerList reread = new ServerList(mc);
        reread.load();
        qa.check(contains(reread, qa.serverIp()), "saved server is read back through ServerList");
        CompoundTag shared = NbtIo.read(SharedContentPaths.serversFile());
        qa.check(shared != null && shared.getListOrEmpty("servers").compoundStream().anyMatch(entry -> qa.serverIp().equals(entry.getStringOr("ip", ""))),
            "saved server is in the shared servers.dat");

        LevelStorageSource levels = mc.getLevelSource();
        Path levelDat = mc.getSingleplayerServer().getWorldPath(LevelResource.LEVEL_DATA_FILE);
        qa.check(levels.isNewLevelIdAcceptable(qa.worldName()) && !levels.levelExists(qa.worldName()), "disposable world name is unused");
        try (LevelStorageSource.LevelStorageAccess access = levels.createAccess(qa.worldName())) {
            Files.copy(levelDat, access.getLevelPath(LevelResource.LEVEL_DATA_FILE));
        }
        Path world = saves.resolve(qa.worldName());
        qa.check(levels.levelExists(qa.worldName()) && Files.isRegularFile(world.resolve("level.dat")), "disposable world is in the shared saves");
        qa.info("created resource pack {} (zip with pack.mcmeta {}), shader pack placeholder {} (zip with shaders/lads-shared-qa.txt), "
            + "server '{}' ({}) via ServerList.add+save, world folder {} via LevelStorageSource.createAccess holding only level.dat copied "
            + "from the open QA world {} (no region data; never opened)", pack, mcmeta, shader, qa.serverName(), qa.serverIp(), world, levelDat);
    }

    private static void observe(Minecraft mc, SharedContentQa qa, Path packs, Path shaders) throws Exception {
        qa.check(Files.isRegularFile(packs.resolve(qa.packFileName())), "resource pack from the other version is visible");
        qa.check(Files.isRegularFile(shaders.resolve(qa.packFileName())), "shader pack from the other version is visible");
        ServerList servers = new ServerList(mc);
        servers.load();
        qa.check(contains(servers, qa.serverIp()), "server from the other version is in this server list");
        LevelStorageSource levels = mc.getLevelSource();
        LevelStorageSource.LevelCandidates candidates = levels.findLevelCandidates();
        qa.check(candidates.levels().stream().anyMatch(level -> level.directoryName().equals(qa.worldName())), "world folder is a level candidate");
        LevelSummary summary = levels.loadLevelSummaries(candidates).get(30, TimeUnit.SECONDS).stream()
            .filter(level -> level.getLevelId().equals(qa.worldName())).findFirst().orElse(null);
        qa.check(summary != null, "world from the other version is in this world list");
        qa.info("listed world '{}' without opening it: downgrade {}, locked {}", qa.worldName(), summary.isDowngrade(), summary.isLocked());
    }

    private static boolean contains(ServerList servers, String ip) {
        for (int i = 0; i < servers.size(); i++) if (ip.equals(servers.get(i).ip)) return true;
        return false;
    }

    private static Path shaderPacks() throws ReflectiveOperationException {
        if (!FabricLoader.getInstance().isModLoaded("iris")) return FabricLoader.getInstance().getGameDir().resolve("shaderpacks");
        return (Path) Class.forName("net.irisshaders.iris.Iris").getMethod("getShaderpacksDirectory").invoke(null);
    }

    private static void writeZip(Path file, String entry, String content) throws IOException {
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(file, StandardOpenOption.CREATE_NEW))) {
            zip.putNextEntry(new ZipEntry(entry));
            zip.write(content.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
    }
}
