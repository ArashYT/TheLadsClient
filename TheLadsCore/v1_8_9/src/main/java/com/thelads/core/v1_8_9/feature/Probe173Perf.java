package com.thelads.core.v1_8_9.feature;

import static com.thelads.core.v1_8_9.feature.CoreProbe.after;
import static com.thelads.core.v1_8_9.feature.CoreProbe.check;
import static com.thelads.core.v1_8_9.feature.CoreProbe.retry;
import static com.thelads.core.v1_8_9.feature.CoreProbe.screenshot;
import static com.thelads.core.v1_8_9.feature.Probe172HudFlicker.command;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import com.thelads.core.client.hud.HudElement;
import com.thelads.core.client.hud.HudManager;
import com.thelads.core.client.killbanner.KillBannerStyle;
import com.thelads.core.config.BoolOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.Option;
import com.thelads.core.modules.ItemPhysicsModule;
import com.thelads.core.modules.KillBannerModule;
import java.io.File;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.world.WorldServer;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;
import net.minecraftforge.common.MinecraftForge;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.GL11;

/**
 * QA only (LADS_VERIFY_189_ONLY=perf): the 1.7.3 benchmark. A fresh superflat world (fixed seed, no structures, noon, no
 * weather, no mobs), the player standing still at a fixed pose, then each scene warmed up and measured for a fixed time
 * (thelads.perfSeconds, 60 by default, at most 90: CoreProbe's step limit): S-empty, S-crowd (100 armour stands and players with nametags, 70 behind a stone
 * wall), S-items (300 dropped items, Item Physics on), S-hud (every HUD module, a 15-line scoreboard, chat spam), S-banner
 * (the Reaver kill banner looping), S-particles (500 crits and 10 explosions a tick) and S-swap (10 world leave/joins: their times and
 * the heap after a full GC). Per scene: average FPS, 1% and 0.1% lows, p50/p99 frame ms (KillBanner189's frame times,
 * RenderTickEvent START to START), the render thread's CPU ms per frame, GPU ms per frame (Probe170r.GpuTimer) and the GC
 * time. Results in lads-qa/perf/perf.json, a screenshot per scene in lads-qa/screenshots/perf-*.png. Uncapped FPS and the
 * render distance are the harness's (LADS_VERIFY_PERF_RD); every setting the scenes change is put back.
 */
final class Probe173Perf {
    private static final Logger LOG = LogManager.getLogger("TheLadsCore");
    static final String WORLD = "Lads Perf 1_8_9";
    private static final int SECONDS = Math.min(90, Integer.getInteger("thelads.perfSeconds", 60)), WARM = 10, SWAPS = 10;
    private static final double X = 0.5, Y = 4, Z = 0.5; // the grass top of the "2;7,2x3,2;1;" preset is y 3
    static final List<CoreProbe.Step> STEPS = Arrays.<CoreProbe.Step>asList(Probe173Perf::setup,
        scene("empty", 20), scene("crowd", WARM), scene("items", WARM), scene("hud", WARM), scene("banner", WARM), scene("particles", WARM),
        Probe173Perf::swap, Probe173Perf::swap, Probe173Perf::swap, Probe173Perf::swap, Probe173Perf::swap, Probe173Perf::swap, Probe173Perf::swap,
        Probe173Perf::swap, Probe173Perf::swap, Probe173Perf::swap, Probe173Perf::swaps, Probe173Perf::write);
    private static final JsonObject report = new JsonObject(), scenes = new JsonObject();
    private static final Map<Option, JsonElement> optionsWere = new LinkedHashMap<Option, JsonElement>();
    private static final Map<Module, Boolean> enabledWere = new LinkedHashMap<Module, Boolean>();
    private static final Random RANDOM = new Random();
    private static final KillBannerModule.Pick REAVER = new KillBannerModule.Pick(KillBannerStyle.REAVER, 0, KillBannerStyle.REAVER);
    private static String running;
    private static boolean frozen;
    private static long warmEnd, measureEnd, start, cpu0, gcMs0, gcCount0, alloc0;
    private static int ticks, loadSamples;
    private static double loadSum;
    private static float pitch;
    private static Probe170r.GpuTimer gpu;

