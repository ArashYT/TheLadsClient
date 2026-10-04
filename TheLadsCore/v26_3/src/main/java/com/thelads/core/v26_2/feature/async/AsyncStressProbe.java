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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
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
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * QA only (auto-world, ".lads-qa-async" from the harness's LADS_VERIFY_ASYNC). Fences two pens next to the player and fills
 * them with ~1700 villagers, husks and cows, on a stone platform 24 blocks up (no water, terrain or natural spawns). Then,
 * once with Async off and once with it on: 30 s of server tick times and how far mobs wander (40 cows have a pen of their
 * own, uncrowded), followed by a 60 s soak with dropped items, bread, experience, loving cows, ten villagers fenced in next
 * to a free job site each and a cramming column. A last 30 s with Async off again shows what drift over time alone does. The on round must behave like the off round (items merge and none are lost or duplicated, cows breed,
 * villagers take job sites and pick up bread, cramming kills, mobs keep walking), peaceful must despawn every husk, and
 * Async must never fall back. Everything placed or spawned is removed and the settings are restored.
 */
final class AsyncStressProbe {
    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore");
    private static final String TAG = "lads_async";
    private static final long SECOND = 1_000_000_000L;
    private static final int VILLAGERS = 700, HUSKS = 700, COWS = 300, WALKERS = 40, PIGS = 40, PAIRS = 300, BREAD = 100, ORB_PAIRS = 100, LOVERS = 100, JOB_CELLS = 10;
    private static final List<Long> TICKS = new ArrayList<>();
    private static final List<String> FAILURES = new ArrayList<>(), REPORT = new ArrayList<>();
    /** Every block the probe changed and what was there before, restored at the end. */
    private static final Map<BlockPos, BlockState> ORIGINAL = new LinkedHashMap<>();
    private static final Set<UUID> EXISTING = new HashSet<>();
    /** Round 0 with Async off, round 1 with it on, round 2 off again (timing and walking only). */
    private static final Soak[] SOAKS = {new Soak(), new Soak(), new Soak()};
    private static int step = -1, passed, floor;
    private static long due, tickStart;
    private static boolean timing, enabledBefore, griefingBefore, spawningBefore;
    private static BlockPos center;
    private static GameType modeBefore;
    private static Difficulty difficultyBefore;
    private static Map<UUID, double[]> positions = new HashMap<>();

    private static final class Soak {
        double huskWalked, cowWalked;
        int jobs, carriedBefore, carriedAfter, breadGround, cobble, stacks, orbs, pigs;
        long calves;
        String timing;
    }

    private AsyncStressProbe() {}

    static void initialize() {
        if (!Boolean.getBoolean("thelads.verifyAutoWorld")) return;
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            try { tick(mc); } catch (Throwable failure) { fail("probe step " + step + ": " + failure); LOGGER.error("Lads async stress probe FAILED", failure); finish(); }
        });
        ServerTickEvents.START_SERVER_TICK.register(server -> tickStart = System.nanoTime());
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (!timing) return;
            synchronized (TICKS) { TICKS.add(System.nanoTime() - tickStart); }
        });
    }

    private static void tick(Minecraft mc) throws Exception {
        if (step < 0) {
            if (!NativeWorldVerification.worldReady() || mc.gui.screen() != null || mc.getSingleplayerServer() == null) return;
            Path request = FabricLoader.getInstance().getGameDir().resolve(".lads-qa-async");
            if (!Files.isRegularFile(request, LinkOption.NOFOLLOW_LINKS)) return;
            Files.delete(request);
            LOGGER.info("Lads async stress probe BEGIN: {} villagers, {} husks, {} cows on the integrated server, Async off then on", VILLAGERS, HUSKS, COWS);
            step = 0;
        }
        if (step > 9 || System.nanoTime() < due) return;
        MinecraftServer server = mc.getSingleplayerServer();
        AsyncModule module = (AsyncModule) ModuleManager.getInstance().getModule(AsyncModule.NAME);
        switch (step) {
            case 0 -> { // pens and mobs, Async off
                enabledBefore = module.isEnabled();
                module.setEnabled(false);
                onServer(server, () -> { build(server); return null; });
                next(20);
            }
            case 1, 4, 7 -> { // 30 s of tick times and wandering
                positions = onServer(server, () -> positions(server));
                startTiming();
                next(30);
            }
            case 2, 5 -> { // timing done: soak
                int round = step == 2 ? 0 : 1;
                Soak soak = SOAKS[round];
                soak.timing = stopTiming(round == 0 ? "Async off" : "Async on");
                onServer(server, () -> {
                    soak.huskWalked = walked(server, Zombie.class);
                    soak.cowWalked = walked(server, Cow.class);
                    soakSetup(server, round);
                    return null;
                });
                next(60);
            }
            case 3 -> { // off soak results, then Async on
                onServer(server, () -> { soakResults(server, SOAKS[0]); clearSoak(server); return null; });
                module.setEnabled(true);
                int cores = Runtime.getRuntime().availableProcessors();
                REPORT.add("cores " + cores + ", Async worker threads " + module.threads(cores) + ", min entities " + module.minEntities());
                AsyncTicking.PARALLEL_TICKS.set(0); AsyncTicking.PHASES.set(0); AsyncTicking.REGIONS.set(0); AsyncTicking.PARALLEL_ENTITIES.set(0);
                next(5);
            }
            case 6 -> { // on soak results, then Async off again
                onServer(server, () -> { soakResults(server, SOAKS[1]); clearSoak(server); return null; });
                module.setEnabled(false);
                next(5);
            }
            case 8 -> {
                Soak late = SOAKS[2];
                late.timing = stopTiming("Async off again");
                onServer(server, () -> {
                    late.huskWalked = walked(server, Zombie.class);
                    late.cowWalked = walked(server, Cow.class);
                    server.setDifficulty(Difficulty.PEACEFUL, true);
                    return null;
                });
                compare();
                // peaceful despawning runs with Async on
                module.setEnabled(true);
                next(4);
            }
            case 9 -> {
                onServer(server, () -> {
                    check(level(server).getEntitiesOfClass(Zombie.class, pens()).isEmpty(), "peaceful despawns every husk (checkDespawn on worker threads)");
                    return null;
                });
                check(AsyncTicking.fallback() == null, "no error or unsafe access in the whole run" + (AsyncTicking.fallback() == null ? "" : ": " + AsyncTicking.fallback()));
                finish();
            }
            default -> {}
        }
    }

    private static void compare() {
        Soak off = SOAKS[0], on = SOAKS[1], late = SOAKS[2];
        long ticks = AsyncTicking.PARALLEL_TICKS.get(), phases = AsyncTicking.PHASES.get();
        for (int round = 0; round < 3; round++) {
            Soak soak = SOAKS[round];
            REPORT.add(String.format(Locale.ROOT, "%s; walked 3+ blocks in 30 s: %.0f%% of husks (crowded pen), %.0f%% of cows (own pen)",
                soak.timing, soak.huskWalked * 100, soak.cowWalked * 100));
            if (round == 2) break;
            REPORT.add(String.format(Locale.ROOT, "  soak %s: cobblestone %d in %d stacks (from %d single items), %d orbs left of %d, %d pigs left of %d, %d calves, "
                + "%d of %d fenced villagers took their job site, bread carried %d -> %d (%d on the ground)", round == 0 ? "off" : "on", soak.cobble, soak.stacks,
                PAIRS * 2, soak.orbs, ORB_PAIRS * 2, soak.pigs, PIGS, soak.calves, soak.jobs, JOB_CELLS, soak.carriedBefore, soak.carriedAfter, soak.breadGround));
        }
        REPORT.add(String.format(Locale.ROOT, "Async on: %d parallel ticks, %.1f regions per phase, %.0f entities per parallel tick", ticks,
            phases == 0 ? 0.0 : AsyncTicking.REGIONS.get() / (double) phases, ticks == 0 ? 0.0 : AsyncTicking.PARALLEL_ENTITIES.get() / (double) ticks));
        check(ticks > 100 && phases > 0, "Async ticked in parallel (" + ticks + " parallel ticks)");
        double cowsOff = Math.min(off.cowWalked, late.cowWalked), husksOff = Math.min(off.huskWalked, late.huskWalked);
        check(on.cowWalked >= cowsOff * 0.7, String.format(Locale.ROOT, "cows keep wandering with Async on (%.0f%% on, %.0f%% and %.0f%% off)",
            on.cowWalked * 100, off.cowWalked * 100, late.cowWalked * 100));
        check(on.huskWalked >= husksOff * 0.5 || on.huskWalked > 0.3, String.format(Locale.ROOT, "husks keep walking with Async on (%.0f%% on, %.0f%% and %.0f%% off)",
            on.huskWalked * 100, off.huskWalked * 100, late.huskWalked * 100));
        for (Soak soak : SOAKS) check(soak.cobble == PAIRS * 2, "item merging keeps every cobblestone (" + PAIRS * 2 + " -> " + soak.cobble + ")");
        check(on.stacks <= PAIRS * 2 * 0.6 && on.stacks <= off.stacks * 1.2 + 10, "dropped items merge with Async on (" + off.stacks + " stacks off, " + on.stacks + " on)");
        check(on.orbs <= off.orbs * 1.2 + 10, "experience orbs merge with Async on (" + off.orbs + " orbs off, " + on.orbs + " on)");
        check(on.pigs <= 24, "entity cramming kills the pigs over 24 (" + PIGS + " -> " + on.pigs + ")");
        check(on.calves >= Math.max(5, off.calves / 2), "loving cows breed with Async on (" + off.calves + " calves off, " + on.calves + " on)");
        check(on.jobs >= off.jobs - 2 && (off.jobs == 0 || on.jobs > 0), "villagers take their job site with Async on (" + off.jobs + " of " + JOB_CELLS + " off, " + on.jobs + " on)");
        int pickedOff = off.carriedAfter - off.carriedBefore, pickedOn = on.carriedAfter - on.carriedBefore;
        check(pickedOn >= pickedOff / 2 && (pickedOff == 0 || pickedOn > 0), "villagers pick up bread with Async on (" + pickedOff + " off, " + pickedOn + " on)");
        for (Soak soak : SOAKS) check(soak.breadGround + soak.carriedAfter - soak.carriedBefore == BREAD, "bread is neither lost nor duplicated ("
            + soak.breadGround + " on the ground + " + (soak.carriedAfter - soak.carriedBefore) + " picked up of " + BREAD + ")");
    }

    private static void build(MinecraftServer server) {
        ServerLevel level = level(server);
        ServerPlayer player = server.getPlayerList().getPlayers().get(0);
        center = player.blockPosition();
        floor = center.getY() + 24; // the platform
        level.getEntitiesOfClass(Entity.class, pens().inflate(4)).forEach(entity -> EXISTING.add(entity.getUUID()));
        modeBefore = player.gameMode();
        difficultyBefore = level.getDifficulty();
        griefingBefore = server.getGameRules().get(GameRules.MOB_GRIEFING);
        spawningBefore = server.getGameRules().get(GameRules.SPAWN_MOBS);
        REPORT.add("world: " + (level.isBrightOutside() ? "day" : "night") + ", mob griefing was " + griefingBefore + ", difficulty was " + difficultyBefore);
        player.setGameMode(GameType.CREATIVE);
        server.setDifficulty(Difficulty.NORMAL, true);
        server.getGameRules().set(GameRules.MOB_GRIEFING, true, server); // mobs pick up items only with mob griefing
        server.getGameRules().set(GameRules.SPAWN_MOBS, false, server); // no night spawns on the platform
        for (int x = center.getX() - 45; x <= center.getX() + 45; x++) for (int z = center.getZ() - 41; z <= center.getZ() + 41; z++)
            place(level, new BlockPos(x, floor, z), Blocks.SMOOTH_STONE.defaultBlockState());
        // two pens of 36 x 80 blocks, 8 blocks either side of the player
        fence(level, center.getX() - 44, center.getX() - 8);
        fence(level, center.getX() + 8, center.getX() + 44);
        fence(level, center.getX() - 6, center.getX() + 6); // the walkers' own pen
        for (int i = 0; i < WALKERS; i++) summon(server, "cow", -5, 5, i * 5 + 2, "");
        for (int i = 0; i < VILLAGERS; i++) summon(server, "villager", -38, -10, i, "");
        for (int i = 0; i < COWS; i++) summon(server, "cow", -38, -10, i * 7 + 3, "");
        // husks: zombies that do not burn in daylight
        for (int i = 0; i < HUSKS; i++) summon(server, "husk", 10, 42, i, "");
        // a 1 x 1 barrier column for the cramming check
        BlockPos column = column(level);
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) for (int dy = 0; dy < 3; dy++)
            if (dx != 0 || dz != 0) place(level, column.offset(dx, dy, dz), Blocks.BARRIER.defaultBlockState());
    }

    private static void soakSetup(MinecraftServer server, int round) {
        ServerLevel level = level(server);
        Soak soak = SOAKS[round];
        soak.carriedBefore = carriedBread(level);
        // fresh unemployed villagers, each fenced in next to its own composter; the cells of round 1 sit between those of round 0
        for (int i = 0; i < JOB_CELLS; i++) {
            BlockPos spot = new BlockPos(center.getX() - 43, floor + 1, center.getZ() - 38 + 8 * i + 4 * round);
            for (int dx = -1; dx <= 2; dx++) for (int dz = -1; dz <= 1; dz++) for (int dy = 0; dy < 2; dy++)
                if (dz != 0 || dx < 0 || dx > 1) place(level, spot.offset(dx, dy, dz), Blocks.BARRIER.defaultBlockState());
            place(level, spot.east(), Blocks.COMPOSTER.defaultBlockState());
            command(server, String.format(Locale.ROOT, "summon villager %d %d %d {Tags:[\"%s\",\"%s_job%d\"]}", spot.getX(), spot.getY(), spot.getZ(), TAG, TAG, round));
        }
        for (int i = 0; i < PAIRS; i++) for (int copy = 0; copy < 2; copy++) summon(server, "item", -38, -12, i * 13 + 1, "Item:{id:\"minecraft:cobblestone\",count:1}");
        for (int i = 0; i < BREAD; i++) summon(server, "item", -38, -12, i * 17 + 5, "Item:{id:\"minecraft:bread\",count:1}");
        for (int i = 0; i < ORB_PAIRS; i++) for (int copy = 0; copy < 2; copy++) summon(server, "experience_orb", -38, -24, i * 11 + 2, "Value:3");
        for (int i = 0; i < LOVERS; i++) summon(server, "cow", -38, -10, i * 9 + 4, "InLove:600");
        BlockPos column = column(level);
        for (int i = 0; i < PIGS; i++) command(server, String.format(Locale.ROOT, "summon pig %d %d %d {Tags:[\"%s\"]}", column.getX(), column.getY(), column.getZ(), TAG));
    }

    private static void soakResults(MinecraftServer server, Soak soak) {
        ServerLevel level = level(server);
        soak.cobble = items(level, Items.COBBLESTONE);
        soak.stacks = level.getEntitiesOfClass(ItemEntity.class, pens(), item -> item.getItem().is(Items.COBBLESTONE)).size();
        soak.orbs = level.getEntitiesOfClass(ExperienceOrb.class, pens(), orb -> orb.entityTags().contains(TAG)).size();
        soak.pigs = level.getEntitiesOfClass(Pig.class, pens()).size();
        soak.calves = level.getEntitiesOfClass(Cow.class, pens(), Cow::isBaby).size();
        String jobTag = TAG + "_job" + (soak == SOAKS[0] ? 0 : 1);
        soak.jobs = level.getEntitiesOfClass(Villager.class, pens(), villager -> villager.entityTags().contains(jobTag)
            && !villager.getVillagerData().profession().is(VillagerProfession.NONE)).size();
        soak.carriedAfter = carriedBread(level);
        soak.breadGround = items(level, Items.BREAD);
    }

    /** Between the rounds: the soak's items, orbs, pigs, calves and bred cows go; job sites stay (their villagers keep them). */
    private static void clearSoak(MinecraftServer server) {
        ServerLevel level = level(server);
        for (Entity entity : level.getEntitiesOfClass(Entity.class, pens(), entity -> entity instanceof ItemEntity || entity instanceof ExperienceOrb
            || entity instanceof Pig || entity instanceof Cow cow && (cow.isBaby() || cow.isInLove() || cow.getAge() > 0))) entity.discard();
    }

    private static void finish() {
        if (step == 99) return;
        step = 99;
        timing = false;
        MinecraftServer server = Minecraft.getInstance().getSingleplayerServer();
        Module module = ModuleManager.getInstance().getModule(AsyncModule.NAME);
        if (module != null) module.setEnabled(enabledBefore);
        if (server != null && center != null) {
            try {
                onServer(server, () -> {
                    ServerLevel level = level(server);
                    for (Entity entity : level.getEntitiesOfClass(Entity.class, pens().inflate(4), entity -> !(entity instanceof Player) && !EXISTING.contains(entity.getUUID())))
                        entity.discard();
                    List<Map.Entry<BlockPos, BlockState>> changed = new ArrayList<>(ORIGINAL.entrySet());
                    for (int i = changed.size() - 1; i >= 0; i--) level.setBlock(changed.get(i).getKey(), changed.get(i).getValue(), 2);
                    if (difficultyBefore != null) server.setDifficulty(difficultyBefore, true);
                    server.getGameRules().set(GameRules.MOB_GRIEFING, griefingBefore, server);
                    server.getGameRules().set(GameRules.SPAWN_MOBS, spawningBefore, server);
                    if (modeBefore != null) server.getPlayerList().getPlayers().forEach(player -> player.setGameMode(modeBefore));
                    return null;
                });
            } catch (Exception failure) { fail("cleanup: " + failure); }
        }
        REPORT.forEach(line -> LOGGER.info("Lads async stress probe: {}", line));
        List<String> lines = new ArrayList<>(REPORT);
        FAILURES.forEach(failure -> lines.add("FAILED: " + failure));
        try { Files.write(FabricLoader.getInstance().getGameDir().resolve("async-stress.txt"), lines); } catch (Exception ignored) {}
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
        return String.format(Locale.ROOT, "%s: %d server ticks, mean %.1f ms, p95 %.1f ms", label, ticks.size(), mean, p95);
    }

    private static <T> T onServer(MinecraftServer server, Supplier<T> task) { return server.submit(task).join(); }

    private static ServerLevel level(MinecraftServer server) { return server.getPlayerList().getPlayers().get(0).level(); }

    private static AABB pens() { return new AABB(center.getX() - 46, center.getY() - 8, center.getZ() - 42, center.getX() + 46, floor + 16, center.getZ() + 42); }

    private static BlockPos column(ServerLevel level) { return new BlockPos(center.getX() + 26, floor + 1, center.getZ() + 30); }

    private static void fence(ServerLevel level, int x0, int x1) {
        int z0 = center.getZ() - 40, z1 = center.getZ() + 40;
        for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) {
            if (x != x0 && x != x1 && z != z0 && z != z1) continue;
            for (int dy = 1; dy <= 3; dy++) place(level, new BlockPos(x, floor + dy, z), Blocks.BARRIER.defaultBlockState());
        }
    }

    private static void place(ServerLevel level, BlockPos pos, BlockState state) {
        BlockState before = level.getBlockState(pos);
        if (!before.canBeReplaced()) return;
        ORIGINAL.putIfAbsent(pos.immutable(), before);
        level.setBlock(pos, state, 2);
    }

    /** Spreads entity i over a pen spanning dx0..dx1 blocks east of the player and 76 blocks north to south. */
    private static void summon(MinecraftServer server, String type, int dx0, int dx1, int i, String nbt) {
        int width = dx1 - dx0 + 1;
        int x = center.getX() + dx0 + Math.floorMod(i * 7, width), z = center.getZ() - 38 + Math.floorMod(i * 11 / width + i * 3, 77);
        command(server, String.format(Locale.ROOT, "summon %s %d %d %d {Tags:[\"%s\"]%s}", type, x, floor + 1, z, TAG, nbt.isEmpty() ? "" : "," + nbt));
    }

    private static void command(MinecraftServer server, String command) {
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), command);
    }

    private static Map<UUID, double[]> positions(MinecraftServer server) {
        Map<UUID, double[]> map = new HashMap<>();
        for (Mob mob : level(server).getEntitiesOfClass(Mob.class, pens())) map.put(mob.getUUID(), new double[]{mob.getX(), mob.getZ()});
        return map;
    }

    private static double walked(MinecraftServer server, Class<? extends Mob> type) {
        int moved = 0, seen = 0;
        AABB area = type == Cow.class ? pens().setMinX(center.getX() - 6).setMaxX(center.getX() + 7) : pens();
        for (Mob mob : level(server).getEntitiesOfClass(type, area)) {
            double[] was = positions.get(mob.getUUID());
            if (was == null) continue;
            seen++;
            if (Math.hypot(mob.getX() - was[0], mob.getZ() - was[1]) >= 3) moved++;
        }
        return seen == 0 ? 0 : moved / (double) seen;
    }

    private static int items(ServerLevel level, Item item) {
        return level.getEntitiesOfClass(ItemEntity.class, pens(), entity -> entity.getItem().is(item)).stream().mapToInt(entity -> entity.getItem().getCount()).sum();
    }

    private static int carriedBread(ServerLevel level) {
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
}
