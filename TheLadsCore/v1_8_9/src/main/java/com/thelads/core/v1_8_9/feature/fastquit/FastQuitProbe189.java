package com.thelads.core.v1_8_9.feature.fastquit;

import com.thelads.core.client.FastQuit;
import com.thelads.core.client.FastQuitRegionCheck;
import com.thelads.core.v1_8_9.feature.fastquit.mixin.GuiScreenFastQuitAccessor;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiIngameMenu;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.multiplayer.GuiConnecting;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;
import net.minecraft.world.storage.SaveFormatComparator;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * QA only: -Dthelads.verifyFastQuit=run|verify|baseline (or LADS_VERIFY_FASTQUIT), in a game folder under artifacts\verification.
 * The same scenarios as the Fabric probes (FastQuitProbe26): quit through the pause menu's own button, then rejoin, open
 * another world, connect, delete, rename and list, and last close the game during the save; verify reopens and checks.
 * Steps run at the start of a client tick (never inside Minecraft's scheduled-task lock, which the server's network uses).
 */
public final class FastQuitProbe189 {
    private static final Logger LOGGER = LogManager.getLogger("TheLadsCore");
    private static final String PHASE = System.getProperty("thelads.verifyFastQuit", System.getenv("LADS_VERIFY_FASTQUIT"));
    /** The saves folder is shared by every version (Lads shared content): each version keeps its own QA worlds. */
    private static final String A = "FastQuit QA A 1.8.9", B = "FastQuit QA B 1.8.9", C = "FastQuit QA C 1.8.9";
    private static final Block[] MARKS = {Blocks.gold_block, Blocks.emerald_block, Blocks.diamond_block, Blocks.lapis_block};
    private static final int[][] OFFSETS = {{0, 0}, {40, 0}, {-40, 0}, {0, 40}, {0, -40}, {-600, 0}, {600, 600}};
    private static final List<String> failures = new ArrayList<String>();
    private static volatile Callable<?> pending;
    private static volatile CompletableFuture<Object> pendingResult;
    private static File folder;

    private FastQuitProbe189() {}

    public static void initialize() {
        if (PHASE == null) return;
        File game = Minecraft.getMinecraft().mcDataDir.getAbsoluteFile();
        if (!game.getPath().replace('\\', '/').contains("/artifacts/verification/")) {
            LOGGER.error("Lads fast quit probe FAILED: refusing to run outside artifacts/verification ({})", game);
            return;
        }
        folder = new File(game, "lads-qa/fastquit");
        MinecraftForge.EVENT_BUS.register(new FastQuitProbe189());
        Thread thread = new Thread(new Runnable() { @Override public void run() { FastQuitProbe189.run(); } }, "Lads FastQuit probe");
        thread.setDaemon(true);
        thread.start();
    }

    @SubscribeEvent
    public void tick(TickEvent.ClientTickEvent event) {
        Callable<?> step = pending;
        if (event.phase != TickEvent.Phase.START || step == null) return;
        pending = null;
        try { pendingResult.complete(step.call()); } catch (Throwable e) { pendingResult.completeExceptionally(e); }
    }

    private static void run() {
        try {
            folder.mkdirs();
            final Minecraft mc = Minecraft.getMinecraft();
            waitFor("the title screen", 300, new Check() { public boolean ok() {
                return mc.theWorld == null && mc.currentScreen != null && (mc.currentScreen instanceof GuiMainMenu || mc.currentScreen.getClass().getSimpleName().contains("Title")); } });
            Thread.sleep(8000);
            LOGGER.info("Lads fast quit probe BEGIN: phase {}, FastQuit {}", PHASE, FastQuit.enabled() ? "on" : "off");
            if ("run".equals(PHASE)) scenarios(mc);
            else if ("verify".equals(PHASE)) verify(mc);
            else if ("baseline".equals(PHASE)) baseline(mc);
            else if ("crash".equals(PHASE)) crash(mc);
            else throw new IllegalArgumentException("phase " + PHASE);
        } catch (Throwable failure) {
            LOGGER.error("Lads fast quit probe FAILED: " + failure, failure);
        }
    }

    // ---- phases ----