    private Probe173Perf() {}

    /** Before the QA world: a fresh perf world each run (no village, seed 173), so every run starts the same. */
    static boolean open(Minecraft mc) throws Exception {
        if (!(mc.currentScreen instanceof net.minecraft.client.gui.GuiMainMenu)) return retry(20);
        CoreProbe.deleteQaWorld(mc, WORLD);
        LOG.info("Lads 1.8.9 perf: opening a fresh '{}'", WORLD);
        mc.launchIntegratedServer(WORLD, WORLD, new WorldSettings(173L, WorldSettings.GameType.CREATIVE, false, false, WorldType.FLAT)
            .enableCommands().setWorldName("2;7,2x3,2;1;"));
        return true;
    }

    private static boolean setup(Minecraft mc) {
        for (String c : new String[] {"gamerule doDaylightCycle false", "gamerule doMobSpawning false", "time set 6000", "weather clear 1000000",
            "difficulty 0", "kill @e[type=!Player]", "tp @p " + X + " " + Y + " " + Z + " 0 0"}) command(mc, c);
        report.addProperty("seconds", SECONDS);
        report.addProperty("renderDistance", mc.gameSettings.renderDistanceChunks);
        report.addProperty("maxFps", mc.gameSettings.limitFramerate);
        report.addProperty("vsync", mc.gameSettings.enableVsync);
        report.addProperty("window", mc.displayWidth + "x" + mc.displayHeight);
        report.addProperty("gl", GL11.glGetString(GL11.GL_RENDERER) + " | " + GL11.glGetString(GL11.GL_VERSION));
        report.addProperty("java", System.getProperty("java.version"));
        report.addProperty("maxHeapMb", Runtime.getRuntime().maxMemory() >> 20);
        report.addProperty("jvmArgs", String.join(" ", ManagementFactory.getRuntimeMXBean().getInputArguments()).replaceAll("-Djava\\.library\\.path=.*?natives ", ""));
        String optiFine;
        try { optiFine = String.valueOf(Class.forName("Config").getField("VERSION").get(null)); } catch (Throwable absent) { optiFine = "absent"; }
        report.addProperty("optifine", optiFine);
        report.add("scenes", scenes);
        LOG.info("Lads 1.8.9 perf BEGIN: {} s per scene, render distance {}, max FPS {}, VSync {}, {}", SECONDS, mc.gameSettings.renderDistanceChunks,
            mc.gameSettings.limitFramerate, mc.gameSettings.enableVsync, optiFine);
        return after(40);
    }

    /** One scene: built on the first call, warmed up, photographed, measured, then taken down. */
    private static CoreProbe.Step scene(String name, int warmSeconds) {
        return mc -> {
            long now = System.nanoTime();
            if (!name.equals(running)) {
                running = name;
                ticks = 0;
                pitch = name.equals("items") ? 35 : 10;
                build(mc, name);
                System.gc(); // every scene starts from a collected heap; the collection itself is not measured
                warmEnd = now + warmSeconds * 1_000_000_000L;
                measureEnd = 0;
            }
            pin(mc);
            if (!frozen) tick(mc, name, ticks++);
            if (measureEnd == 0 && now >= warmEnd) {
                if (name.equals("banner") && !frozen) { // the photo at a fixed moment: 3 kills, 1 s in
                    KillBanner189.trigger(3, false, false, REAVER);
                    KillBanner189.freeze(1.0);
                    frozen = true;
                    return retry(2);
                }
                mc.guiAchievement.clearAchievements(); // the "Press E" hint is not part of any scene
                screenshot(mc, "perf-" + name);
                if (frozen) KillBanner189.freeze(Double.NaN);
                frozen = false;
                begin(mc, name);
                measureEnd = System.nanoTime() + SECONDS * 1_000_000_000L;
            }
            if (measureEnd == 0 || now < measureEnd) {
                if (measureEnd != 0 && ticks % 20 == 0) sampleLoad();
                return retry(0);
            }
            end(mc, name);
            teardown(mc, name);
            running = null;
            return after(20);
        };
    }

