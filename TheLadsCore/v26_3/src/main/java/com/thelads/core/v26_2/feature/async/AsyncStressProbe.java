package com.thelads.core.v26_2.feature.async;

import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.modules.AsyncModule;
import com.thelads.core.modules.ItemPhysicsModule;
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
import java.util.function.Predicate;
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
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.chicken.Chicken;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.decoration.LeashFenceKnotEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PressurePlateBlock;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * QA only (auto-world, ".lads-qa-async" from the harness's LADS_VERIFY_ASYNC). Builds a stone platform 24 blocks above the player
 * (no terrain or natural spawns) with barrier pens: ~1750 villagers, husks and cows crowd two of them; a third holds 40 cows that
 * must wander and 40 villagers that walk around, all within 32 blocks of the player (vanilla stops mobs farther than that from
 * every player strolling: on 26.2 only non-persistent ones, on 26.3 all of them), and 20 non-persistent cows farther away that
 * show that rule. Strips north and south hold fights (husk vs
 * villager), skeletons shooting iron golems, cows on pressure plates next to redstone lamps, leashed cows, chicken jockeys,
 * non-persistent husks that despawn at vanilla's random rate, and Item Physics' water pool and ignite pads. Three rounds follow,
 * Async off, on, off: 30 s of server tick times and walking, then a 60 s soak with dropped items, bread for the villagers to
 * pick up, experience orbs, loving cows, villagers fenced next to a composter and a cramming column. The on round is compared
 * with both off rounds, so drift over time is not mistaken for Async. Then Async runs on for two more minutes, peaceful must
 * despawn every husk, and Async must never have fallen back. Last, worker threads are made to wait 5 ms per phase (simulated
 * outside CPU load): Async's self-check must switch to normal ticking, after which ticks are no slower than with Async off. Everything placed or spawned is removed and settings restored.
 */
final class AsyncStressProbe {
    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore");
    private static final String TAG = "lads_async", KEEP = "PersistenceRequired:1b";
    private static final long SECOND = 1_000_000_000L;
    private static final int VILLAGERS = 700, HUSKS = 700, COWS = 300, WALKERS = 40, PIGS = 40, PAIRS = 300, BREAD = 100, ORB_PAIRS = 100,
        LOVERS = 100, JOB_CELLS = 16, FIGHTS = 12, SHOOTERS = 6, PLATES = 8, LEASHED = 8, JOCKEYS = 8, DESPAWNERS = 60, PADS = 4, ENDURANCE = 120;
    /** One of each, so no two can merge into one stack. */
    private static final Item[] FLOATERS = {Items.OAK_PLANKS, Items.STICK, Items.OAK_LOG, Items.OAK_SLAB},
        SINKERS = {Items.IRON_INGOT, Items.STONE, Items.DIAMOND, Items.GOLD_INGOT};
    private static final boolean[] ASYNC = {false, true, false};
    private static final List<Long> TICKS = new ArrayList<>();
    private static final List<String> FAILURES = new ArrayList<>(), REPORT = new ArrayList<>();
    /** Every block the probe changed and what was there before, restored at the end. */
    private static final Map<BlockPos, BlockState> ORIGINAL = new LinkedHashMap<>();
    private static final Set<UUID> EXISTING = new HashSet<>(), NEAR = new HashSet<>();
    private static final List<UUID> FLOATING = new ArrayList<>(), SINKING = new ArrayList<>(), GOLEMS = new ArrayList<>();
    private static final Round[] ROUNDS = {new Round(), new Round(), new Round()};
    private static final Round LONG = new Round(), STALLED = new Round();
    private static final long STALL_NANOS = 5_000_000;
    /** Server ticks from the simulated load's start until the self-check benched parallel ticking (-1: not yet). */
    private static volatile int stallTicks = -1, benchedAfter = -1;
    private static int step = -1, passed, floor;
    private static long due, tickStart;
    private static boolean timing, enabledBefore, physicsBefore, griefingBefore, spawningBefore;
    private static BlockPos center;
    private static GameType modeBefore;
    private static Difficulty difficultyBefore;
    private static Map<UUID, double[]> positions = new HashMap<>();
    /** The round whose walkers are being followed (server thread samples them every 10 ticks), and where each was last seen. */
    private static volatile Round sampled;
    private static final Map<UUID, double[]> LAST = new HashMap<>();

    private static final class Round {
        double cowWalked, villagerWalked, nearWalked, farWalked, mspt, p95, entityMs;
        long benched, resumed; // Async self-check switches during the round
        /** Walkers in the middle pen, persistent cows [0] and villagers [1]: blocks walked, samples, samples on the move. */
        final double[] path = new double[2];
        final int[] samples = new int[2], moving = new int[2];
        final List<String> misplaced = new ArrayList<>(); // Item Physics items not where they belong, with their height above the floor

        double distance(int group) { return path[group] / Math.max(1, samples[group]) * 60; } // blocks per mob in the 30 s window (60 samples)
        double movingShare(int group) { return moving[group] / (double) Math.max(1, samples[group]); }
        int near, far, farIdle, ticks, jobs, carriedBefore, carriedAfter, breadGround, cobble, stacks, orbs, pigs, attacked, shot, lit, leashed,
            riding, despawned, floated, sank, ignited;
        long calves;
    }

    private AsyncStressProbe() {}

    static void initialize() {
        if (!Boolean.getBoolean("thelads.verifyAutoWorld")) return;
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            try { tick(mc); } catch (Throwable failure) { fail("probe step " + step + ": " + failure); LOGGER.error("Lads async stress probe FAILED", failure); finish(); }
        });
        ServerTickEvents.START_SERVER_TICK.register(server -> tickStart = System.nanoTime());
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (stallTicks >= 0) {
                stallTicks++;
                if (benchedAfter < 0 && AsyncTicking.benched(level(server))) benchedAfter = stallTicks;
            }
            if (!timing) return;
            synchronized (TICKS) { TICKS.add(System.nanoTime() - tickStart); }
            Round round = sampled;
            if (round != null && server.getTickCount() % 10 == 0) follow(server, round);
        });
    }

    /** Step 0 builds; round r takes steps 1 + 3r (measure), 2 + 3r (soak), 3 + 3r (results); 10 and 11 the long run; 11 to 13 the simulated load; 14 ends. */
    private static void tick(Minecraft mc) throws Exception {
        if (step < 0) {
            if (!NativeWorldVerification.worldReady() || mc.gui.screen() != null || mc.getSingleplayerServer() == null) return;
            Path request = FabricLoader.getInstance().getGameDir().resolve(".lads-qa-async");
            if (!Files.isRegularFile(request, LinkOption.NOFOLLOW_LINKS)) return;
            Files.delete(request);
            LOGGER.info("Lads async stress probe BEGIN: {} villagers, {} husks, {} cows on the integrated server, Async off, on, off", VILLAGERS, HUSKS, COWS);
            step = 0;
        }
        if (step > 14 || System.nanoTime() < due) return;
        MinecraftServer server = mc.getSingleplayerServer();
        AsyncModule module = (AsyncModule) ModuleManager.getInstance().getModule(AsyncModule.NAME);
        if (step == 0) {
            enabledBefore = module.isEnabled();
            module.setEnabled(false);
            Module physics = ModuleManager.getInstance().getModule(ItemPhysicsModule.NAME);
            physicsBefore = physics.isEnabled();
            physics.setEnabled(true); // its item rules run in the item tick, on worker threads with Async on
            int cores = AsyncTicking.usableCores();
            REPORT.add("cores " + Runtime.getRuntime().availableProcessors() + ", this game may use " + cores + ", Async worker threads " + module.threads(cores)
                + ", min entities " + module.minEntities());
            onServer(server, () -> { build(server); return null; });
            next(20);
            return;
        }
        if (step == 10) { // two more minutes with Async on and everything still on the platform
            module.setEnabled(true);
            AsyncTicking.LOOP_NANOS.set(0);
            LONG.benched = AsyncTicking.BENCHED.get();
            LONG.resumed = AsyncTicking.RESUMED.get();
            startTiming();
            next(ENDURANCE);
            return;
        }
        if (step == 11) {
            stopTiming(LONG);
            LONG.benched = AsyncTicking.BENCHED.get() - LONG.benched;
            LONG.resumed = AsyncTicking.RESUMED.get() - LONG.resumed;
            boolean benchedNow = onServer(server, () -> AsyncTicking.benched(level(server)));
            REPORT.add(String.format(Locale.ROOT, "Async on for %d s more: %d server ticks, mean %.1f ms (entity loop %.1f ms), p95 %.1f ms, %d entities on the platform;"
                + " self-check: benched %d, back on %d, %s at the end", ENDURANCE, LONG.ticks, LONG.mspt, LONG.entityMs, LONG.p95,
                onServer(server, () -> level(server).getEntitiesOfClass(Entity.class, platform()).size()), LONG.benched, LONG.resumed,
                benchedNow ? "ticking normally" : "ticking in parallel"));
            REPORT.add("self-check in the whole run: parallel ticking benched " + AsyncTicking.BENCHED.get() + " times, back on " + AsyncTicking.RESUMED.get() + " times");
            check(LONG.ticks > ENDURANCE * 10, "the server keeps ticking with Async on for " + ENDURANCE + " s (" + LONG.ticks + " ticks)");
            double offMspt = (ROUNDS[0].mspt + ROUNDS[2].mspt) / 2;
            check(LONG.mspt <= offMspt * 1.15, String.format(Locale.ROOT, "server tick time is no worse with Async on for %d s more (%.1f ms, %.1f and %.1f ms off)",
                ENDURANCE, LONG.mspt, ROUNDS[0].mspt, ROUNDS[2].mspt));
            // simulated outside load: wait for the self-check (it samples every 30 s and needs two slower samples)
            AsyncTicking.qaStallNanos = STALL_NANOS;
            STALLED.benched = AsyncTicking.BENCHED.get();
            stallTicks = 0;
            next(60);
            return;
        }
        if (step == 12) { // then 30 s of tick times with Async still on, the workers still held back
            STALLED.benched = AsyncTicking.BENCHED.get() - STALLED.benched;
            check(STALLED.benched >= 1 && benchedAfter > 0, "Async's self-check switches to normal ticking when parallel ticking is slower (workers held back "
                + STALL_NANOS / 1_000_000 + " ms per phase; benched after " + benchedAfter + " ticks)");
            AsyncTicking.LOOP_NANOS.set(0);
            startTiming();
            next(30);
            return;
        }
        if (step == 13) {
            stopTiming(STALLED);
            AsyncTicking.qaStallNanos = 0;
            stallTicks = -1;
            double offMspt = (ROUNDS[0].mspt + ROUNDS[2].mspt) / 2;
            REPORT.add(String.format(Locale.ROOT, "simulated outside load (workers held back %d ms per phase): self-check benched parallel ticking after %d ticks; "
                + "the next 30 s: %d server ticks, mean %.1f ms (entity loop %.1f ms), p95 %.1f ms", STALL_NANOS / 1_000_000, benchedAfter, STALLED.ticks, STALLED.mspt,
                STALLED.entityMs, STALLED.p95));
            check(STALLED.mspt <= offMspt * 1.15, String.format(Locale.ROOT, "with the self-check ticking normally under that load, ticks are no slower than Async off "
                + "(%.1f ms, %.1f and %.1f ms off)", STALLED.mspt, ROUNDS[0].mspt, ROUNDS[2].mspt));
            onServer(server, () -> { server.setDifficulty(Difficulty.PEACEFUL, true); return null; }); // peaceful despawning with Async on
            next(4);
            return;
        }
        if (step == 14) {
            onServer(server, () -> {
                check(level(server).getEntitiesOfClass(Zombie.class, platform()).isEmpty(), "peaceful despawns every husk (checkDespawn on worker threads)");
                return null;
            });
            check(AsyncTicking.fallback() == null, "no error or unsafe access in the whole run" + (AsyncTicking.fallback() == null ? "" : ": " + AsyncTicking.fallback()));
            finish();
            return;
        }
        int r = (step - 1) / 3;
        Round round = ROUNDS[r];
        switch ((step - 1) % 3) {
            case 0 -> { // fresh behaviour subjects, then 30 s of tick times and walking
                module.setEnabled(ASYNC[r]);
                if (ASYNC[r]) { AsyncTicking.PARALLEL_TICKS.set(0); AsyncTicking.PHASES.set(0); AsyncTicking.REGIONS.set(0); AsyncTicking.PARALLEL_ENTITIES.set(0); }
                round.benched = AsyncTicking.BENCHED.get();
                round.resumed = AsyncTicking.RESUMED.get();
                positions = onServer(server, () -> { setupRound(server, r); return positions(server); });
                AsyncTicking.LOOP_NANOS.set(0);
                LAST.clear();
                sampled = round;
                startTiming();
                next(30);
            }
            case 1 -> { // timing done: soak
                stopTiming(round);
                sampled = null;
                onServer(server, () -> { walked(server, round); soakSetup(server, r); return null; });
                next(60);
            }
            default -> { // soak and behaviour results
                onServer(server, () -> { soakResults(server, r); roundResults(server, r); clearSoak(server); return null; });
                round.benched = AsyncTicking.BENCHED.get() - round.benched;
                round.resumed = AsyncTicking.RESUMED.get() - round.resumed;
                if (r == 2) compare();
                next(5);
            }
        }
    }

    private static void compare() {
        Round off = ROUNDS[0], on = ROUNDS[1], late = ROUNDS[2];
        long ticks = AsyncTicking.PARALLEL_TICKS.get(), phases = AsyncTicking.PHASES.get();
        for (int r = 0; r < 3; r++) {
            Round round = ROUNDS[r];
            REPORT.add(String.format(Locale.ROOT, "round %d, Async %s: %d server ticks, mean %.1f ms (entity loop %.1f ms), p95 %.1f ms%s", r + 1,
                ASYNC[r] ? "on" : "off", round.ticks, round.mspt, round.entityMs, round.p95,
                ASYNC[r] ? "; self-check: benched " + round.benched + ", back on " + round.resumed : ""));
            REPORT.add(String.format(Locale.ROOT, "  in 30 s persistent cows walked %.1f blocks each (on the move in %.0f%% of half-second samples, %.0f%% got 3+ blocks"
                + " from their start), villagers %.1f blocks (%.0f%%, %.0f%%); non-persistent cows got 3+ blocks away: %.0f%% of %d within 32 blocks of the player,"
                + " %.0f%% of %d farther (%d of those idle by vanilla's rule, no-action time 100+)", round.distance(0), round.movingShare(0) * 100, round.cowWalked * 100,
                round.distance(1), round.movingShare(1) * 100, round.villagerWalked * 100, round.nearWalked * 100, round.near, round.farWalked * 100, round.far, round.farIdle));
            REPORT.add(String.format(Locale.ROOT, "  soak: cobblestone %d in %d stacks (from %d single items), %d orbs left of %d, %d pigs left of %d, %d calves, "
                + "%d of %d fenced villagers took their composter, bread carried %d -> %d (%d on the ground)", round.cobble, round.stacks, PAIRS * 2,
                round.orbs, ORB_PAIRS * 2, round.pigs, PIGS, round.calves, round.jobs, JOB_CELLS, round.carriedBefore, round.carriedAfter, round.breadGround));
            REPORT.add(String.format(Locale.ROOT, "  behaviour: %d of %d fenced husks attacked their villager, %d of %d golems hit by skeleton arrows, %d of %d cows pressed "
                + "a plate that lit its lamp, %d of %d cows still leashed, %d of %d chicken jockeys still riding, %d of %d far husks despawned; Item Physics: %d of %d "
                + "light items float, %d of %d heavy items sink, %d of %d burning sticks lit their planks", round.attacked, FIGHTS, round.shot, SHOOTERS,
                round.lit, PLATES, round.leashed, LEASHED, round.riding, JOCKEYS, round.despawned, DESPAWNERS, round.floated, FLOATERS.length,
                round.sank, SINKERS.length, round.ignited, PADS) + (round.misplaced.isEmpty() ? "" : " " + round.misplaced));
        }
        REPORT.add(String.format(Locale.ROOT, "Async on: %d parallel ticks, %.1f regions per phase, %.0f entities per parallel tick", ticks,
            phases == 0 ? 0.0 : AsyncTicking.REGIONS.get() / (double) phases, ticks == 0 ? 0.0 : AsyncTicking.PARALLEL_ENTITIES.get() / (double) ticks));
        check(ticks > 100 && phases > 0, "Async ticked in parallel (" + ticks + " parallel ticks)");
        double offMspt = (off.mspt + late.mspt) / 2;
        check(on.mspt <= offMspt * 1.15, String.format(Locale.ROOT, "server tick time is no worse with Async on (%.1f ms on, %.1f and %.1f ms off)", on.mspt, off.mspt, late.mspt));
        // distance walked, not "got 3+ blocks from the start": that share swung 63-86 % between two off rounds of the same 40 cows
        check(on.distance(0) >= 0.75 * Math.min(off.distance(0), late.distance(0)) && on.distance(0) > 3, String.format(Locale.ROOT,
            "cows keep wandering with Async on (%.1f blocks each on, %.1f and %.1f off)", on.distance(0), off.distance(0), late.distance(0)));
        // idle villagers stroll little (2-5 blocks in 30 s, falling round to round), so only a collapse fails; their pathing is also
        // checked by the job site cells and the bread they walk to
        check(on.distance(1) >= 0.5 * Math.min(off.distance(1), late.distance(1)) && on.distance(1) > 0.5, String.format(Locale.ROOT,
            "villagers keep walking (pathfinding) with Async on (%.1f blocks each on, %.1f and %.1f off)", on.distance(1), off.distance(1), late.distance(1)));
        for (Round round : ROUNDS) check(round.cobble == PAIRS * 2, "item merging keeps every cobblestone (" + PAIRS * 2 + " -> " + round.cobble + ")");
        check(on.stacks <= PAIRS * 2 * 0.6 && on.stacks <= Math.max(off.stacks, late.stacks) * 1.2 + 10, "dropped items merge with Async on ("
            + off.stacks + " and " + late.stacks + " stacks off, " + on.stacks + " on)");
        check(on.orbs <= Math.max(off.orbs, late.orbs) * 1.2 + 10, "experience orbs merge with Async on (" + off.orbs + " and " + late.orbs + " orbs off, " + on.orbs + " on)");
        check(on.pigs <= 24, "entity cramming kills the pigs over 24 (" + PIGS + " -> " + on.pigs + ")");
        check(on.calves >= Math.max(5, Math.min(off.calves, late.calves) / 2), "loving cows breed with Async on (" + off.calves + " and " + late.calves + " calves off, " + on.calves + " on)");
        check(on.jobs >= Math.min(off.jobs, late.jobs) - 3 && (Math.max(off.jobs, late.jobs) == 0 || on.jobs > 0), "fenced villagers take their composter with Async on ("
            + off.jobs + " and " + late.jobs + " of " + JOB_CELLS + " off, " + on.jobs + " on)");
        // villagers also eat bread they carry (Villager.eatAndDigestFood), so bread can only go missing, never appear
        for (Round round : ROUNDS) check(round.breadGround + round.carriedAfter - round.carriedBefore <= BREAD, "bread is not duplicated ("
            + round.breadGround + " on the ground + " + (round.carriedAfter - round.carriedBefore) + " picked up of " + BREAD + ")");
        int pickedOn = on.carriedAfter - on.carriedBefore, pickedOff = Math.min(off.carriedAfter - off.carriedBefore, late.carriedAfter - late.carriedBefore);
        check(pickedOn >= BREAD / 2 && pickedOn >= pickedOff - 15, "villagers pick up bread with Async on (" + (off.carriedAfter - off.carriedBefore) + " and "
            + (late.carriedAfter - late.carriedBefore) + " of " + BREAD + " off, " + pickedOn + " on)");
        check(on.attacked >= Math.min(off.attacked, late.attacked) - 2 && on.attacked >= FIGHTS / 2, "husks find and attack villagers with Async on ("
            + off.attacked + " and " + late.attacked + " of " + FIGHTS + " off, " + on.attacked + " on)");
        check(on.shot >= Math.min(off.shot, late.shot) - 1 && on.shot > 0, "skeleton arrows (projectiles from worker threads) hit golems with Async on ("
            + off.shot + " and " + late.shot + " of " + SHOOTERS + " off, " + on.shot + " on)");
        for (int r = 0; r < 3; r++) check(ROUNDS[r].lit == PLATES, "round " + (r + 1) + ": cows press pressure plates that light redstone lamps ("
            + ROUNDS[r].lit + " of " + PLATES + ")");
        for (int r = 0; r < 3; r++) check(ROUNDS[r].leashed >= LEASHED - 1 && ROUNDS[r].riding >= JOCKEYS - 1, "round " + (r + 1) + ": leashed cows stay leashed ("
            + ROUNDS[r].leashed + " of " + LEASHED + ") and chicken jockeys keep their rider (" + ROUNDS[r].riding + " of " + JOCKEYS + ")");
        check(on.despawned >= Math.min(off.despawned, late.despawned) - 12 && on.despawned <= Math.max(off.despawned, late.despawned) + 12 && on.despawned >= 10,
            "far husks despawn at vanilla's random rate with Async on (" + off.despawned + " and " + late.despawned + " of " + DESPAWNERS + " off, " + on.despawned + " on)");
        for (int r = 0; r < 3; r++) check(ROUNDS[r].floated == FLOATERS.length && ROUNDS[r].sank == SINKERS.length && ROUNDS[r].ignited == PADS,
            "round " + (r + 1) + ": Item Physics in the item tick: wood floats (" + ROUNDS[r].floated + "), iron and stone sink (" + ROUNDS[r].sank
            + "), burning sticks set planks alight (" + ROUNDS[r].ignited + " of " + PADS + ")");
    }

    private static void build(MinecraftServer server) {
        ServerLevel level = level(server);
        ServerPlayer player = server.getPlayerList().getPlayers().get(0);
        center = player.blockPosition();
        floor = center.getY() + 24; // the platform
        level.getEntitiesOfClass(Entity.class, platform()).forEach(entity -> EXISTING.add(entity.getUUID()));
        modeBefore = player.gameMode();
        difficultyBefore = level.getDifficulty();
        griefingBefore = server.getGameRules().get(GameRules.MOB_GRIEFING);
        spawningBefore = server.getGameRules().get(GameRules.SPAWN_MOBS);
        REPORT.add("world: " + (level.isBrightOutside() ? "day" : "night") + ", mob griefing was " + griefingBefore + ", difficulty was " + difficultyBefore
            + "; probe sets creative, normal difficulty, mob griefing on, mob spawning off, noon with the clock paused, Item Physics on");
        player.setGameMode(GameType.CREATIVE);
        server.setDifficulty(Difficulty.NORMAL, true);
        server.getGameRules().set(GameRules.MOB_GRIEFING, true, server); // mobs pick up items only with mob griefing
        server.getGameRules().set(GameRules.SPAWN_MOBS, false, server); // no natural spawns on the platform
        command(server, "time set 6000"); // villagers keep their day schedule (they idle at night) and every round sees the same light
        command(server, "time pause");
        for (int x = -45; x <= 45; x++) for (int z = -53; z <= 53; z++) place(level, at(x, 0, z), Blocks.SMOOTH_STONE.defaultBlockState());
        // three pens of 81 blocks north to south, 8 blocks either side of the player; the middle one split in a cow and a villager half
        box(level, -44, -40, -8, 40, 3);
        box(level, 8, -40, 44, 40, 3);
        box(level, -6, -40, 6, 40, 3);
        // compartments z -39..-19 (far), -17..-1 and 1..17 (within 32 blocks of the player 24 blocks below), 19..39 (empty)
        for (int x = -6; x <= 6; x++) for (int z : new int[]{-18, 0, 18}) wall(level, x, z, 3);
        for (int i = 0; i < WALKERS; i++) {
            summonAt(server, "cow", spread(i, -5, 5, -17, -1), "walk", KEEP);
            if (i % 2 == 0) summonAt(server, "cow", spread(i, -5, 5, -39, -19), "idle", "");
            summonAt(server, "villager", spread(i, -5, 5, 1, 17), "vwalk", "");
        }
        for (int i = 0; i < VILLAGERS; i++) summonAt(server, "villager", spread(i, -34, -10, -38, 38), "crowd", "CanPickUpLoot:1b");
        for (int i = 0; i < COWS; i++) summonAt(server, "cow", spread(i * 7 + 3, -34, -10, -38, 38), "crowd", KEEP);
        // husks: zombies that do not burn in daylight; persistent, so the crowd stays the same size in every round
        for (int i = 0; i < HUSKS; i++) summonAt(server, "husk", spread(i, 10, 42, -38, 38), "crowd", KEEP);
        BlockPos column = column(); // a 1 x 1 barrier column for the cramming check
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) for (int dy = 0; dy < 3; dy++)
            if (dx != 0 || dz != 0) place(level, column.offset(dx, dy, dz), Blocks.BARRIER.defaultBlockState());
        // south strip: fight cells, skeleton corridors (roofed: skeletons burn in daylight), plate holes, the far box
        for (int k = 0; k < FIGHTS; k++) { BlockPos c = fight(k); box(level, c.getX() - center.getX() - 1, c.getZ() - center.getZ() - 1, c.getX() - center.getX() + 3, c.getZ() - center.getZ() + 1, 2); }
        for (int k = 0; k < SHOOTERS; k++) {
            int x0 = shooter(k).getX() - center.getX(), z0 = shooter(k).getZ() - center.getZ();
            box(level, x0 - 1, z0 - 2, x0 + 7, z0 + 2, 3);
            for (int x = x0 - 1; x <= x0 + 7; x++) for (int z = z0 - 2; z <= z0 + 2; z++) place(level, at(x, 4, z), Blocks.SMOOTH_STONE.defaultBlockState());
        }
        for (int r = 0; r < 3; r++) for (int k = 0; k < PLATES; k++) {
            BlockPos hole = hole(r, k);
            for (BlockPos wall : new BlockPos[]{hole.west(), hole.north(), hole.south(), hole.east().above()})
                for (int dy = 0; dy < 2; dy++) place(level, wall.above(dy), Blocks.BARRIER.defaultBlockState());
            place(level, hole, Blocks.STONE_PRESSURE_PLATE.defaultBlockState());
            place(level, hole.east(), Blocks.REDSTONE_LAMP.defaultBlockState());
        }
        box(level, 15, 42, 45, 49, 2);
        for (int k = 0; k < LEASHED; k++) place(level, post(k), Blocks.OAK_FENCE.defaultBlockState());
        // north strip: Item Physics' water pool (two deep) and ignite pads
        box(level, -3, -50, 3, -46, 3);
        for (int x = -2; x <= 2; x++) for (int z = -49; z <= -47; z++) for (int dy = 1; dy <= 2; dy++) // even where a tree reaches up
            replace(level, at(x, dy, z), Blocks.WATER.defaultBlockState());
        for (int r = 0; r < 3; r++) for (int k = 0; k < PADS; k++) {
            place(level, pad(r, k), Blocks.OAK_PLANKS.defaultBlockState());
            place(level, pad(r, k).above(), Blocks.AIR.defaultBlockState()); // remembered, so the fire it may hold is cleared at the end
        }
    }

    /** Round start: the previous round's subjects go, fresh ones arrive. */
    private static void setupRound(MinecraftServer server, int r) {
        ServerLevel level = level(server);
        for (Entity entity : level.getEntitiesOfClass(Entity.class, strips(), entity -> !(entity instanceof Player) && !(entity instanceof LeashFenceKnotEntity)
            && !EXISTING.contains(entity.getUUID()))) entity.discard();
        level.getEntitiesOfClass(ItemEntity.class, north()).forEach(Entity::discard);
        for (int k = 0; k < FIGHTS; k++) { summonAt(server, "villager", fight(k), "fight", ""); summonAt(server, "husk", fight(k).east(2), "fight", KEEP); }
        GOLEMS.clear();
        for (int k = 0; k < SHOOTERS; k++) {
            AbstractSkeleton skeleton = EntityTypes.SKELETON.create(level, EntitySpawnReason.COMMAND); // summoned with NBT it would carry no bow
            skeleton.snapTo(shooter(k).getX() + 0.5, floor + 1, shooter(k).getZ() + 0.5, -90, 0);
            skeleton.setPersistenceRequired();
            skeleton.addTag(TAG);
            skeleton.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
            level.addFreshEntity(skeleton);
            IronGolem golem = EntityTypes.IRON_GOLEM.create(level, EntitySpawnReason.COMMAND); // a still target: no AI, so it never hits back
            golem.snapTo(shooter(k).getX() + 5.5, floor + 1, shooter(k).getZ() + 0.5, 90, 0); // 1.4 wide: clear of the end wall
            golem.setNoAi(true);
            golem.setPersistenceRequired();
            golem.addTag(TAG);
            level.addFreshEntity(golem);
            GOLEMS.add(golem.getUUID());
        }
        for (int k = 0; k < PLATES; k++) summonAt(server, "cow", hole(r, k), "plate", KEEP);
        for (int k = 0; k < LEASHED; k++) {
            Cow cow = EntityTypes.COW.create(level, EntitySpawnReason.COMMAND);
            cow.snapTo(post(k).getX() + 2.5, floor + 1, post(k).getZ() + 0.5, 0, 0);
            cow.setPersistenceRequired();
            cow.addTag(TAG);
            level.addFreshEntity(cow);
            cow.setLeashedTo(LeashFenceKnotEntity.getOrCreateKnot(level, post(k)), true);
        }
        for (int k = 0; k < JOCKEYS; k++) command(server, String.format(Locale.ROOT, "summon chicken %d %d %d {%s,Tags:[\"%s\"],Passengers:[{id:\"minecraft:husk\",IsBaby:1b,%s,Tags:[\"%s\",\"%s_jockey\"]}]}",
            center.getX() + 20 + 3 * k, floor + 1, center.getZ() + 44, KEEP, TAG, KEEP, TAG, TAG));
        for (int i = 0; i < DESPAWNERS; i++) summonAt(server, "husk", spread(i, 16, 44, 43, 48), "desp" + r, "");
        FLOATING.clear();
        SINKING.clear();
        for (int i = 0; i < FLOATERS.length; i++) FLOATING.add(drop(level, FLOATERS[i], -2 + i, -48.5 + (i % 2)));
        for (int i = 0; i < SINKERS.length; i++) SINKING.add(drop(level, SINKERS[i], -2 + i, -47.5 - (i % 2)));
        for (int k = 0; k < PADS; k++) {
            ItemEntity stick = new ItemEntity(level, pad(r, k).getX() + 0.5, floor + 2.05, pad(r, k).getZ() + 0.5, new ItemStack(Items.STICK), 0, 0, 0);
            stick.setNeverPickUp();
            level.addFreshEntity(stick);
            stick.igniteForSeconds(4);
        }
        NEAR.clear();
        for (Cow cow : level.getEntitiesOfClass(Cow.class, platform(), tagged("idle")))
            if (level.getNearestPlayer(cow, 32) != null) NEAR.add(cow.getUUID());
    }

    private static void soakSetup(MinecraftServer server, int r) {
        ServerLevel level = level(server);
        ROUNDS[r].carriedBefore = carriedBread(level);
        // fresh unemployed villagers, each fenced in next to its own composter: two columns of cells, rounds side by side
        for (int i = 0; i < JOB_CELLS; i++) {
            BlockPos spot = at(-43 + 4 * (i % 2), 1, -38 + 9 * (i / 2) + 3 * r);
            for (int dx = -1; dx <= 2; dx++) for (int dz = -1; dz <= 1; dz++) for (int dy = 0; dy < 2; dy++)
                if (dz != 0 || dx < 0 || dx > 1) place(level, spot.offset(dx, dy, dz), Blocks.BARRIER.defaultBlockState());
            place(level, spot.east(), Blocks.COMPOSTER.defaultBlockState());
            summonAt(server, "villager", spot, "job" + r, "");
        }
        for (int i = 0; i < PAIRS; i++) for (int copy = 0; copy < 2; copy++) summonAt(server, "item", spread(i * 13 + 1, -34, -12, -38, 38), "", "Item:{id:\"minecraft:cobblestone\",count:1}");
        for (int i = 0; i < BREAD; i++) summonAt(server, "item", spread(i * 17 + 5, -34, -12, -38, 38), "", "Item:{id:\"minecraft:bread\",count:1}");
        for (int i = 0; i < ORB_PAIRS; i++) for (int copy = 0; copy < 2; copy++) summonAt(server, "experience_orb", spread(i * 11 + 2, -34, -20, -38, 38), "", "Value:3");
        for (int i = 0; i < LOVERS; i++) summonAt(server, "cow", spread(i * 9 + 4, -34, -10, -38, 38), "", "InLove:600");
        for (int i = 0; i < PIGS; i++) summonAt(server, "pig", column(), "", "");
    }

    private static void soakResults(MinecraftServer server, int r) {
        ServerLevel level = level(server);
        Round soak = ROUNDS[r];
        soak.cobble = items(level, Items.COBBLESTONE);
        soak.stacks = level.getEntitiesOfClass(ItemEntity.class, pens(), item -> item.getItem().is(Items.COBBLESTONE)).size();
        soak.orbs = level.getEntitiesOfClass(ExperienceOrb.class, pens(), orb -> orb.entityTags().contains(TAG)).size();
        soak.pigs = level.getEntitiesOfClass(Pig.class, pens()).size();
        soak.calves = level.getEntitiesOfClass(Cow.class, pens(), Cow::isBaby).size();
        soak.jobs = level.getEntitiesOfClass(Villager.class, pens(), tagged("job" + r).and(villager -> !((Villager) villager).getVillagerData().profession().is(VillagerProfession.NONE))).size();
        soak.carriedAfter = carriedBread(level);
        soak.breadGround = items(level, Items.BREAD);
    }

    /** 90 s after the round's subjects arrived. */
    private static void roundResults(MinecraftServer server, int r) {
        ServerLevel level = level(server);
        Round round = ROUNDS[r];
        for (int k = 0; k < FIGHTS; k++) {
            List<Villager> villagers = level.getEntitiesOfClass(Villager.class, new AABB(fight(k)).inflate(1.5, 1, 0.5).move(1, 0, 0));
            if (villagers.isEmpty() || villagers.get(0).getHealth() < villagers.get(0).getMaxHealth()) round.attacked++; // dead, turned or hurt
        }
        for (UUID id : GOLEMS) // killed by arrows, or hurt
            if (!(level.getEntity(id) instanceof IronGolem golem) || golem.getHealth() < golem.getMaxHealth()) round.shot++;
        for (int k = 0; k < PLATES; k++)
            if (level.getBlockState(hole(r, k)).getValue(PressurePlateBlock.POWERED) && level.getBlockState(hole(r, k).east()).getValue(RedstoneLampBlock.LIT)) round.lit++;
        for (Cow cow : level.getEntitiesOfClass(Cow.class, strips(), cow -> cow.getLeashHolder() instanceof LeashFenceKnotEntity))
            if (cow.distanceTo(cow.getLeashHolder()) <= 12) round.leashed++;
        round.riding = level.getEntitiesOfClass(Zombie.class, strips(), tagged("jockey").and(rider -> ((Entity) rider).getVehicle() instanceof Chicken)).size();
        round.despawned = DESPAWNERS - level.getEntitiesOfClass(Zombie.class, strips(), tagged("desp" + r)).size();
        for (UUID id : FLOATING) {
            if (level.getEntity(id) instanceof ItemEntity item && item.getY() > floor + 2.4) round.floated++;
            else round.misplaced.add(level.getEntity(id) instanceof ItemEntity item ? item.getItem().getItem() + String.format(Locale.ROOT, " %.2f", item.getY() - floor) : "a floater is gone");
        }
        for (UUID id : SINKING) {
            if (level.getEntity(id) instanceof ItemEntity item && item.getY() < floor + 1.6) round.sank++;
            else round.misplaced.add(level.getEntity(id) instanceof ItemEntity item ? item.getItem().getItem() + String.format(Locale.ROOT, " %.2f", item.getY() - floor) : "a sinker is gone");
        }
        for (int k = 0; k < PADS; k++) {
            BlockPos pad = pad(r, k);
            if (!level.getBlockState(pad).is(Blocks.OAK_PLANKS) || level.getBlockState(pad.above()).getBlock() instanceof BaseFireBlock) round.ignited++;
            level.setBlock(pad.above(), Blocks.AIR.defaultBlockState(), 2); // out before it spreads
            level.setBlock(pad, Blocks.AIR.defaultBlockState(), 2);
        }
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
        AsyncTicking.qaStallNanos = 0;
        stallTicks = -1;
        MinecraftServer server = Minecraft.getInstance().getSingleplayerServer();
        Module module = ModuleManager.getInstance().getModule(AsyncModule.NAME);
        if (module != null) module.setEnabled(enabledBefore);
        Module physics = ModuleManager.getInstance().getModule(ItemPhysicsModule.NAME);
        if (physics != null) physics.setEnabled(physicsBefore);
        if (server != null && center != null) {
            try {
                onServer(server, () -> {
                    ServerLevel level = level(server);
                    for (Entity entity : level.getEntitiesOfClass(Entity.class, platform().inflate(4), entity -> !(entity instanceof Player) && !EXISTING.contains(entity.getUUID())))
                        entity.discard();
                    List<Map.Entry<BlockPos, BlockState>> changed = new ArrayList<>(ORIGINAL.entrySet());
                    for (int i = changed.size() - 1; i >= 0; i--) level.setBlock(changed.get(i).getKey(), changed.get(i).getValue(), 2);
                    if (difficultyBefore != null) server.setDifficulty(difficultyBefore, true);
                    server.getGameRules().set(GameRules.MOB_GRIEFING, griefingBefore, server);
                    server.getGameRules().set(GameRules.SPAWN_MOBS, spawningBefore, server);
                    command(server, "time resume");
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

    private static void stopTiming(Round round) {
        timing = false;
        List<Long> ticks;
        synchronized (TICKS) { ticks = new ArrayList<>(TICKS); }
        ticks.sort(null);
        round.ticks = ticks.size();
        round.mspt = ticks.stream().mapToLong(Long::longValue).average().orElse(0) / 1e6;
        round.p95 = ticks.isEmpty() ? 0 : ticks.get((int) (ticks.size() * 0.95)) / 1e6;
        round.entityMs = ticks.isEmpty() ? 0 : AsyncTicking.LOOP_NANOS.get() / 1e6 / ticks.size();
    }

    private static <T> T onServer(MinecraftServer server, Supplier<T> task) { return server.submit(task).join(); }

    private static ServerLevel level(MinecraftServer server) { return server.getPlayerList().getPlayers().get(0).level(); }

    /** dx, dy (above the platform), dz from the player's column. */
    private static BlockPos at(int dx, int dy, int dz) { return new BlockPos(center.getX() + dx, floor + dy, center.getZ() + dz); }

    private static AABB platform() { return new AABB(center.getX() - 46, center.getY() - 8, center.getZ() - 54, center.getX() + 46, floor + 16, center.getZ() + 54); }

    private static AABB pens() { return new AABB(center.getX() - 46, floor, center.getZ() - 41, center.getX() + 46, floor + 16, center.getZ() + 41); }

    /** The south strip (beyond the pens) with the behaviour subjects. */
    private static AABB strips() { return new AABB(center.getX() - 46, floor, center.getZ() + 41.5, center.getX() + 46, floor + 8, center.getZ() + 54); }

    /** The north strip with Item Physics' pool and pads. */
    private static AABB north() { return new AABB(center.getX() - 46, floor, center.getZ() - 54, center.getX() + 46, floor + 8, center.getZ() - 41.5); }

    private static BlockPos column() { return at(26, 1, 30); }

    private static BlockPos fight(int k) { return at(-44 + 6 * (k % 6), 1, k < 6 ? 44 : 48); }

    private static BlockPos shooter(int k) { return at(-9 + 8 * (k % 3), 1, k < 3 ? 44 : 48); }

    private static BlockPos hole(int r, int k) { return at(-44 + 3 * (PLATES * r + k), 1, 52); }

    private static BlockPos post(int k) { return at(18 + 3 * k, 1, 46); }

    private static BlockPos pad(int r, int k) { return at(-20 + 3 * (PADS * r + k), 1, -44); }

    /** Entity i of many spread over the area dx0..dx1, dz0..dz1, standing on the platform. */
    private static BlockPos spread(int i, int dx0, int dx1, int dz0, int dz1) {
        int width = dx1 - dx0 + 1, depth = dz1 - dz0 + 1;
        return at(dx0 + Math.floorMod(i * 7, width), 1, dz0 + Math.floorMod(i * 11 / width + i * 3, depth));
    }

    /** Barrier walls, height blocks tall, around the rectangle x0..x1, z0..z1. */
    private static void box(ServerLevel level, int x0, int z0, int x1, int z1, int height) {
        for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++)
            if (x == x0 || x == x1 || z == z0 || z == z1) wall(level, x, z, height);
    }

    private static void wall(ServerLevel level, int x, int z, int height) {
        for (int dy = 1; dy <= height; dy++) place(level, at(x, dy, z), Blocks.BARRIER.defaultBlockState());
    }

    private static void place(ServerLevel level, BlockPos pos, BlockState state) {
        if (level.getBlockState(pos).canBeReplaced()) replace(level, pos, state);
    }

    private static void replace(ServerLevel level, BlockPos pos, BlockState state) {
        ORIGINAL.putIfAbsent(pos.immutable(), level.getBlockState(pos));
        level.setBlock(pos, state, 2);
    }

    private static UUID drop(ServerLevel level, Item item, int dx, double dz) {
        ItemEntity entity = new ItemEntity(level, center.getX() + dx + 0.5, floor + 2.9, center.getZ() + dz, new ItemStack(item), 0, 0, 0);
        entity.setNeverPickUp();
        entity.addTag(TAG);
        level.addFreshEntity(entity);
        return entity.getUUID();
    }

    private static void summonAt(MinecraftServer server, String type, BlockPos pos, String group, String nbt) {
        String tags = group.isEmpty() ? "\"" + TAG + "\"" : "\"" + TAG + "\",\"" + TAG + "_" + group + "\"";
        command(server, String.format(Locale.ROOT, "summon %s %d %d %d {Tags:[%s]%s}", type, pos.getX(), pos.getY(), pos.getZ(), tags, nbt.isEmpty() ? "" : "," + nbt));
    }

    private static void command(MinecraftServer server, String command) {
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), command);
    }

    private static Predicate<Entity> tagged(String group) { return entity -> entity.entityTags().contains(TAG + "_" + group); }

    private static Map<UUID, double[]> positions(MinecraftServer server) {
        Map<UUID, double[]> map = new HashMap<>();
        for (Mob mob : level(server).getEntitiesOfClass(Mob.class, pens())) map.put(mob.getUUID(), new double[]{mob.getX(), mob.getZ()});
        return map;
    }

    /** End of the 30 s window: who walked 3+ blocks since its start. */
    private static void walked(MinecraftServer server, Round round) {
        ServerLevel level = level(server);
        round.cowWalked = share(level, tagged("walk"));
        round.villagerWalked = share(level, tagged("vwalk"));
        round.nearWalked = share(level, tagged("idle").and(cow -> NEAR.contains(cow.getUUID())));
        round.farWalked = share(level, tagged("idle").and(cow -> !NEAR.contains(cow.getUUID())));
        List<Mob> idle = level.getEntitiesOfClass(Mob.class, pens(), tagged("idle"));
        round.near = (int) idle.stream().filter(cow -> NEAR.contains(cow.getUUID())).count();
        round.far = idle.size() - round.near;
        round.farIdle = (int) idle.stream().filter(cow -> !NEAR.contains(cow.getUUID()) && cow.getNoActionTime() >= 100).count();
    }

    /** Server thread, every 10 ticks of the 30 s window: how far each walker moved since the last look. */
    private static void follow(MinecraftServer server, Round round) {
        for (Mob mob : level(server).getEntitiesOfClass(Mob.class, pens(), tagged("walk").or(tagged("vwalk")))) {
            double[] was = LAST.put(mob.getUUID(), new double[]{mob.getX(), mob.getZ()});
            if (was == null) continue;
            int group = mob instanceof Villager ? 1 : 0;
            double step = Math.hypot(mob.getX() - was[0], mob.getZ() - was[1]);
            round.path[group] += step;
            round.samples[group]++;
            if (step > 0.25) round.moving[group]++;
        }
    }

    private static double share(ServerLevel level, Predicate<Entity> which) {
        int moved = 0, seen = 0;
        for (Mob mob : level.getEntitiesOfClass(Mob.class, pens(), which)) {
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
