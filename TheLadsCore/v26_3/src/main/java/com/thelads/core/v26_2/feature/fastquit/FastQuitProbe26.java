package com.thelads.core.v26_2.feature.fastquit;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.thelads.core.client.FastQuit;
import com.thelads.core.client.FastQuitRegionCheck;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.Difficulty;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.LevelSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * QA only: -Dthelads.verifyFastQuit=run|verify|baseline (or LADS_VERIFY_FASTQUIT), in a game folder under artifacts\verification. FastQuit's scenarios
 * from the title screen through the real pause-menu button, with blocks, a chest and inventory written before every quit.
 * run: quit then rejoin the same world, quit then open another world, quit then connect to a server, quit then delete, edit
 * and list a world, and last quit then close the game at once (the marks of that last session are checked by verify, in the
 * next game). verify: reopens the worlds, checks the last marks and reads every chunk of every region file.
 * baseline (with -Dlads.fastquit=false): vanilla's quit times.
 */
public final class FastQuitProbe26 {
    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore");
    private static final String PHASE = System.getProperty("thelads.verifyFastQuit", System.getenv("LADS_VERIFY_FASTQUIT"));
    /** The saves folder is shared by every version (Lads shared content): each version keeps its own QA worlds. */
    private static final String A = "FastQuit QA A 26.3", B = "FastQuit QA B 26.3", C = "FastQuit QA C 26.3";
    private static final Block[] MARKS = {Blocks.GOLD_BLOCK, Blocks.EMERALD_BLOCK, Blocks.DIAMOND_BLOCK, Blocks.LAPIS_BLOCK};
    /** Mark offsets from the player: nearby chunks, and far ones in other region files. */
    private static final int[][] OFFSETS = {{0, 0}, {40, 0}, {-40, 0}, {0, 40}, {0, -40}, {-600, 0}, {600, 600}};
    private static final List<String> failures = new ArrayList<>();
    private static Path folder;

    private FastQuitProbe26() {}

    public static void initialize() {
        if (PHASE == null) return;
        Path game = net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir().toAbsolutePath().normalize();
        if (!game.toString().replace('\\', '/').contains("/artifacts/verification/")) {
            LOGGER.error("Lads fast quit probe FAILED: refusing to run outside artifacts/verification ({})", game);
            return;
        }
        folder = game.resolve("lads-qa").resolve("fastquit");
        Thread thread = new Thread(FastQuitProbe26::run, "Lads FastQuit probe");
        thread.setDaemon(true);
        thread.start();
    }

    private static void run() {
        try {
            Files.createDirectories(folder);
            waitFor("the game", 180, () -> Minecraft.getInstance() != null);
            Minecraft mc = Minecraft.getInstance();
            waitFor("the title screen", 180, () -> mc.level == null && mc.isGameLoadFinished() && mc.gui.overlay() == null
                && mc.gui.screen() != null && mc.gui.screen().getClass().getSimpleName().contains("TitleScreen"));
            Thread.sleep(8000); // the title screen's own QA reports first
            LOGGER.info("Lads fast quit probe BEGIN: phase {}, FastQuit {}", PHASE, FastQuit.enabled() ? "on" : "off");
            switch (PHASE) {
                case "run" -> scenarios(mc);
                case "verify" -> verify(mc);
                case "baseline" -> baseline(mc);
                case "crash" -> crash(mc);
                default -> throw new IllegalArgumentException("phase " + PHASE);
            }
        } catch (Throwable failure) {
            LOGGER.error("Lads fast quit probe FAILED: {}", failure.toString(), failure);
        }
    }

    // ---- phases ----

