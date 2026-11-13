package com.thelads.smp.qa;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;

/**
 * Test-only content sweep for the Lads SMP pack. Never ship this jar.
 * Starts when -Dladsqa.smoke=1 is set: places every block, uses it, rolls its drops; spawns every entity, lets it live,
 * kills it; uses every item in air and on a block; rolls every loot table; generates terrain and every reachable structure
 * in every dimension. Each step writes ladsqa/current.txt first, so a hard crash names the content that caused it.
 * Results go to ladsqa/report.txt (FAIL lines with the mod id) and ladsqa/summary.txt.
 */
public class SmokeMod implements ModInitializer {
    static final boolean ON = "1".equals(System.getProperty("ladsqa.smoke"));
    static final Path DIR = Path.of("ladsqa");
    static final int X0 = 20000, Z0 = 20000, Y = 200, ROW = 48;

    private final Deque<Runnable> steps = new ArrayDeque<>();
    private final Map<String, int[]> perMod = new TreeMap<>(); // ok, fail
    private final List<Entity> alive = new ArrayList<>();
    private int wait = 200, budgetPerTick = 40;
    private boolean started, finished;
    private long startMs;

    @Override
    public void onInitialize() {
        if (!ON) return;
        try { Files.createDirectories(DIR); Files.writeString(DIR.resolve("report.txt"), ""); } catch (IOException ignored) { }
        ServerTickEvents.END_SERVER_TICK.register(this::tick);
    }

    private void tick(MinecraftServer server) {
        if (finished) return;
        if (wait > 0) { wait--; return; }
        if (!started) { started = true; startMs = System.currentTimeMillis(); plan(server); }
        long until = System.nanoTime() + 40_000_000L; // ~40 ms of work per tick
        int n = 0;
        while (!steps.isEmpty() && (n++ < budgetPerTick) && System.nanoTime() < until) {
            Runnable r = steps.pollFirst();
            r.run();
            if (wait > 0) return;
        }
        if (steps.isEmpty()) finish(server);
    }

    // ---------- plan ----------
    private void plan(MinecraftServer server) {
        ServerLevel level = server.overworld();
        log("START blocks=" + BuiltInRegistries.BLOCK.size() + " items=" + BuiltInRegistries.ITEM.size() + " entities=" + BuiltInRegistries.ENTITY_TYPE.size());
        String only = System.getProperty("ladsqa.phases", "blocks,entities,items,loot,worldgen");

        // Force-load the test area.
        int area = 200;
        for (int cx = (X0 >> 4) - 1; cx <= ((X0 + area * 3) >> 4) + 1; cx++)
            for (int cz = (Z0 >> 4) - 1; cz <= ((Z0 + area * 3) >> 4) + 1; cz++) {
                int fx = cx, fz = cz;
                steps.add(() -> level.setChunkForced(fx, fz, true));
            }

        if (only.contains("blocks")) {
            List<Block> blocks = new ArrayList<>();
            BuiltInRegistries.BLOCK.forEach(blocks::add);
            for (int i = 0; i < blocks.size(); i++) {
                Block b = blocks.get(i);
                BlockPos pos = new BlockPos(X0 + (i % ROW) * 3, Y, Z0 + (i / ROW) * 3);
                if (i % ROW == 0) { int row = i / ROW; steps.add(() -> showPlayers(server, level, new BlockPos(X0 + ROW * 3 / 2, Y + 12, Z0 + row * 3), 3)); }
                steps.add(() -> block(level, b, pos));
            }
        }
        if (only.contains("entities")) {
            List<EntityType<?>> types = new ArrayList<>();
            BuiltInRegistries.ENTITY_TYPE.forEach(types::add);
            int base = Z0 + 600;
            for (int i = 0; i < types.size(); i++) {
                EntityType<?> t = types.get(i);
                BlockPos pos = new BlockPos(X0 + (i % 16) * 6, Y, base + (i / 16) * 6);
                steps.add(() -> spawn(level, t, pos));
                if (i % 32 == 31 || i == types.size() - 1) { steps.add(() -> showPlayers(server, level, pos.above(10), 0)); steps.add(() -> wait = 100); steps.add(() -> killAll(level)); }
            }
        }
        if (only.contains("items")) {
            List<Item> items = new ArrayList<>();
            BuiltInRegistries.ITEM.forEach(items::add);
            int base = Z0 + 1200;
            for (int i = 0; i < items.size(); i++) {
                Item it = items.get(i);
                BlockPos pos = new BlockPos(X0 + (i % ROW) * 3, Y, base + (i / ROW) * 3);
                steps.add(() -> item(level, it, pos));
            }
            steps.add(() -> wait = 60);
            steps.add(() -> killAll(level));
        }
        if (only.contains("loot")) {
            var lookup = server.reloadableRegistries().lookup().lookupOrThrow(Registries.LOOT_TABLE);
            lookup.listElementIds().forEach(k -> steps.add(() -> loot(server, level, k)));
        }
        if (only.contains("worldgen")) {
            int r = Integer.getInteger("ladsqa.radius", 6);
            for (ServerLevel l : server.getAllLevels()) {
                for (int cx = -r; cx <= r; cx++) for (int cz = -r; cz <= r; cz++) {
                    int fx = (5000 >> 4) + cx, fz = (5000 >> 4) + cz;
                    steps.add(() -> guard("worldgen", l.dimension().identifier(), "chunk " + fx + "," + fz, () -> l.getChunk(fx, fz)));
                }
            }
            var structures = server.registryAccess().lookupOrThrow(Registries.STRUCTURE);
            structures.listElements().forEach(h -> steps.add(() -> structure(server, h)));
        }
    }