    private static void scenarios(final Minecraft mc) throws Exception {
        Properties old = readExpect();
        final int k = (old == null ? 0 : Integer.parseInt(old.getProperty("k"))) + 1;
        // S1: quit, then rejoin the same world at once.
        enter(mc, A);
        Properties marks = mark(mc, k);
        long[] s1 = onClient(new Callable<long[]>() { public long[] call() throws Exception {
            long quit = quitToTitle(mc);
            boolean saving = FastQuit.anySaving();
            long start = System.nanoTime();
            open(mc, A);
            return new long[]{quit, saving ? 1 : 0, ms(start), FastQuit.anySaving() ? 1 : 0};
        } });
        waitInWorld(mc, A);
        report("S1 quit then rejoin the same world", s1, check(mc, marks));

        // S2: quit, then open another world at once.
        final Properties marksA2 = mark(mc, k + 1);
        long[] s2 = onClient(new Callable<long[]>() { public long[] call() throws Exception {
            long quit = quitToTitle(mc);
            boolean saving = FastQuit.anySaving();
            long start = System.nanoTime();
            open(mc, B);
            return new long[]{quit, saving ? 1 : 0, ms(start), FastQuit.anySaving() ? 1 : 0};
        } });
        waitInWorld(mc, B);
        Properties marksB = mark(mc, k);
        report("S2 quit then open another world", s2, "B opened");

        // S3: quit, then connect to a server at once (a closed local port).
        long[] s3 = onClient(new Callable<long[]>() { public long[] call() throws Exception {
            long quit = quitToTitle(mc);
            boolean saving = FastQuit.anySaving();
            long start = System.nanoTime();
            mc.displayGuiScreen(new GuiConnecting(new GuiMainMenu(), mc, "127.0.0.1", 1));
            return new long[]{quit, saving ? 1 : 0, ms(start), FastQuit.anySaving() ? 1 : 0};
        } });
        report("S3 quit then connect to a server", s3, "connection attempt started after the save");
        Thread.sleep(3000);
        onClient(new Callable<Object>() { public Object call() { mc.displayGuiScreen(new GuiMainMenu()); return null; } });

        // S4: quit, then delete that world at once (the world list's delete: flushCache + deleteWorldDirectory).
        enter(mc, C);
        mark(mc, k);
        long[] s4 = onClient(new Callable<long[]>() { public long[] call() throws Exception {
            long quit = quitToTitle(mc);
            boolean saving = FastQuit.anySaving();
            long start = System.nanoTime();
            mc.getSaveLoader().flushCache();
            mc.getSaveLoader().deleteWorldDirectory(C);
            return new long[]{quit, saving ? 1 : 0, ms(start), FastQuit.anySaving() ? 1 : 0};
        } });
        report("S4 quit then delete the world", s4, worldDir(mc, C).exists() ? fail("S4: C still exists after delete") : "folder deleted");

        // S5 + S6: quit A, the world list at once, then rename it.
        enter(mc, A);
        String a2 = check(mc, marksA2);
        final long[] s5 = new long[4];
        String list = onClient(new Callable<String>() { public String call() throws Exception {
            long quit = quitToTitle(mc);
            boolean saving = FastQuit.anySaving();
            boolean listed = false;
            for (SaveFormatComparator world : mc.getSaveLoader().getSaveList()) listed |= world.getFileName().equals(A);
            boolean savingNow = FastQuit.saving(worldDir(mc, A).toPath());
            long start = System.nanoTime();
            mc.getSaveLoader().renameWorld(A, A);
            s5[0] = quit; s5[1] = saving ? 1 : 0; s5[2] = ms(start); s5[3] = FastQuit.anySaving() ? 1 : 0;
            if (!listed) return fail("S6: A is missing from the world list during its background save");
            return "A listed" + (savingNow ? " while saving (the list draws Saving...)" : " (save already done)");
        } });
        report("S6 world list during the save", new long[]{s5[0], s5[1], 0, 0}, list + "; A's marks of S2: " + a2);
        report("S5 quit then rename the world", s5, "renamed");

        // S7: quit, then close the game at once; verify (next game) checks these marks.
        enter(mc, A);
        Properties last = mark(mc, k + 2, true);
        writeExpect(last);
        final boolean[] savingAtStop = new boolean[1];
        long quit = onClient(new Callable<Long>() { public Long call() throws Exception {
            long q = quitToTitle(mc);
            savingAtStop[0] = FastQuit.anySaving();
            mc.shutdown(); // waits for the save here (MinecraftFastQuitMixin), then the game loop ends
            return q;
        } });
        long stopAt = System.nanoTime();
        while (FastQuit.anySaving()) Thread.sleep(5);
        LOGGER.info("Lads fast quit probe: S7 quit then close the game: quit {} ms, save running at close {}, background save done {} ms after close returned",
            quit, savingAtStop[0], ms(stopAt));
        if (!savingAtStop[0]) failures.add("S7: no background save was running when the game closed (nothing tested)");
        end("run k=" + k + "; B marks " + marksB.getProperty("k") + "; the closing game waited for the save, verify checks it");
    }

