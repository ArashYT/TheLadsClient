package com.thelads.core.v26_2.feature.async;

import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.modules.AsyncModule;
import com.thelads.core.v26_2.feature.NativeWorldVerification;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * QA only (auto-world, ".lads-qa-async" from the harness's LADS_VERIFY_ASYNC): fences two pens next to the player, fills
 * them with ~1700 villagers, zombies and cows, measures server tick time with Async off then on, then (on) adds items,
 * bread, experience, loving cows, job sites and a cramming column and checks the game still behaves: mobs path around,
 * villagers take job sites and pick up bread, cows breed, items merge, cramming kills, peaceful despawns zombies, nothing
 * is duplicated or lost and Async never falls back. Everything it placed or spawned is removed and settings are restored.
 */
final class AsyncStressProbe {
    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore");
    private static final String TAG = "lads_async";
    private static final long SECOND = 1_000_000_000L;
    private static final int VILLAGERS = 700, ZOMBIES = 700, COWS = 300, PIGS = 40, COBBLE_PAIRS = 300, BREAD = 100, ORBS = 200, JOB_SITES = 30;
    private static final List<Long> TICKS = new ArrayList<>();
    private static final List<String> FAILURES = new ArrayList<>(), REPORT = new ArrayList<>();
    private static final List<BlockPos> PLACED = new ArrayList<>();
    private static int step = -1, passed;
    private static long due, tickStart, parallelBefore, phasesBefore, regionsBefore, entitiesBefore;
    private static boolean timing, enabledBefore;
    private static BlockPos center;
    private static GameType modeBefore;
    private static Difficulty difficultyBefore;
    private static double movedOff;
    private static Map<UUID, double[]> positions = new HashMap<>();
    private static int employedBefore, cobbleBefore, breadBefore;
    private static final java.util.Set<UUID> EXISTING = new java.util.HashSet<>();

    private AsyncStressProbe() {}

    static void initialize() {
        if (!Boolean.getBoolean("thelads.verifyAutoWorld")) return;
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            try { tick(mc); } catch (Throwable failure) { fail("probe step " + step + ": " + failure); finish(); LOGGER.error("Lads async stress probe FAILED", failure); }
        });
        ServerTickEvents.START_SERVER_TICK.register(server -> tickStart = System.nanoTime());
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (!timing) return;
            synchronized (TICKS) { TICKS.add(System.nanoTime() - tickStart); }
        });
    }

    private static void tick(Minecraft mc) throws Exception {
        long now = System.nanoTime();
        if (step < 0) {
            if (!NativeWorldVerification.worldReady() || mc.gui.screen() != null || mc.getSingleplayerServer() == null) return;
            Path request = FabricLoader.getInstance().getGameDir().resolve(".lads-qa-async");
            if (!Files.isRegularFile(request, LinkOption.NOFOLLOW_LINKS)) return;
            Files.delete(request);
            LOGGER.info("Lads async stress probe BEGIN: {} villagers, {} zombies, {} cows on the integrated server", VILLAGERS, ZOMBIES, COWS);
            step = 0;
        }
        if (step > 7 || now < due) return;
        MinecraftServer server = mc.getSingleplayerServer();
        Module module = ModuleManager.getInstance().getModule(AsyncModule.NAME);
        switch (step) {
            case 0 -> { // pens and mobs, Async off
                enabledBefore = module.isEnabled();
                module.setEnabled(false);
                onServer(server, () -> { build(server); return null; });
                next(20);
            }
            case 1 -> { // off: 30 s of tick times
                positions = onServer(server, () -> zombiePositions(server));
                startTiming();
                next(30);
            }
            case 2 -> {
                String off = stopTiming("Async off");
                movedOff = onServer(server, () -> movedFraction(server));
                REPORT.add(off + String.format(Locale.ROOT, ", %.0f%% of zombies walked 3+ blocks", movedOff * 100));
                module.setEnabled(true);
                parallelBefore = AsyncTicking.PARALLEL_TICKS.get(); phasesBefore = AsyncTicking.PHASES.get();
                regionsBefore = AsyncTicking.REGIONS.get(); entitiesBefore = AsyncTicking.PARALLEL_ENTITIES.get();
                next(5);
            }
            case 3 -> { // on: 30 s of tick times
                positions = onServer(server, () -> zombiePositions(server));
                startTiming();
                next(30);
            }
            case 4 -> {
                String on = stopTiming("Async on");
                double moved = onServer(server, () -> movedFraction(server));
                long ticks = AsyncTicking.PARALLEL_TICKS.get() - parallelBefore, phases = AsyncTicking.PHASES.get() - phasesBefore;
                REPORT.add(on + String.format(Locale.ROOT, ", %.0f%% of zombies walked 3+ blocks; %d parallel ticks, %.1f regions per phase, %.0f entities per parallel tick",
                    moved * 100, ticks, phases == 0 ? 0.0 : (AsyncTicking.REGIONS.get() - regionsBefore) / (double) phases,
                    ticks == 0 ? 0.0 : (AsyncTicking.PARALLEL_ENTITIES.get() - entitiesBefore) / (double) ticks));
                check(ticks > 400 && phases > 0, "Async ticked in parallel (" + ticks + " parallel ticks)");
                check(moved >= movedOff * 0.5 && moved > 0.1, "zombies keep pathing with Async on");
                onServer(server, () -> { soakSetup(server); return null; });
                next(25);
            }
            case 5 -> { // 25 s into the soak
                onServer(server, () -> { midChecks(server); return null; });
                next(60);
            }
            case 6 -> { // 85 s into the soak
                onServer(server, () -> { lateChecks(server); server.setDifficulty(Difficulty.PEACEFUL, true); return null; });
                next(4);
            }
            case 7 -> {
                onServer(server, () -> {
                    check(level(server).getEntitiesOfClass(Zombie.class, pens()).isEmpty(), "peaceful despawns every zombie (checkDespawn on workers)");
                    return null;
                });
                check(AsyncTicking.fallback() == null, "no error or unsafe access in the whole run" + (AsyncTicking.fallback() == null ? "" : ": " + AsyncTicking.fallback()));
                finish();
            }
            default -> {}
        }
    }

    private static void build(MinecraftServer server) {
        ServerLevel level = level(server);
        ServerPlayer player = server.getPlayerList().getPlayers().get(0);
        center = player.blockPosition();
        modeBefore = player.gameMode();
        difficultyBefore = level.getDifficulty();
        player.setGameMode(GameType.CREATIVE);
        level.getEntitiesOfClass(Entity.class, pens().inflate(4)).forEach(entity -> EXISTING.add(entity.getUUID()));
        server.setDifficulty(Difficulty.NORMAL, true);
        // two pens of 36 x 80 blocks, 8 blocks either side of the player
        fence(level, center.getX() - 44, center.getX() - 8);
        fence(level, center.getX() + 8, center.getX() + 44);
        for (int i = 0; i < VILLAGERS; i++) summon(server, "villager", -42, -10, i, "");
        for (int i = 0; i < COWS; i++) summon(server, "cow", -42, -10, i * 7 + 3, "");
        // husks: zombies that do not burn in daylight
        for (int i = 0; i < ZOMBIES; i++) summon(server, "husk", 10, 42, i, "");
    }

    private static void soakSetup(MinecraftServer server) {
        ServerLevel level = level(server);
        employedBefore = (int) level.getEntitiesOfClass(Villager.class, pens()).stream().filter(AsyncStressProbe::employed).count();
        for (int i = 0; i < JOB_SITES; i++) {
            BlockPos pos = surface(level, center.getX() - 40 + (i % 6) * 6, center.getZ() - 35 + (i / 6) * 15);
            if (level.getBlockState(pos).isAir()) { level.setBlockAndUpdate(pos, (i % 2 == 0 ? Blocks.COMPOSTER : Blocks.BARREL).defaultBlockState()); PLACED.add(pos); }
        }
        for (int i = 0; i < COBBLE_PAIRS; i++) for (int copy = 0; copy < 2; copy++)
            summon(server, "item", -40, -12, i * 13 + 1, "Item:{id:\"minecraft:cobblestone\",count:1}");
        for (int i = 0; i < BREAD; i++) summon(server, "item", -40, -12, i * 17 + 5, "Item:{id:\"minecraft:bread\",count:1}");
        for (int i = 0; i < ORBS; i++) summon(server, "experience_orb", -40, -24, i * 11 + 2, "Value:3");
        for (Cow cow : level.getEntitiesOfClass(Cow.class, pens())) if (!cow.isBaby()) cow.setInLoveTime(600);
        // a 1 x 1 barrier column with 40 pigs: entity cramming (24) must kill the extra ones
        BlockPos column = surface(level, center.getX() + 26, center.getZ() + 30);
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) for (int dy = 0; dy < 3; dy++)
            if ((dx != 0 || dz != 0)) place(level, column.offset(dx, dy, dz), Blocks.BARRIER.defaultBlockState());
        for (int i = 0; i < PIGS; i++) command(server, String.format(Locale.ROOT, "summon pig %d %d %d {Tags:[\"%s\"]}", column.getX(), column.getY(), column.getZ(), TAG));
        cobbleBefore = items(level, Items.COBBLESTONE);
        breadBefore = items(level, Items.BREAD) + villagerBread(level);
        REPORT.add("soak entities: " + level.getEntitiesOfClass(Entity.class, pens()).size());
    }

    private static void midChecks(MinecraftServer server) {
        ServerLevel level = level(server);
        int cobble = items(level, Items.COBBLESTONE), stacks = level.getEntitiesOfClass(ItemEntity.class, pens(), item -> item.getItem().is(Items.COBBLESTONE)).size();
        check(cobble == cobbleBefore, "item merging keeps every cobblestone (" + cobbleBefore + " -> " + cobble + ")");
        check(stacks <= COBBLE_PAIRS * 2 * 0.6, "dropped items merge (" + COBBLE_PAIRS * 2 + " -> " + stacks + " stacks)");
        int pigs = level.getEntitiesOfClass(Pig.class, pens()).size();
        check(pigs <= 24, "entity cramming kills the pigs over 24 (" + PIGS + " -> " + pigs + ")");
        int orbs = level.getEntitiesOfClass(ExperienceOrb.class, pens()).size();
        check(orbs < ORBS, "experience orbs merge (" + ORBS + " -> " + orbs + ")");
    }

    private static void lateChecks(MinecraftServer server) {
        ServerLevel level = level(server);
        long babies = level.getEntitiesOfClass(Cow.class, pens(), Cow::isBaby).size();
        check(babies >= 10, "loving cows breed (" + babies + " calves)");
        int employed = (int) level.getEntitiesOfClass(Villager.class, pens()).stream().filter(AsyncStressProbe::employed).count();
        check(employed >= employedBefore + 5, "villagers claim new job sites (" + employedBefore + " -> " + employed + " employed)");
        int ground = items(level, Items.BREAD), held = villagerBread(level);
        check(ground + held == breadBefore, "bread is neither lost nor duplicated (" + breadBefore + " -> " + ground + " on the ground + " + held + " carried)");
        check(held > 0, "villagers pick up bread (" + held + " carried)");
    }

    private static void finish() {
        if (step == 99) return;
        step = 99;
        timing = false;
        Minecraft mc = Minecraft.getInstance();
        MinecraftServer server = mc.getSingleplayerServer();
        Module module = ModuleManager.getInstance().getModule(AsyncModule.NAME);
        if (module != null) module.setEnabled(enabledBefore);
        if (server != null && center != null) {
            try {
                onServer(server, () -> {
                    ServerLevel level = level(server);
                    for (Entity entity : level.getEntitiesOfClass(Entity.class, pens().inflate(4), entity -> !(entity instanceof Player) && !EXISTING.contains(entity.getUUID())))
                        entity.discard();
                    for (BlockPos pos : PLACED) level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                    if (difficultyBefore != null) server.setDifficulty(difficultyBefore, true);
                    if (modeBefore != null) server.getPlayerList().getPlayers().forEach(player -> player.setGameMode(modeBefore));
                    return null;
                });
            } catch (Exception failure) { fail("cleanup: " + failure); }
        }
        REPORT.forEach(line -> LOGGER.info("Lads async stress probe: {}", line));
        try { Files.write(FabricLoader.getInstance().getGameDir().resolve("async-stress.txt"), concat(REPORT, FAILURES)); } catch (Exception ignored) {}
        if (!FAILURES.isEmpty()) LOGGER.error("Lads async stress probe FAILED: {}", String.join("; ", FAILURES));
        LOGGER.info("Lads async stress probe END: {} passed, {} failed", passed, FAILURES.size());
    }

    // ---- helpers ----

    private static void next(int seconds) { step++; due = System.nanoTime() + seconds * SECOND; }

    private static void startTiming() { synchronized (TICKS) { TICKS.clear(); } timing = true; }

    private static String stopTiming(String label) {
        timing = false;
        List<Long> ticks;
        synchronized (TICKS) { ticks = new ArrayList<>(TICKS); }
        ticks.sort(null);
        double mean = ticks.stream().mapToLong(Long::longValue).average().orElse(0) / 1e6;
        double p95 = ticks.isEmpty() ? 0 : ticks.get((int) (ticks.size() * 0.95)) / 1e6;
        return String.format(Locale.ROOT, "%s: %d ticks, mean %.1f ms, p95 %.1f ms", label, ticks.size(), mean, p95);
    }

    private static <T> T onServer(MinecraftServer server, Supplier<T> task) {
        return server.submit(task).join();
    }

    private static ServerLevel level(MinecraftServer server) { return server.getPlayerList().getPlayers().get(0).level(); }

    private static AABB pens() { return new AABB(center.getX() - 46, center.getY() - 24, center.getZ() - 42, center.getX() + 46, center.getY() + 24, center.getZ() + 42); }

    private static void fence(ServerLevel level, int x0, int x1) {
        int z0 = center.getZ() - 40, z1 = center.getZ() + 40;
        for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) {
            if (x != x0 && x != x1 && z != z0 && z != z1) continue;
            BlockPos ground = surface(level, x, z);
            for (int dy = 0; dy < 3; dy++) place(level, ground.above(dy), Blocks.BARRIER.defaultBlockState());
        }
    }

    private static void place(ServerLevel level, BlockPos pos, BlockState state) {
        if (!level.getBlockState(pos).isAir()) return;
        level.setBlock(pos, state, 2);
        PLACED.add(pos.immutable());
    }

    private static BlockPos surface(ServerLevel level, int x, int z) {
        return new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
    }

    /** Spreads entity i over a pen spanning dx0..dx1 blocks east of the player and 76 blocks north to south. */
    private static void summon(MinecraftServer server, String type, int dx0, int dx1, int i, String nbt) {
        int width = dx1 - dx0 + 1;
        int x = center.getX() + dx0 + Math.floorMod(i * 7, width), z = center.getZ() - 38 + Math.floorMod(i * 11 / width + i * 3, 77);
        BlockPos at = surface(level(server), x, z);
        command(server, String.format(Locale.ROOT, "summon %s %d %d %d {Tags:[\"%s\"]%s}", type, x, at.getY(), z, TAG, nbt.isEmpty() ? "" : "," + nbt));
    }

    private static void command(MinecraftServer server, String command) {
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), command);
    }

    private static Map<UUID, double[]> zombiePositions(MinecraftServer server) {
        Map<UUID, double[]> map = new HashMap<>();
        for (Zombie zombie : level(server).getEntitiesOfClass(Zombie.class, pens())) map.put(zombie.getUUID(), new double[]{zombie.getX(), zombie.getZ()});
        return map;
    }

    private static double movedFraction(MinecraftServer server) {
        int moved = 0, seen = 0;
        for (Zombie zombie : level(server).getEntitiesOfClass(Zombie.class, pens())) {
            double[] was = positions.get(zombie.getUUID());
            if (was == null) continue;
            seen++;
            if (Math.hypot(zombie.getX() - was[0], zombie.getZ() - was[1]) >= 3) moved++;
        }
        return seen == 0 ? 0 : moved / (double) seen;
    }

    private static boolean employed(Villager villager) {
        return !villager.getVillagerData().profession().is(VillagerProfession.NONE);
    }

    private static int items(ServerLevel level, net.minecraft.world.item.Item item) {
        return level.getEntitiesOfClass(ItemEntity.class, pens(), entity -> entity.getItem().is(item)).stream().mapToInt(entity -> entity.getItem().getCount()).sum();
    }

    private static int villagerBread(ServerLevel level) {
        return level.getEntitiesOfClass(Villager.class, pens()).stream().mapToInt(villager -> villager.getInventory().countItem(Items.BREAD)).sum();
    }

    private static void check(boolean ok, String what) {
        if (ok) { passed++; LOGGER.info("Lads async stress probe PASS: {}", what); }
        else fail(what);
    }

    private static void fail(String what) {
        FAILURES.add(what);
        LOGGER.warn("Lads async stress probe check failed: {}", what);
    }

    private static List<String> concat(List<String> a, List<String> b) {
        List<String> all = new ArrayList<>(a);
        b.forEach(failure -> all.add("FAILED: " + failure));
        return all;
    }
}