    // ---------- phases ----------
    private void block(ServerLevel level, Block b, BlockPos pos) {
        Identifier id = BuiltInRegistries.BLOCK.getKey(b);
        guard("block", id, "place/use/drops", () -> {
            level.setBlock(pos.below(), Blocks.STONE.defaultBlockState(), 3);
            BlockState s = b.defaultBlockState();
            if (b instanceof LiquidBlock) return; // fluids flood the grid; the fluid itself is covered by buckets in the item phase
            level.setBlock(pos, s, 3);
            BlockState placed = level.getBlockState(pos);
            FakePlayer fp = fake(level, pos);
            placed.useWithoutItem(level, fp, new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
            fp.closeContainer();
            placed = level.getBlockState(pos);
            if (placed.isRandomlyTicking()) placed.randomTick(level, pos, level.getRandom());
            Block.getDrops(placed, level, pos, level.getBlockEntity(pos), fp, new ItemStack(Items.DIAMOND_PICKAXE));
        });
    }

    private void spawn(ServerLevel level, EntityType<?> t, BlockPos pos) {
        Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(t);
        if (t == net.minecraft.world.entity.EntityTypes.PLAYER) return;
        guard("entity", id, "spawn", () -> {
            level.setBlock(pos.below(), Blocks.STONE.defaultBlockState(), 3);
            Entity e = t.create(level, EntitySpawnReason.COMMAND);
            if (e == null) return;
            e.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0, 0);
            if (e instanceof Mob m) m.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), EntitySpawnReason.COMMAND, null);
            level.addFreshEntity(e);
            alive.add(e);
        });
    }

    private void killAll(ServerLevel level) {
        for (Entity e : alive) {
            Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(e.getType());
            guard("entity", id, "kill/loot", () -> {
                if (e.isRemoved()) return;
                if (e instanceof LivingEntity le) le.hurtServer(level, level.damageSources().playerAttack(fake(level, e.blockPosition())), Float.MAX_VALUE);
                if (!e.isRemoved()) e.discard();
            });
        }
        alive.clear();
        // Clear dropped items and anything the entities spawned.
        guard("entity", Identifier.withDefaultNamespace("cleanup"), "cleanup", () -> {
            var box = new net.minecraft.world.phys.AABB(X0 - 64, 0, Z0 - 64, X0 + 400, 400, Z0 + 2000);
            for (Entity e : level.getEntities((Entity) null, box, e -> !(e instanceof Player))) e.discard();
        });
    }

    private void item(ServerLevel level, Item it, BlockPos pos) {
        Identifier id = BuiltInRegistries.ITEM.getKey(it);
        guard("item", id, "use/useOn/tick", () -> {
            level.setBlock(pos.below(), Blocks.STONE.defaultBlockState(), 3);
            FakePlayer fp = fake(level, pos);
            ItemStack stack = new ItemStack(it);
            fp.setItemInHand(InteractionHand.MAIN_HAND, stack);
            it.inventoryTick(stack, level, fp, EquipmentSlot.MAINHAND);
            it.use(level, fp, InteractionHand.MAIN_HAND);
            fp.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(it));
            it.useOn(new UseOnContext(fp, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(pos.below()).add(0, 0.5, 0), Direction.UP, pos.below(), false)));
            fp.stopUsingItem();
            fp.closeContainer();
            fp.getInventory().clearContent();
        });
    }

    private void loot(MinecraftServer server, ServerLevel level, ResourceKey<LootTable> key) {
        guard("loot", key.identifier(), "roll", () -> {
            LootTable table = server.reloadableRegistries().getLootTable(key);
            var set = table.getParamSet();
            FakePlayer fp = fake(level, new BlockPos(X0, Y, Z0));
            LootParams.Builder b = new LootParams.Builder(level);
            for (ContextKey<?> k : set.allowed()) supply(b, k, level, fp);
            table.getRandomItems(b.create(set));
        });
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void supply(LootParams.Builder b, ContextKey k, ServerLevel level, FakePlayer fp) {
        Object v = null;
        if (k == LootContextParams.ORIGIN) v = new Vec3(X0, Y, Z0);
        else if (k == LootContextParams.THIS_ENTITY || k == LootContextParams.ATTACKING_ENTITY || k == LootContextParams.DIRECT_ATTACKING_ENTITY
                || k == LootContextParams.INTERACTING_ENTITY || k == LootContextParams.TARGET_ENTITY || k == LootContextParams.LAST_DAMAGE_PLAYER) v = fp;
        else if (k == LootContextParams.DAMAGE_SOURCE) v = level.damageSources().playerAttack(fp);
        else if (k == LootContextParams.BLOCK_STATE) v = Blocks.STONE.defaultBlockState();
        else if (k == LootContextParams.TOOL) v = new ItemStack(Items.DIAMOND_PICKAXE);
        else if (k == LootContextParams.EXPLOSION_RADIUS) v = 4f;
        else if (k == LootContextParams.ENCHANTMENT_LEVEL) v = 1;
        else if (k == LootContextParams.ENCHANTMENT_ACTIVE) v = true;
        else if (k == LootContextParams.ADDITIONAL_COST_COMPONENT_ALLOWED) v = net.minecraft.util.Unit.INSTANCE;
        else if (k == LootContextParams.CONTAINER) v = fp;
        else if (k == LootContextParams.BLOCK_ENTITY) v = net.minecraft.world.level.block.entity.BlockEntityTypes.CHEST.create(new BlockPos(X0, Y, Z0), Blocks.CHEST.defaultBlockState());
        if (v != null) b.withParameter(k, v);
    }

    private void structure(MinecraftServer server, Holder<Structure> h) {
        Identifier id = h.unwrapKey().map(ResourceKey::identifier).orElse(Identifier.withDefaultNamespace("unknown"));
        for (ServerLevel l : server.getAllLevels()) {
            guard("structure", id, "locate+generate in " + l.dimension().identifier(), () -> {
                var found = l.getChunkSource().getGenerator().findNearestMapStructure(l, HolderSet.direct(h), new BlockPos(0, 64, 0), 40, false);
                if (found == null) return;
                BlockPos p = found.getFirst();
                for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) l.getChunk((p.getX() >> 4) + dx, (p.getZ() >> 4) + dz);
                log("STRUCT " + id + " in " + l.dimension().identifier() + " at " + p.getX() + "," + p.getZ());
            });
        }
    }

    // ---------- helpers ----------
    private void showPlayers(MinecraftServer server, ServerLevel level, BlockPos at, int waitTicks) {
        List<ServerPlayer> ps = server.getPlayerList().getPlayers();
        if (ps.isEmpty()) return;
        for (ServerPlayer p : ps) p.teleportTo(level, at.getX() + 0.5, at.getY(), at.getZ() + 0.5, Set.<Relative>of(), 0, 60, false);
        wait = Math.max(wait, waitTicks);
    }

    private static FakePlayer fake(ServerLevel level, BlockPos pos) {
        FakePlayer fp = FakePlayer.get(level);
        fp.snapTo(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, 0, 0);
        return fp;
    }

    private void guard(String phase, Identifier id, String what, Runnable r) {
        String line = phase + " " + id + " " + what;
        write("current.txt", line, false);
        int[] c = perMod.computeIfAbsent(id.getNamespace(), k -> new int[2]);
        long t0 = System.nanoTime();
        try {
            r.run();
            c[0]++;
            long ms = (System.nanoTime() - t0) / 1_000_000;
            if (ms >= 2000) log("SLOW " + ms + " ms | " + line);
        } catch (Throwable t) {
            c[1]++;
            StringWriter sw = new StringWriter();
            t.printStackTrace(new PrintWriter(sw));
            String trace = sw.toString();
            String head = trace.lines().limit(14).reduce("", (a, b2) -> a + "\n    " + b2);
            log("FAIL " + id.getNamespace() + " | " + line + " | " + t + head);
        }
    }

    private void finish(MinecraftServer server) {
        finished = true;
        StringBuilder sb = new StringBuilder("mod ok fail\n");
        int fails = 0;
        for (var e : perMod.entrySet()) { sb.append(e.getKey()).append(' ').append(e.getValue()[0]).append(' ').append(e.getValue()[1]).append('\n'); fails += e.getValue()[1]; }
        sb.append("TOTAL_FAILS ").append(fails).append(" seconds ").append((System.currentTimeMillis() - startMs) / 1000).append('\n');
        write("summary.txt", sb.toString(), false);
        write("current.txt", "DONE", false);
        log("DONE fails=" + fails);
        if (Boolean.getBoolean("ladsqa.halt")) server.halt(false);
    }

    private static void log(String s) { System.out.println("[LadsQA] " + s.lines().findFirst().orElse("")); write("report.txt", s + "\n", true); }

    private static void write(String name, String s, boolean append) {
        try {
            Files.writeString(DIR.resolve(name), s, append ? new StandardOpenOption[]{StandardOpenOption.CREATE, StandardOpenOption.APPEND}
                    : new StandardOpenOption[]{StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING});
        } catch (IOException ignored) { }
    }
}