    private static void scenarios(Minecraft mc) throws Exception {
        int k = readExpect().map(e -> e.get("k").getAsInt()).orElse(0) + 1;
        // S1: quit, then rejoin the same world at once.
        enter(mc, A);
        JsonObject marks = mark(mc, k);
        long[] s1 = onClient(mc, () -> {
            long quit = quitToTitle(mc);
            boolean pending = FastQuit.anySaving();
            long start = System.nanoTime();
            mc.createWorldOpenFlows().openWorld(A, () -> fail("S1: reopening A was cancelled"));
            return new long[]{quit, pending ? 1 : 0, ms(start), FastQuit.anySaving() ? 1 : 0};
        });
        waitInWorld(mc, A);
        String s1Check = check(mc, marks);
        report("S1 quit then rejoin the same world", s1, s1Check);

        // S2: quit, then open another world at once.
        JsonObject marksA2 = mark(mc, k + 1);
        long[] s2 = onClient(mc, () -> {
            long quit = quitToTitle(mc);
            boolean pending = FastQuit.anySaving();
            long start = System.nanoTime();
            if (Files.isDirectory(saves(mc).resolve(B))) mc.createWorldOpenFlows().openWorld(B, () -> fail("S2: opening B was cancelled"));
            else create(mc, B);
            return new long[]{quit, pending ? 1 : 0, ms(start), FastQuit.anySaving() ? 1 : 0};
        });
        waitInWorld(mc, B);
        JsonObject marksB = mark(mc, k);
        report("S2 quit then open another world", s2, "B opened");

        // S3: quit, then connect to a server at once (a closed local port: the connection fails after the wait).
        long[] s3 = onClient(mc, () -> {
            long quit = quitToTitle(mc);
            boolean pending = FastQuit.anySaving();
            long start = System.nanoTime();
            ConnectScreen.startConnecting(new TitleScreen(), mc, ServerAddress.parseString("127.0.0.1:1"),
                new ServerData("FastQuit QA", "127.0.0.1:1", ServerData.Type.OTHER), false, null);
            return new long[]{quit, pending ? 1 : 0, ms(start), FastQuit.anySaving() ? 1 : 0};
        });
        report("S3 quit then connect to a server", s3, "connection attempt started after the save");
        Thread.sleep(3000);
        onClient(mc, () -> { mc.setScreenAndShow(new TitleScreen()); return null; });

        // S4: quit, then delete that world at once (the world list's delete: createAccess + deleteLevel).
        if (Files.isDirectory(saves(mc).resolve(C))) enter(mc, C); else { onClient(mc, () -> { create(mc, C); return null; }); waitInWorld(mc, C); }
        mark(mc, k);
        long[] s4 = onClient(mc, () -> {
            long quit = quitToTitle(mc);
            boolean pending = FastQuit.anySaving();
            long start = System.nanoTime();
            try (LevelStorageSource.LevelStorageAccess access = mc.getLevelSource().createAccess(C)) { access.deleteLevel(); }
            return new long[]{quit, pending ? 1 : 0, ms(start), FastQuit.anySaving() ? 1 : 0};
        });
        report("S4 quit then delete the world", s4, Files.exists(saves(mc).resolve(C)) ? fail("S4: C still exists after delete") : "folder deleted");

        // S5 + S6: quit A, the world list at once (A listed, "Saving..." while it saves), then edit it (rename).
        enter(mc, A);
        String s1Again = check(mc, marksA2);
        long[] s5 = new long[4];
        String list = onClient(mc, () -> {
            long quit = quitToTitle(mc);
            boolean pending = FastQuit.anySaving();
            LevelStorageSource source = mc.getLevelSource();
            List<LevelSummary> summaries = source.loadLevelSummaries(source.findLevelCandidates()).join();
            LevelSummary a = summaries.stream().filter(s -> s.getLevelId().equals(A)).findFirst().orElse(null);
            String info = a == null ? null : a.getInfo().getString();
            boolean savingNow = FastQuit.anySaving();
            long start = System.nanoTime();
            try (LevelStorageSource.LevelStorageAccess access = source.validateAndCreateAccess(A)) { access.renameLevel(A); }
            s5[0] = quit; s5[1] = pending ? 1 : 0; s5[2] = ms(start); s5[3] = FastQuit.anySaving() ? 1 : 0;
            if (a == null) return fail("S6: A is missing from the world list during its background save");
            return "A listed" + (savingNow ? ", info '" + info + "'" + (info.equals("Saving...") ? "" : " " + fail("S6: info is not 'Saving...'")) : " (save already done)");
        });
        report("S6 world list during the save", new long[]{s5[0], s5[1], 0, 0}, list + "; A's marks of S2: " + s1Again);
        report("S5 quit then edit (rename) the world", s5, "renamed");

        // S7: quit, then close the game at once; verify (next game) checks these marks.
        enter(mc, A);
        writeExpect(mark(mc, k + 2, true), k + 2);
        boolean[] pendingAtStop = new boolean[1];
        long quit = onClient(mc, () -> {
            long q = quitToTitle(mc);
            pendingAtStop[0] = FastQuit.anySaving();
            mc.stop();
            return q;
        });
        long stopAt = System.nanoTime();
        while (FastQuit.anySaving()) Thread.sleep(5);
        LOGGER.info("Lads fast quit probe: S7 quit then close the game: quit {} ms, save pending at close {}, background save done {} ms after close",
            quit, pendingAtStop[0], ms(stopAt));
        if (!pendingAtStop[0]) failures.add("S7: no background save was running when the game closed (nothing tested)");
        end("run k=" + k + "; B marks " + marksB.get("k") + "; the closing game waits for the save, verify checks it");
    }