    private static void verify(final Minecraft mc) throws Exception {
        Properties expect = readExpect();
        if (expect == null) throw new IllegalStateException("no expect.properties: run phase 'run' first");
        enter(mc, A);
        String a = check(mc, expect);
        LOGGER.info("Lads fast quit probe: verify A (marks written just before the game closed during its save): {}", a);
        long[] open = onClient(new Callable<long[]>() { public long[] call() throws Exception {
            long quit = quitToTitle(mc);
            boolean saving = FastQuit.anySaving();
            long start = System.nanoTime();
            open(mc, B);
            return new long[]{quit, saving ? 1 : 0, ms(start), FastQuit.anySaving() ? 1 : 0};
        } });
        waitInWorld(mc, B);
        report("verify quit A then open B", open, "B opened");
        long quit = onClient(new Callable<Long>() { public Long call() throws Exception { return quitToTitle(mc); } });
        FastQuit.awaitAll(120_000);
        for (String world : new String[]{A, B}) {
            FastQuitRegionCheck.Result regions = FastQuitRegionCheck.scan(worldDir(mc, world).toPath());
            LOGGER.info("Lads fast quit probe: {} region files: {}", world, regions);
            if (!regions.ok() || regions.chunks() == 0) failures.add(world + " regions: " + regions);
        }
        end("verify: A " + a + "; last quit " + quit + " ms");
    }

    private static void baseline(final Minecraft mc) throws Exception {
        List<Long> quits = new ArrayList<Long>();
        for (int i = 0; i < 3; i++) {
            enter(mc, A);
            mark(mc, 100 + i);
            quits.add(onClient(new Callable<Long>() { public Long call() throws Exception { return quitToTitle(mc); } }));
            FastQuit.awaitAll(120_000);
        }
        LOGGER.info("Lads fast quit probe: baseline (FastQuit {}) quit times {} ms", FastQuit.enabled() ? "on" : "off", quits);
        end("baseline " + quits);
    }

    /** S8: quit, then the game crashes at once; the JVM exit waits for the save (FastQuit's shutdown hook); verify checks it. */
    private static void crash(final Minecraft mc) throws Exception {
        Properties old = readExpect();
        final int k = (old == null ? 0 : Integer.parseInt(old.getProperty("k"))) + 10;
        enter(mc, A);
        writeExpect(mark(mc, k, true));
        final boolean[] saving = new boolean[1];
        // Built first: filling a crash report takes longer than a small save.
        final net.minecraft.crash.CrashReport report = net.minecraft.crash.CrashReport.makeCrashReport(new IllegalStateException("Lads FastQuit QA: a crash during a background save"), "FastQuit QA crash");
        long quit = onClient(new Callable<Long>() { public Long call() throws Exception {
            long q = quitToTitle(mc);
            saving[0] = FastQuit.anySaving();
            mc.crashed(report);
            return q;
        } });
        LOGGER.info("Lads fast quit probe: S8 crash during the save: quit {} ms, save running when the crash was raised {}", quit, saving[0]);
        long raised = System.nanoTime();
        while (FastQuit.anySaving()) Thread.sleep(5);
        if (!saving[0]) failures.add("S8: no background save was running at the crash (nothing tested)");
        end("crash k=" + k + ": the save finished " + ms(raised) + " ms after the crash was raised; verify checks it");
    }

    // ---- steps ----

    private static void enter(final Minecraft mc, final String world) throws Exception {
        onClient(new Callable<Object>() { public Object call() { open(mc, world); return null; } });
        waitInWorld(mc, world);
    }

    /** Opens the world (creates it, superflat creative, if it does not exist). Blocks until its server runs, as the world list does. */
    private static void open(Minecraft mc, String world) {
        boolean exists = new File(worldDir(mc, world), "level.dat").isFile();
        mc.launchIntegratedServer(world, world, exists ? null : new WorldSettings(20261008L, WorldSettings.GameType.CREATIVE, false, false, WorldType.FLAT));
    }

