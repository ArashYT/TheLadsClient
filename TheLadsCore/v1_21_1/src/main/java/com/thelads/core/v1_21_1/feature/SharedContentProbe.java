package com.thelads.core.v1_21_1.feature;

import com.thelads.core.shared.SharedContentPaths;
import com.thelads.core.shared.SharedContentQa;
import com.thelads.core.v1_21_1.mixin.ServerListAccessor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;
import net.minecraft.world.level.storage.LevelStorageSource;

/**
 * Opt-in QA (-Dthelads.verifySharedContent=true, sandbox root only), run once from the title screen: worlds, resource
 * packs, shader packs and servers.dat resolve to the shared folder. Role observe also finds the disposable pack, shader
 * pack, server and world a 26.x run created, without opening the world. This version never creates them. Every role
 * also runs the stale-writer check against the real ServerListSharedMixin and restores the list it found.
 */
public final class SharedContentProbe {
    private static boolean done;
    private SharedContentProbe() {}

    public static void runOnce() {
        if (done || !SharedContentQa.requested()) return;
        done = true;
        SharedContentQa.run(SharedContentProbe::run);
    }

    private static void run(SharedContentQa qa) throws Exception {
        Minecraft mc = Minecraft.getInstance();
        qa.check(qa.role() != SharedContentQa.Role.CREATE, "role create runs on 26.x; this version only checks and observes");
        qa.requireSandbox(FabricLoader.getInstance().getGameDir());
        qa.requireShared("world folder", mc.getLevelSource().getBaseDir(), SharedContentPaths.savesDir());
        Path packs = qa.requireShared("resource pack folder", mc.getResourcePackDirectory(), SharedContentPaths.resourcePacksDir());
        Path shaders = qa.requireShared("shader pack folder", shaderPacks(), SharedContentPaths.shaderPacksDir());
        qa.requireSharedServersFile();
        // Loading here also applies the ServerList mixins (the redirect requires both of its targets) on a title-only run.
        ServerList servers = new ServerList(mc);
        servers.load();
        qa.info("server list {} loaded {} visible server(s)", SharedContentPaths.serversFile(), servers.size());
        staleWriter(mc, qa);
        if (qa.role() != SharedContentQa.Role.OBSERVE) return;
        qa.check(Files.isRegularFile(packs.resolve(qa.packFileName())), "resource pack from the other version is visible");
        qa.check(Files.isRegularFile(shaders.resolve(qa.packFileName())), "shader pack from the other version is visible");
        qa.check(contains(servers, qa.serverIp()), "server from the other version is in this server list");
        LevelStorageSource levels = mc.getLevelSource();
        LevelStorageSource.LevelCandidates candidates = levels.findLevelCandidates();
        qa.check(candidates.levels().stream().anyMatch(level -> level.directoryName().equals(qa.worldName())), "world folder is a level candidate");
        // An older version may not summarise a newer world; the folder listing above is the requirement.
        boolean listed = levels.loadLevelSummaries(candidates).get(30, TimeUnit.SECONDS).stream()
            .anyMatch(level -> level.getLevelId().equals(qa.worldName()));
        qa.info("world '{}' in this version's world list: {} (not opened)", qa.worldName(), listed);
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

    private static boolean contains(ServerList servers, String ip) {
        for (int i = 0; i < servers.size(); i++) if (ip.equals(servers.get(i).ip)) return true;
        return false;
    }

    private static Path shaderPacks() throws ReflectiveOperationException {
        if (!FabricLoader.getInstance().isModLoaded("iris")) return FabricLoader.getInstance().getGameDir().resolve("shaderpacks");
        return (Path) Class.forName("net.irisshaders.iris.Iris").getMethod("getShaderpacksDirectory").invoke(null);
    }
}