    private static void verify(Minecraft mc) throws Exception {
        JsonObject expect = readExpect().orElseThrow(() -> new IllegalStateException("no expect.json: run phase 'run' first"));
        enter(mc, A);
        String a = check(mc, expect);
        LOGGER.info("Lads fast quit probe: verify A (marks written just before the game closed during its save): {}", a);
        long[] open = onClient(mc, () -> {
            long quit = quitToTitle(mc);
            boolean pending = FastQuit.anySaving();
            long start = System.nanoTime();
            mc.createWorldOpenFlows().openWorld(B, () -> fail("verify: opening B was cancelled"));
            return new long[]{quit, pending ? 1 : 0, ms(start), FastQuit.anySaving() ? 1 : 0};
        });
        waitInWorld(mc, B);
        report("verify quit A then open B", open, "B opened");
        long quit = onClient(mc, () -> quitToTitle(mc));
        FastQuit.awaitAll(120_000);
        for (String world : new String[]{A, B}) {
            FastQuitRegionCheck.Result regions = FastQuitRegionCheck.scan(saves(mc).resolve(world));
            LOGGER.info("Lads fast quit probe: {} region files: {}", world, regions);
            if (!regions.ok() || regions.chunks() == 0) failures.add(world + " regions: " + regions);
        }
        end("verify: A " + a + "; last quit " + quit + " ms");
    }