    private static void build(Minecraft mc, String name) {
        switch (name) {
            case "crowd": {
                // A stone wall 8 blocks ahead; 70 entities behind it, 30 in front (armour stands and players, all with nametags).
                command(mc, "fill -12 4 8 12 12 8 stone");
                int id = -17300;
                for (int i = 0; i < 100; i++) {
                    boolean behind = i < 70;
                    double ex = behind ? -9 + (i % 10) * 2 : -13.5 + (i % 10) * 3, ez = behind ? 12 + (i / 10) * 2 : 3.5 + ((i - 70) / 10) * 1.5;
                    EntityLivingBase entity;
                    if (i % 2 == 0) {
                        EntityArmorStand stand = new EntityArmorStand(mc.theWorld);
                        stand.setCustomNameTag("Stand " + i);
                        stand.setAlwaysRenderNameTag(true);
                        entity = stand;
                    } else {
                        entity = new EntityOtherPlayerMP(mc.theWorld, new GameProfile(UUID.nameUUIDFromBytes(("perf" + i).getBytes(StandardCharsets.UTF_8)), "PerfBot" + i));
                    }
                    entity.setCurrentItemOrArmor(0, new ItemStack(Items.diamond_sword));
                    entity.setCurrentItemOrArmor(1, new ItemStack(Items.iron_boots));
                    entity.setCurrentItemOrArmor(2, new ItemStack(Items.iron_leggings));
                    entity.setCurrentItemOrArmor(3, new ItemStack(Items.iron_chestplate));
                    entity.setCurrentItemOrArmor(4, new ItemStack(Items.iron_helmet));
                    entity.setPositionAndRotation(ex, Y, ez, 180, 0);
                    entity.rotationYawHead = entity.renderYawOffset = 180;
                    mc.theWorld.addEntityToWorld(id - i, entity);
                }
                break;
            }
            case "items": {
                // 300 items in a 20 x 15 grid, 1 block apart (never merged), ten kinds in turn; nobody picks them up.
                remember(mc, ItemPhysicsModule.NAME);
                Options189.module(ItemPhysicsModule.NAME).setEnabled(true);
                final Item[] kinds = {Items.diamond_sword, Items.bow, Items.apple, Items.iron_ingot, Item.getItemFromBlock(Blocks.stone),
                    Item.getItemFromBlock(Blocks.glass), Item.getItemFromBlock(Blocks.torch), Items.ender_pearl, Item.getItemFromBlock(Blocks.gold_block), Items.redstone};
                final WorldServer world = mc.getIntegratedServer().worldServers[0];
                world.addScheduledTask(() -> {
                    for (int i = 0; i < 300; i++) {
                        EntityItem item = new EntityItem(world, -9.5 + i % 20, Y, 3.5 + i / 20, new ItemStack(kinds[i % kinds.length]));
                        item.motionX = item.motionY = item.motionZ = 0;
                        item.setInfinitePickupDelay();
                        item.setNoDespawn();
                        world.spawnEntityInWorld(item);
                    }
                });
                break;
            }
            case "hud": {
                // Every HUD module on (their own defaults otherwise), the paper doll always shown, Autohide off; survival bars.
                for (HudElement element : HudManager.getInstance().getElements()) {
                    Module module = Options189.module(element.getModuleName());
                    if (!element.isAvailable() || module == null) continue;
                    remember(mc, element.getModuleName());
                    module.setEnabled(true);
                }
                remember(mc, "Autohide");
                if (Options189.module("Autohide") != null) Options189.module("Autohide").setEnabled(false);
                if (Options189.module("Paperdoll") != null) {
                    remember(mc, "Paperdoll");
                    ((BoolOption) Options189.module("Paperdoll").getOption("Always Display")).set(true);
                }
                List<String> commands = new ArrayList<String>(Arrays.asList("gamemode 0 @a", "scoreboard objectives add ladsperf dummy Perf QA",
                    "scoreboard objectives setdisplay sidebar ladsperf"));
                for (int i = 1; i <= 15; i++) commands.add("scoreboard players set Line" + (i < 10 ? "0" : "") + i + " ladsperf " + i);
                for (String c : commands) command(mc, c);
                mc.ingameGUI.getChatGUI().clearChatMessages();
                break;
            }
            case "banner":
                remember(mc, "KillBanner");
                Options189.module("KillBanner").setEnabled(true);
                break;
            default:
        }
        RANDOM.setSeed(173);
    }