    private static void waitInWorld(final Minecraft mc, String world) throws Exception {
        waitFor(world + " to load", 180, new Check() { public boolean ok() {
            IntegratedServer server = mc.getIntegratedServer();
            return mc.theWorld != null && mc.thePlayer != null && server != null && server.serverIsInRunLoop()
                && !server.getConfigurationManager().getPlayerList().isEmpty() && mc.currentScreen == null; } });
        Thread.sleep(1500);
    }

    /** The pause menu's own Save and Quit to Title button (id 1), pressed; returns how long the press took (ms). */
    private static long quitToTitle(Minecraft mc) throws Exception {
        GuiIngameMenu pause = new GuiIngameMenu();
        mc.displayGuiScreen(pause);
        GuiButton quit = null;
        for (GuiButton button : ((GuiScreenFastQuitAccessor) pause).lads$buttons()) if (button.id == 1) quit = button;
        if (quit == null) throw new IllegalStateException("no Save and Quit button in the pause menu");
        long start = System.nanoTime();
        ((GuiScreenFastQuitAccessor) pause).lads$press(quit);
        long took = ms(start);
        if (mc.theWorld != null) throw new IllegalStateException("still in the world after Save and Quit");
        return took;
    }

    private static Properties mark(Minecraft mc, final int k) throws Exception { return mark(mc, k, false); }

    /** heavy: also a glass block in each of 256 chunks around the player (1.8.9 keeps loaded chunks): a longer background save. */
    private static Properties mark(Minecraft mc, final int k, final boolean heavy) throws Exception {
        final IntegratedServer server = mc.getIntegratedServer();
        return onServer(server, new Callable<Properties>() { public Properties call() {
            EntityPlayerMP player = server.getConfigurationManager().getPlayerList().get(0);
            WorldServer level = server.worldServerForDimension(0);
            BlockPos origin = player.getPosition().up(6);
            Properties marks = new Properties();
            marks.setProperty("k", String.valueOf(k));
            marks.setProperty("x", String.valueOf(origin.getX())); marks.setProperty("y", String.valueOf(origin.getY())); marks.setProperty("z", String.valueOf(origin.getZ()));
            for (int i = 0; i < OFFSETS.length; i++) {
                BlockPos at = origin.add(OFFSETS[i][0], 0, OFFSETS[i][1]);
                level.theChunkProviderServer.loadChunk(at.getX() >> 4, at.getZ() >> 4); // 1.8.9 hands out an empty dummy for an unloaded chunk
                level.setBlockState(at, MARKS[(k + i) % MARKS.length].getDefaultState());
            }
            BlockPos chest = origin.add(2, 0, 0);
            level.setBlockState(chest, Blocks.chest.getDefaultState());
            TileEntity box = level.getTileEntity(chest);
            if (box instanceof IInventory) ((IInventory) box).setInventorySlotContents(0, new ItemStack(Items.emerald, k % 64 + 1));
            player.inventory.setInventorySlotContents(1, new ItemStack(Items.diamond, k % 64 + 1));
            if (heavy) {
                marks.setProperty("heavy", "true");
                for (int i = -8; i < 8; i++) for (int j = -8; j < 8; j++) {
                    int cx = (origin.getX() >> 4) + i, cz = (origin.getZ() >> 4) + j;
                    level.theChunkProviderServer.loadChunk(cx, cz);
                    level.setBlockState(new BlockPos(cx * 16 + 8, origin.getY() + 2, cz * 16 + 8), Blocks.glass.getDefaultState());
                }
            }
            return marks;
        } });
    }