    private static void baseline(Minecraft mc) throws Exception {
        List<Long> quits = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            enter(mc, A);
            mark(mc, 100 + i);
            quits.add(onClient(mc, () -> quitToTitle(mc)));
            FastQuit.awaitAll(120_000);
        }
        LOGGER.info("Lads fast quit probe: baseline (FastQuit {}) quit times {} ms", FastQuit.enabled() ? "on" : "off", quits);
        end("baseline " + quits);
    }

    /** S8: quit, then the game crashes at once; the JVM exit waits for the save (FastQuit's shutdown hook); verify checks it. */
    private static void crash(Minecraft mc) throws Exception {
        int k = readExpect().map(e -> e.get("k").getAsInt()).orElse(0) + 10;
        enter(mc, A);
        writeExpect(mark(mc, k, true), k);
        boolean[] saving = new boolean[1];
        // Built first: filling a crash report takes longer than a small save.
        net.minecraft.CrashReport report = net.minecraft.CrashReport.forThrowable(new IllegalStateException("Lads FastQuit QA: a crash during a background save"), "FastQuit QA crash");
        long quit = onClient(mc, () -> {
            long q = quitToTitle(mc);
            saving[0] = FastQuit.anySaving();
            mc.delayCrash(report);
            return q;
        });
        LOGGER.info("Lads fast quit probe: S8 crash during the save: quit {} ms, save running when the crash was raised {}", quit, saving[0]);
        long raised = System.nanoTime();
        while (FastQuit.anySaving()) Thread.sleep(5);
        if (!saving[0]) failures.add("S8: no background save was running at the crash (nothing tested)");
        end("crash k=" + k + ": the save finished " + ms(raised) + " ms after the crash was raised; verify checks it");
    }

    // ---- steps ----

    /** Opens (or creates) a world from the title screen and waits until the player stands in it. */
    private static void enter(Minecraft mc, String world) throws Exception {
        onClient(mc, () -> {
            if (Files.isDirectory(saves(mc).resolve(world))) mc.createWorldOpenFlows().openWorld(world, () -> fail("opening " + world + " was cancelled"));
            else create(mc, world);
            return null;
        });
        waitInWorld(mc, world);
    }

    private static void create(Minecraft mc, String world) {
        LevelSettings settings = new LevelSettings(world, GameType.CREATIVE, new LevelSettings.DifficultySettings(Difficulty.PEACEFUL, false, false),
            true, WorldDataConfiguration.DEFAULT);
        mc.createWorldOpenFlows().createFreshLevel(world, settings, new WorldOptions(20261008L, false, false),
            registries -> registries.lookupOrThrow(net.minecraft.core.registries.Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),
            new TitleScreen());
    }

    private static void waitInWorld(Minecraft mc, String world) throws Exception {
        waitFor(world + " to load", 180, () -> {
            skipBackupQuestion(mc);
            MinecraftServer server = mc.getSingleplayerServer();
            return mc.level != null && mc.player != null && server != null && server.isReady() && !server.getPlayerList().getPlayers().isEmpty()
                && mc.gui.screen() == null;
        });
        Thread.sleep(1500);
    }

    /**
     * The "back up before loading?" question (a QA world opened by a game with other data packs or settings than the one that
     * made it): Load without a backup, as a player clicking through would.
     */
    private static void skipBackupQuestion(Minecraft mc) {
        var screen = mc.gui.screen();
        if (!(screen instanceof net.minecraft.client.gui.screens.BackupConfirmScreen)) return;
        String load = Component.translatable("selectWorld.backupJoinSkipButton").getString();
        mc.execute(() -> {
            if (mc.gui.screen() != screen) return;
            for (var child : screen.children())
                if (child instanceof Button button && button.getMessage().getString().equals(load)) {
                    LOGGER.info("Lads fast quit probe: '{}' asked for a backup; loading without one", screen.getTitle().getString());
                    button.onPress(null);
                    return;
                }
        });
    }

    /** The real pause menu's "Save and Quit to Title" button, pressed; returns how long the press took (ms). */
    private static long quitToTitle(Minecraft mc) {
        PauseScreen pause = new PauseScreen(true);
        mc.setScreenAndShow(pause);
        String[] labels = {Component.translatable("menu.returnToMenu").getString(), Component.translatable("menu.disconnect").getString()};
        Button quit = null;
        for (var child : pause.children())
            if (child instanceof Button button && (button.getMessage().getString().equals(labels[0]) || button.getMessage().getString().equals(labels[1]))) quit = button;
        if (quit == null) throw new IllegalStateException("no Save and Quit button in the pause menu");
        long start = System.nanoTime();
        quit.onPress(null);
        long took = ms(start);
        if (mc.level != null) throw new IllegalStateException("still in the world after Save and Quit");
        return took;
    }

    /** Blocks around the player (some far, in other region files), a chest with emeralds and diamonds in slot 1, all from k. */
    private static JsonObject mark(Minecraft mc, int k) throws Exception { return mark(mc, k, false); }

    /**
     * heavy: also a glass block in each of 256 force-loaded chunks around the player (kept loaded, so the next quit saves
     * them all: a background save long enough to close or crash the game in the middle of it).
     */
    private static JsonObject mark(Minecraft mc, int k, boolean heavy) throws Exception {
        MinecraftServer server = mc.getSingleplayerServer();
        return onServer(server, () -> {
            ServerPlayer player = server.getPlayerList().getPlayers().get(0);
            ServerLevel level = server.overworld();
            BlockPos origin = player.blockPosition().above(6);
            JsonObject marks = new JsonObject();
            marks.addProperty("k", k);
            marks.addProperty("x", origin.getX()); marks.addProperty("y", origin.getY()); marks.addProperty("z", origin.getZ());
            for (int i = 0; i < OFFSETS.length; i++)
                level.setBlockAndUpdate(origin.offset(OFFSETS[i][0], 0, OFFSETS[i][1]), MARKS[(k + i) % MARKS.length].defaultBlockState());
            BlockPos chest = origin.offset(2, 0, 0);
            level.setBlockAndUpdate(chest, Blocks.CHEST.defaultBlockState());
            if (level.getBlockEntity(chest) instanceof Container box) box.setItem(0, new ItemStack(Items.EMERALD, k % 64 + 1));
            player.getInventory().setItem(1, new ItemStack(Items.DIAMOND, k % 64 + 1));
            if (heavy) {
                marks.addProperty("heavy", true);
                for (int i = -8; i < 8; i++) for (int j = -8; j < 8; j++) {
                    int cx = (origin.getX() >> 4) + i, cz = (origin.getZ() >> 4) + j;
                    level.setChunkForced(cx, cz, true);
                    level.setBlockAndUpdate(new BlockPos(cx * 16 + 8, origin.getY() + 2, cz * 16 + 8), Blocks.GLASS.defaultBlockState());
                }
            }
            return marks;
        });
    }

    /** "7/7 marks, chest OK, inventory OK", or the failures (recorded). */
    private static String check(Minecraft mc, JsonObject marks) throws Exception {
        MinecraftServer server = mc.getSingleplayerServer();
        int k = marks.get("k").getAsInt();
        BlockPos origin = new BlockPos(marks.get("x").getAsInt(), marks.get("y").getAsInt(), marks.get("z").getAsInt());
        String result = onServer(server, () -> {
            ServerLevel level = server.overworld();
            int good = 0;
            for (int i = 0; i < OFFSETS.length; i++)
                if (level.getBlockState(origin.offset(OFFSETS[i][0], 0, OFFSETS[i][1])).getBlock() == MARKS[(k + i) % MARKS.length]) good++;
            ItemStack inChest = level.getBlockEntity(origin.offset(2, 0, 0)) instanceof Container box ? box.getItem(0) : ItemStack.EMPTY;
            boolean chest = inChest.getItem() == Items.EMERALD && inChest.getCount() == k % 64 + 1;
            ItemStack held = server.getPlayerList().getPlayers().get(0).getInventory().getItem(1);
            boolean inventory = held.getItem() == Items.DIAMOND && held.getCount() == k % 64 + 1;
            String heavy = "";
            if (marks.has("heavy")) {
                int glass = 0;
                for (int i = -8; i < 8; i++) for (int j = -8; j < 8; j++)
                    if (level.getBlockState(new BlockPos(((origin.getX() >> 4) + i) * 16 + 8, origin.getY() + 2, ((origin.getZ() >> 4) + j) * 16 + 8)).getBlock() == Blocks.GLASS) glass++;
                heavy = ", " + glass + "/256 force-loaded chunk marks" + (glass == 256 ? "" : " WRONG");
            }
            return good + "/" + OFFSETS.length + " marks of k=" + k + ", chest " + (chest ? "OK" : "WRONG " + inChest) + ", inventory " + (inventory ? "OK" : "WRONG " + held) + heavy;
        });
        if (!result.startsWith(OFFSETS.length + "/") || result.contains("WRONG")) failures.add("check: " + result);
        return result;
    }

    private static void report(String scenario, long[] r, String detail) {
        LOGGER.info("Lads fast quit probe: {}: quit {} ms, save pending {}, next step waited {} ms, saving after the wait {}; {}",
            scenario, r[0], r[1] == 1, r[2], r[3] == 1, detail);
        if (r[3] == 1) failures.add(scenario + ": the next step ran while a background save was still running");
        if (FastQuit.enabled() && r[0] > 1000) failures.add(scenario + ": quit to title took " + r[0] + " ms");
    }

    /** The harness's END marker: ", 0 failed" passes; any failure is also logged as FAILED. */
    private static void end(String summary) {
        if (!failures.isEmpty()) LOGGER.error("Lads fast quit probe FAILED: {}", failures);
        LOGGER.info("Lads fast quit probe END: phase {}, {} failed; {}", PHASE, failures.size(), summary);
    }

    private static String fail(String what) {
        failures.add(what);
        return "FAILED " + what;
    }

    // ---- plumbing ----

    private static Path saves(Minecraft mc) { return mc.getLevelSource().getBaseDir(); }

    private static java.util.Optional<JsonObject> readExpect() throws Exception {
        Path file = folder.resolve("expect.json");
        return Files.isRegularFile(file) ? java.util.Optional.of(new Gson().fromJson(Files.readString(file), JsonObject.class)) : java.util.Optional.empty();
    }

    private static void writeExpect(JsonObject marks, int k) throws Exception {
        marks.addProperty("k", k);
        Files.writeString(folder.resolve("expect.json"), new Gson().toJson(marks), StandardCharsets.UTF_8);
    }

    /** One step on the client or server thread. */
    private interface Step<T> { T run() throws Exception; }

    private static <T> T onClient(Minecraft mc, Step<T> task) throws Exception { return on(mc, task); }

    private static <T> T onServer(MinecraftServer server, Step<T> task) throws Exception { return on(server, task); }

    private static <T> T on(Executor executor, Step<T> task) throws Exception {
        Supplier<T> supplier = () -> {
            try { return task.run(); } catch (Exception e) { throw new java.util.concurrent.CompletionException(e); }
        };
        return CompletableFuture.supplyAsync(supplier, executor).get(300, TimeUnit.SECONDS);
    }

    private static void waitFor(String what, int seconds, BooleanSupplier ready) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds);
        while (!ready.getAsBoolean()) {
            if (System.nanoTime() > deadline) {
                var screen = Minecraft.getInstance().gui.screen();
                throw new IllegalStateException("timed out waiting for " + what + "; screen " + (screen == null ? "none" : screen.getClass().getName() + " '" + screen.getTitle().getString() + "'"));
            }
            Thread.sleep(100);
        }
    }

    private static long ms(long start) { return (System.nanoTime() - start) / 1_000_000; }
}