    /** Every client tick of a scene: what keeps it busy. */
    private static void tick(Minecraft mc, String name, int tick) {
        switch (name) {
            case "hud": // 20 chat lines a second, half of them from players (Chat Heads)
                mc.ingameGUI.getChatGUI().printChatMessage(new ChatComponentText(tick % 2 == 0 ? "<PerfBot" + tick % 7 + "> chat spam line " + tick
                    : "[Server] perf chat line " + tick + ": the quick brown fox jumps over the lazy dog"));
                break;
            case "banner": // a Reaver kill every 3 s, 1 to 5 kills in turn
                if (tick % 60 == 0) KillBanner189.trigger(tick / 60 % 5 + 1, false, false, REAVER);
                break;
            case "particles": // 500 crits and 10 explosions a tick, 3 to 9 blocks ahead
                for (int i = 0; i < 500; i++)
                    mc.theWorld.spawnParticle(EnumParticleTypes.CRIT, X - 3 + RANDOM.nextDouble() * 6, Y + RANDOM.nextDouble() * 3, Z + 3 + RANDOM.nextDouble() * 6,
                        RANDOM.nextGaussian() * 0.3, RANDOM.nextDouble() * 0.4, RANDOM.nextGaussian() * 0.3);
                for (int i = 0; i < 10; i++)
                    mc.theWorld.spawnParticle(EnumParticleTypes.EXPLOSION_LARGE, X - 3 + RANDOM.nextDouble() * 6, Y + RANDOM.nextDouble() * 2,
                        Z + 4 + RANDOM.nextDouble() * 5, 0.5, 0, 0);
                break;
            default:
        }
    }

    private static void teardown(Minecraft mc, String name) {
        switch (name) {
            case "crowd":
                for (int i = 0; i < 100; i++) mc.theWorld.removeEntityFromWorld(-17300 - i);
                command(mc, "fill -12 4 8 12 12 8 air");
                command(mc, "fill -12 3 8 12 3 8 grass"); // what the wall turned to dirt (random ticks) grows back now
                break;
            case "items":
                command(mc, "kill @e[type=Item]");
                break;
            case "hud":
                for (String c : new String[] {"scoreboard objectives remove ladsperf", "gamemode 1 @a"}) command(mc, c);
                mc.ingameGUI.getChatGUI().clearChatMessages();
                break;
            case "banner":
                KillBanner189.reset();
                break;
            default:
        }
        optionsWere.forEach(Option::load);
        enabledWere.forEach(Module::setEnabled);
        optionsWere.clear();
        enabledWere.clear();
    }

    /** A module's state, put back after the scene. */
    private static void remember(Minecraft mc, String module) {
        Module m = Options189.module(module);
        if (m == null || enabledWere.containsKey(m)) return;
        enabledWere.put(m, m.isEnabled());
        for (Option option : m.getOptions()) optionsWere.put(option, option.save());
    }

    /** The player stands still at the scene's pose. */
    private static void pin(Minecraft mc) {
        mc.thePlayer.setPosition(X, mc.thePlayer.posY, Z);
        mc.thePlayer.prevPosX = mc.thePlayer.lastTickPosX = X;
        mc.thePlayer.prevPosZ = mc.thePlayer.lastTickPosZ = Z;
        mc.thePlayer.motionX = mc.thePlayer.motionZ = 0;
        mc.thePlayer.capabilities.isFlying = false;
        mc.thePlayer.rotationYaw = mc.thePlayer.prevRotationYaw = 0;
        mc.thePlayer.rotationPitch = mc.thePlayer.prevRotationPitch = pitch;
    }