    private static String check(Minecraft mc, Properties marks) throws Exception {
        final IntegratedServer server = mc.getIntegratedServer();
        final int k = Integer.parseInt(marks.getProperty("k"));
        final BlockPos origin = new BlockPos(Integer.parseInt(marks.getProperty("x")), Integer.parseInt(marks.getProperty("y")), Integer.parseInt(marks.getProperty("z")));
        String result = onServer(server, new Callable<String>() { public String call() {
            WorldServer level = server.worldServerForDimension(0);
            int good = 0;
            for (int i = 0; i < OFFSETS.length; i++) {
                BlockPos at = origin.add(OFFSETS[i][0], 0, OFFSETS[i][1]);
                level.theChunkProviderServer.loadChunk(at.getX() >> 4, at.getZ() >> 4);
                if (level.getBlockState(at).getBlock() == MARKS[(k + i) % MARKS.length]) good++;
            }
            TileEntity box = level.getTileEntity(origin.add(2, 0, 0));
            ItemStack inChest = box instanceof IInventory ? ((IInventory) box).getStackInSlot(0) : null;
            boolean chest = inChest != null && inChest.getItem() == Items.emerald && inChest.stackSize == k % 64 + 1;
            ItemStack held = server.getConfigurationManager().getPlayerList().get(0).inventory.getStackInSlot(1);
            boolean inventory = held != null && held.getItem() == Items.diamond && held.stackSize == k % 64 + 1;
            String heavy = "";
            if (marks.getProperty("heavy") != null) {
                int glass = 0;
                for (int i = -8; i < 8; i++) for (int j = -8; j < 8; j++) {
                    int cx = (origin.getX() >> 4) + i, cz = (origin.getZ() >> 4) + j;
                    level.theChunkProviderServer.loadChunk(cx, cz);
                    if (level.getBlockState(new BlockPos(cx * 16 + 8, origin.getY() + 2, cz * 16 + 8)).getBlock() == Blocks.glass) glass++;
                }
                heavy = ", " + glass + "/256 chunk marks" + (glass == 256 ? "" : " WRONG");
            }
            return good + "/" + OFFSETS.length + " marks of k=" + k + ", chest " + (chest ? "OK" : "WRONG " + inChest) + ", inventory " + (inventory ? "OK" : "WRONG " + held) + heavy;
        } });
        if (!result.startsWith(OFFSETS.length + "/") || result.contains("WRONG")) failures.add("check: " + result);
        return result;
    }

    private static void report(String scenario, long[] r, String detail) {
        LOGGER.info("Lads fast quit probe: {}: quit {} ms, save pending {}, next step waited {} ms, saving after the wait {}; {}",
            scenario, r[0], r[1] == 1, r[2], r[3] == 1, detail);
        if (r[3] == 1) failures.add(scenario + ": the next step ran while a background save was still running");
        if (FastQuit.enabled() && r[0] > 1000) failures.add(scenario + ": quit to title took " + r[0] + " ms");
    }

    private static void end(String summary) {
        if (!failures.isEmpty()) LOGGER.error("Lads fast quit probe FAILED: {}", failures);
        LOGGER.info("Lads fast quit probe END: phase {}, {} failed; {}", PHASE, failures.size(), summary);
    }

    private static String fail(String what) {
        failures.add(what);
        return "FAILED " + what;
    }

    // ---- plumbing ----

    private static File worldDir(Minecraft mc, String world) { return new File(new File(mc.mcDataDir, "saves"), world); }

    private static Properties readExpect() throws Exception {
        File file = new File(folder, "expect.properties");
        if (!file.isFile()) return null;
        Properties properties = new Properties();
        InputStream in = new FileInputStream(file);
        try { properties.load(in); } finally { in.close(); }
        return properties;
    }

    private static void writeExpect(Properties marks) throws Exception {
        OutputStream out = new FileOutputStream(new File(folder, "expect.properties"));
        try { marks.store(out, "FastQuitProbe189: marks written just before the game closed during its background save"); } finally { out.close(); }
    }

    /** At the start of the next client tick (see tick). */
    @SuppressWarnings("unchecked")
    private static <T> T onClient(Callable<T> task) throws Exception {
        CompletableFuture<Object> result = new CompletableFuture<Object>();
        pendingResult = result;
        pending = task;
        return (T) result.get(300, TimeUnit.SECONDS);
    }

    private static <T> T onServer(IntegratedServer server, Callable<T> task) throws Exception {
        return server.callFromMainThread(task).get(300, TimeUnit.SECONDS);
    }

    private interface Check { boolean ok(); }

    private static void waitFor(String what, int seconds, Check ready) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds);
        while (!ready.ok()) {
            if (System.nanoTime() > deadline) throw new IllegalStateException("timed out waiting for " + what);
            Thread.sleep(100);
        }
    }

    private static long ms(long start) { return (System.nanoTime() - start) / 1_000_000; }
}