    private static void begin(Minecraft mc, String name) {
        if (name.equals("crowd")) check(mc.theWorld.loadedEntityList.size() >= 101, "perf crowd: 100 entities in the world (" + mc.theWorld.loadedEntityList.size() + ")");
        if (name.equals("items")) {
            int items = 0;
            for (Object entity : mc.theWorld.loadedEntityList) if (entity instanceof EntityItem) items++;
            check(items == 300, "perf items: 300 dropped items in the world, none merged (" + items + ")");
        }
        KillBanner189.recordFrames(SECONDS * 3000); // up to 3000 FPS
        gpu = new Probe170r.GpuTimer();
        MinecraftForge.EVENT_BUS.register(gpu);
        loadSum = loadSamples = 0;
        start = System.nanoTime();
        cpu0 = ManagementFactory.getThreadMXBean().getCurrentThreadCpuTime();
        alloc0 = allocated();
        long[] gc = gc();
        gcMs0 = gc[0];
        gcCount0 = gc[1];
    }

    private static void end(Minecraft mc, String name) {
        long elapsed = System.nanoTime() - start, cpu = ManagementFactory.getThreadMXBean().getCurrentThreadCpuTime() - cpu0, alloc = allocated() - alloc0;
        long[] gc = gc();
        MinecraftForge.EVENT_BUS.unregister(gpu);
        long[] times = Arrays.copyOf(KillBanner189.frameTimes, KillBanner189.frameCount);
        KillBanner189.recordFrames(0);
        check(times.length > SECONDS * 5, "perf " + name + ": frames recorded (" + times.length + ")");
        JsonObject s = stats(times);
        s.addProperty("cpuMs", round(cpu / 1e6 / times.length));
        s.addProperty("gpuMs", round(gpu.milliseconds()));
        s.addProperty("gcMs", gc[0] - gcMs0);
        s.addProperty("gcCount", gc[1] - gcCount0);
        s.addProperty("allocMBps", round(alloc / 1048576.0 / (elapsed / 1e9)));
        s.addProperty("debugFps", Minecraft.getDebugFPS());
        s.addProperty("sysCpuPct", round(loadSamples == 0 ? -1 : loadSum / loadSamples * 100)); // the whole machine, this game included
        gpu.close();
        scenes.add(name, s);
        LOG.info("Lads 1.8.9 perf {}: {}", name, s);
    }

    /** FPS figures from frame times (ns): the lows are the mean FPS of the slowest 1 % and 0.1 % of frames. */
    static JsonObject stats(long[] times) {
        long[] sorted = times.clone();
        Arrays.sort(sorted);
        long total = 0;
        for (long t : sorted) total += t;
        JsonObject s = new JsonObject();
        s.addProperty("frames", sorted.length);
        s.addProperty("avgFps", round(sorted.length / (total / 1e9)));
        s.addProperty("low1Fps", round(lowFps(sorted, 0.01)));
        s.addProperty("low01Fps", round(lowFps(sorted, 0.001)));
        s.addProperty("p50Ms", round(percentile(sorted, 0.50) / 1e6));
        s.addProperty("p99Ms", round(percentile(sorted, 0.99) / 1e6));
        s.addProperty("maxMs", round(sorted[sorted.length - 1] / 1e6));
        return s;
    }

    private static double lowFps(long[] sorted, double share) {
        int n = Math.max(1, (int) Math.round(sorted.length * share));
        long sum = 0;
        for (int i = sorted.length - n; i < sorted.length; i++) sum += sorted[i];
        return n / (sum / 1e9);
    }

    private static long percentile(long[] sorted, double p) {
        return sorted[Math.min(sorted.length - 1, Math.max(0, (int) Math.ceil(p * sorted.length) - 1))];
    }

    private static double round(double value) { return Math.round(value * 100) / 100.0; }

    /** Total GC time (ms) and count so far, over every collector. */
    private static long[] gc() {
        long ms = 0, count = 0;
        for (GarbageCollectorMXBean bean : ManagementFactory.getGarbageCollectorMXBeans()) { ms += Math.max(0, bean.getCollectionTime()); count += Math.max(0, bean.getCollectionCount()); }
        return new long[] {ms, count};
    }

    /** The whole machine's CPU load once a second while measuring: anything else running (a build, another game) shows here. */
    private static void sampleLoad() {
        java.lang.management.OperatingSystemMXBean os = ManagementFactory.getOperatingSystemMXBean();
        double load = os instanceof com.sun.management.OperatingSystemMXBean ? ((com.sun.management.OperatingSystemMXBean) os).getSystemCpuLoad() : -1;
        if (load < 0) return;
        loadSum += load;
        loadSamples++;
    }

    /** Bytes the render thread allocated so far (HotSpot), or 0. */
    private static long allocated() {
        java.lang.management.ThreadMXBean threads = ManagementFactory.getThreadMXBean();
        return threads instanceof com.sun.management.ThreadMXBean ? ((com.sun.management.ThreadMXBean) threads).getThreadAllocatedBytes(Thread.currentThread().getId()) : 0;
    }

    private static final JsonArray SWAPS_DONE = new JsonArray();
    private static int phase;
    private static long since, leaveMs;

    /** S-swap, one of its 10 cycles: leave and rejoin the perf world, timed, then the heap after a full GC once back in. */
    private static boolean swap(Minecraft mc) {
        if (phase == 0) { // leave
            long t = System.nanoTime();
            mc.theWorld.sendQuittingDisconnectingPacket();
            mc.loadWorld(null); // blocks while the integrated server saves and stops
            mc.displayGuiScreen(new net.minecraft.client.gui.GuiMainMenu());
            leaveMs = (System.nanoTime() - t) / 1_000_000L;
            phase = 1;
            return retry(10);
        }
        if (phase == 1) { // join
            since = System.nanoTime();
            mc.launchIntegratedServer(WORLD, WORLD, null);
            phase = 2;
            return retry(0);
        }
        if (phase == 2) { // in the world, no loading screen
            if (mc.theWorld == null || mc.thePlayer == null || mc.currentScreen != null) return retry(0);
            JsonObject cycle = new JsonObject();
            cycle.addProperty("leaveMs", leaveMs);
            cycle.addProperty("joinMs", (System.nanoTime() - since) / 1_000_000L);
            SWAPS_DONE.add(cycle);
            phase = 3;
            return retry(40); // 2 s in the world, chunks around the player loaded
        }
        System.gc();
        System.gc();
        long heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
        JsonObject cycle = SWAPS_DONE.get(SWAPS_DONE.size() - 1).getAsJsonObject();
        cycle.addProperty("heapMb", round(heap / 1048576.0));
        LOG.info("Lads 1.8.9 perf swap {}: {}", SWAPS_DONE.size(), cycle);
        phase = 0;
        return after(10);
    }

    private static boolean swaps(Minecraft mc) {
        JsonObject cycle = SWAPS_DONE.get(SWAPS_DONE.size() - 1).getAsJsonObject();
        long leave = 0, join = 0;
        for (JsonElement c : SWAPS_DONE) { leave += c.getAsJsonObject().get("leaveMs").getAsLong(); join += c.getAsJsonObject().get("joinMs").getAsLong(); }
        JsonObject s = new JsonObject();
        s.addProperty("avgLeaveMs", round(leave / (double) SWAPS));
        s.addProperty("avgJoinMs", round(join / (double) SWAPS));
        double first = SWAPS_DONE.get(0).getAsJsonObject().get("heapMb").getAsDouble(), last = cycle.get("heapMb").getAsDouble();
        s.addProperty("heapFirstMb", first);
        s.addProperty("heapLastMb", last);
        s.addProperty("heapGrowthMb", round(last - first));
        s.add("cycles", SWAPS_DONE);
        scenes.add("swap", s);
        check(SWAPS_DONE.size() == SWAPS, "perf swap: " + SWAPS + " leave/join cycles timed");
        return after(20);
    }

    private static boolean write(Minecraft mc) throws Exception {
        File folder = new File(mc.mcDataDir, "lads-qa/perf");
        check(folder.isDirectory() || folder.mkdirs(), "perf folder " + folder);
        Files.write(new File(folder, "perf.json").toPath(), new GsonBuilder().setPrettyPrinting().create().toJson(report).getBytes(StandardCharsets.UTF_8));
        LOG.info("Lads 1.8.9 perf END: {} scenes in lads-qa/perf/perf.json", scenes.entrySet().size());
        return after(5);
    }
}
