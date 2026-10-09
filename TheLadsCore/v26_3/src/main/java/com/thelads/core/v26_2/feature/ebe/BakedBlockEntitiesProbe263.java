package com.thelads.core.v26_2.feature.ebe;

import com.thelads.core.client.ebe.BakedBlockEntities;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * QA only ({@code -Dlads.ebe.probe=true}): in the harness's auto world, builds ~500 chests, shulker boxes and bells in
 * the sky, photographs a showcase row closed / half open / open / closed again and times frames, first baked and then
 * suspended (vanilla renderers), then reloads resources (F3+T) and photographs again. Results: lads-ebe-probe.json and
 * screenshots/ebe-*.png in the game directory; the log ends with "Lads EBE probe END:".
 */
public final class BakedBlockEntitiesProbe263 {
    private static final Logger LOG = LoggerFactory.getLogger("TheLadsCore/BakedBlockEntities");
    private static final int BENCH_TICKS = 200;
    public static volatile boolean recording;
    private static final List<Long> FRAMES = new ArrayList<>();
    private static long lastFrame;

    private static int tick = -1, inWorld;
    private static BlockPos origin;
    private static final List<BlockPos> SHOWCASE_LIDS = new ArrayList<>();
    private static float forced = -1f;
    private static int placed;
    private static final StringBuilder JSON = new StringBuilder("{");
    private static int shots, shotsSaved, limitBefore;
    private static java.util.concurrent.CompletableFuture<Void> reload;
    private static String phase = "baked";
    private static int mark, stage;

    private BakedBlockEntitiesProbe263() {}

    public static void register() {
        if (!Boolean.getBoolean("lads.ebe.probe")) return;
        ClientTickEvents.END_CLIENT_TICK.register(client -> tick());
        LOG.info("Lads EBE probe armed (xmas forced: {})", BakedBlockEntities.christmasForced());
    }

    /** Every rendered frame while recording (from the frame-counter mixin). */
    public static void frame() {
        long now = System.nanoTime();
        if (lastFrame != 0) FRAMES.add(now - lastFrame);
        lastFrame = now;
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.getSingleplayerServer() == null) return;
        if (mc.player.isDeadOrDying() && mc.gui.screen() instanceof net.minecraft.client.gui.screens.DeathScreen) {
            mc.player.respawn(); // a world probe or a mob killed the QA player
            mc.setScreenAndShow(null);
            return;
        }
        if (tick < 0) {
            // The harness asks once its world probes passed (".lads-qa-ebe"); if one of them failed it never asks, so the
            // probe also starts on its own 60 s after joining (the world probes are done by then).
            Path request = mc.gameDirectory.toPath().resolve(".lads-qa-ebe");
            boolean asked = Files.isRegularFile(request);
            if (mc.gui.screen() != null || ++inWorld < 200 || (!asked && inWorld < 1200)) return;
            try { Files.deleteIfExists(request); } catch (Exception ignored) {}
            tick = 0;
        }
        if (forced >= 0f) forceLids(mc, forced);
        try {
            step(mc, tick++);
        } catch (Throwable t) {
            LOG.error("Lads EBE probe FAILED at tick {}", tick, t);
            tick = Integer.MIN_VALUE / 2;
        }
    }

    // Timeline in client ticks: build, settle, photos + bench baked, suspend, photos + bench vanilla, resume, F3+T, photo.
    private static void step(Minecraft mc, int t) throws Exception {
        IntegratedServer server = mc.getSingleplayerServer();
        String name = mc.player.getGameProfile().name();
        switch (t) {
            case 0 -> {
                BlockPos p = mc.player.blockPosition();
                origin = new BlockPos((p.getX() >> 4 << 4) + 8, 180, (p.getZ() >> 4 << 4) + 8);
                command(server, "gamemode spectator " + name);
                command(server, "time set noon");
                command(server, "weather clear");
                server.execute(() -> build(server.overworld()));
                JSON.append("\"version\":\"26.3\",\"active\":").append(BakedBlockEntities.active())
                    .append(",\"xmasForced\":").append(BakedBlockEntities.christmasForced());
            }
            case 40 -> {
                command(server, String.format(Locale.ROOT, "tp %s %.1f %d %.1f 0 35", name, origin.getX() + 0.5, origin.getY() + 5, origin.getZ() - 3.5));
                JSON.append(",\"blockEntities\":").append(placed).append(",\"bakedStates\":").append(installedCount());
                LOG.info("Lads EBE probe: {} block entities placed, {} baked block states", placed, installedCount());
            }
            case 260 -> shot(mc, "closed"); // the game-mode chat line has faded by now
            case 270 -> forced = 0.5f;
            case 300 -> shot(mc, "mid");
            case 310 -> forced = 1f;
            case 340 -> shot(mc, "open");
            case 350 -> forced = 0f;
            case 352 -> forced = -1f;
            case 400 -> shot(mc, "closed-again");
            case 404 -> side(server, name, true);
            case 440 -> shot(mc, "closed-side");
            case 444 -> side(server, name, false);
            case 470 -> bench(mc, true);
            case 470 + BENCH_TICKS -> bench(mc, false);
            case 480 + BENCH_TICKS -> {
                phase = "vanilla";
                BakedBlockEntities.setSuspended(true);
                mc.levelExtractor.allChanged();
            }
            case 600 + BENCH_TICKS -> shot(mc, "closed");
            case 610 + BENCH_TICKS -> forced = 0.5f;
            case 640 + BENCH_TICKS -> shot(mc, "mid");
            case 650 + BENCH_TICKS -> forced = 1f;
            case 680 + BENCH_TICKS -> shot(mc, "open");
            case 690 + BENCH_TICKS -> forced = 0f;
            case 692 + BENCH_TICKS -> forced = -1f;
            case 740 + BENCH_TICKS -> shot(mc, "closed-again");
            case 744 + BENCH_TICKS -> side(server, name, true);
            case 780 + BENCH_TICKS -> shot(mc, "closed-side");
            case 784 + BENCH_TICKS -> side(server, name, false);
            case 810 + BENCH_TICKS -> bench(mc, true);
            case 810 + 2 * BENCH_TICKS -> bench(mc, false);
            case 820 + 2 * BENCH_TICKS -> {
                phase = "baked";
                BakedBlockEntities.setSuspended(false);
                mc.levelExtractor.allChanged();
            }
            case 920 + 2 * BENCH_TICKS -> {
                phase = "reload";
                reload = mc.reloadResourcePacks();
            }
            default -> {
                if (reload != null) {
                    if (reload.isDone()) { reload = null; mark = t; stage++; } // reloads can take a while on a big pack
                    return;
                }
                int dt = t - mark;
                if (stage == 1) { // after F3+T
                    if (dt == 100) {
                        shot(mc, "after-f3t");
                        JSON.append(",\"bakedStatesAfterReload\":").append(installedCount());
                    } else if (dt == 120) reload = pack(mc, true);
                } else if (stage == 2) { // a resource pack retexturing chests (inverted) and shipping a CEM shulker box model
                    if (dt == 100) {
                        phase = "baked";
                        shot(mc, "pack");
                        JSON.append(",\"bakedStatesWithPack\":").append(installedCount());
                    } else if (dt == 110) {
                        BakedBlockEntities.setSuspended(true);
                        allChanged(mc);
                    } else if (dt == 230) {
                        phase = "vanilla";
                        shot(mc, "pack");
                    } else if (dt == 240) {
                        BakedBlockEntities.setSuspended(false);
                        reload = pack(mc, false);
                    }
                } else if (stage == 3 && dt == 40) finish(mc);
            }
        }
    }

    private static void build(ServerLevel level) {
        int ox = origin.getX(), oy = origin.getY(), oz = origin.getZ();
        BlockState stone = Blocks.STONE.defaultBlockState();
        for (int x = ox - 24; x < ox + 24; x++)
            for (int z = oz - 6; z < oz + 46; z++) level.setBlock(new BlockPos(x, oy - 1, z), stone, Block.UPDATE_CLIENTS);
        // Showcase row facing the camera: chest, double chest, trapped, ender, shulker, red shulker, bell, copper, oxidized copper.
        int x = ox - 7, z = oz + 2;
        lid(level, new BlockPos(x, oy, z), chest(Blocks.CHEST, ChestType.SINGLE));
        lid(level, new BlockPos(x + 2, oy, z), chest(Blocks.CHEST, ChestType.LEFT));
        lid(level, new BlockPos(x + 3, oy, z), chest(Blocks.CHEST, ChestType.RIGHT));
        lid(level, new BlockPos(x + 5, oy, z), chest(Blocks.TRAPPED_CHEST, ChestType.SINGLE));
        lid(level, new BlockPos(x + 7, oy, z), Blocks.ENDER_CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.NORTH));
        set(level, new BlockPos(x + 9, oy, z), Blocks.SHULKER_BOX.defaultBlockState());
        set(level, new BlockPos(x + 10, oy, z), block("red_shulker_box").defaultBlockState());
        set(level, new BlockPos(x + 12, oy, z), Blocks.BELL.defaultBlockState());
        lid(level, new BlockPos(x + 14, oy, z), chest(block("copper_chest"), ChestType.SINGLE));
        lid(level, new BlockPos(x + 15, oy, z), chest(block("oxidized_copper_chest"), ChestType.SINGLE));
        set(level, new BlockPos(x + 17, oy, z), Blocks.DECORATED_POT.defaultBlockState());
        // Benchmark field behind it.
        DyeColor[] colors = DyeColor.values();
        int i = 0;
        for (int gz = oz + 6; gz < oz + 45; gz += 2) {
            for (int gx = ox - 20; gx <= ox + 20; gx += 2, i++) {
                BlockPos pos = new BlockPos(gx, oy, gz);
                switch (i % 7) {
                    case 0 -> set(level, pos, chest(Blocks.CHEST, ChestType.SINGLE));
                    case 1 -> set(level, pos, chest(Blocks.TRAPPED_CHEST, ChestType.SINGLE));
                    case 2 -> set(level, pos, Blocks.ENDER_CHEST.defaultBlockState());
                    case 3 -> set(level, pos, block(colors[i % colors.length].getName() + "_shulker_box").defaultBlockState());
                    case 4 -> set(level, pos, Blocks.BELL.defaultBlockState());
                    case 5 -> set(level, pos, (i % 2 == 0 ? Blocks.DECORATED_POT : block("copper_chest")).defaultBlockState());
                    default -> {
                        set(level, pos, chest(Blocks.CHEST, ChestType.LEFT));
                        set(level, pos.east(), chest(Blocks.CHEST, ChestType.RIGHT));
                    }
                }
            }
        }
    }

    private static Block block(String id) { return BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace(id)); }

    private static BlockState chest(Block block, ChestType type) {
        return block.defaultBlockState().setValue(ChestBlock.FACING, Direction.NORTH).setValue(ChestBlock.TYPE, type);
    }

    private static void set(ServerLevel level, BlockPos pos, BlockState state) {
        level.setBlock(pos, state, Block.UPDATE_CLIENTS);
        placed++;
    }

    private static void lid(ServerLevel level, BlockPos pos, BlockState state) {
        set(level, pos, state);
        SHOWCASE_LIDS.add(pos);
    }

    private static void forceLids(Minecraft mc, float openness) {
        for (BlockPos pos : SHOWCASE_LIDS) {
            BlockEntity be = mc.level.getBlockEntity(pos);
            if (be instanceof BakedBlockEntities263.LidHolder holder)
                ((BakedBlockEntities263.LidTarget) holder.lads$lid()).lads$force(openness);
        }
    }

    /** Camera beside the showcase row (signs, pots and the rest of the row's left end), or back in front of it. */
    private static void side(IntegratedServer server, String name, boolean side) {
        command(server, String.format(Locale.ROOT, "tp %s %.1f %d %.1f 0 35", name, origin.getX() + (side ? 9.5 : 0.5), origin.getY() + 5, origin.getZ() - 3.5));
    }

    /**
     * Adds (or removes) a QA resource pack in the sandbox's resourcepacks folder: the vanilla chest texture inverted, and an
     * OptiFine-format shulker box model; baked chests must follow the texture, shulker boxes must fall back to their renderer.
     */
    private static java.util.concurrent.CompletableFuture<Void> pack(Minecraft mc, boolean on) throws Exception {
        String id = "file/lads-ebe-qa";
        Path root = mc.getResourcePackDirectory().resolve("lads-ebe-qa");
        if (on) {
            Files.createDirectories(root.resolve("assets/minecraft/textures/entity/chest"));
            Files.createDirectories(root.resolve("assets/minecraft/optifine/cem"));
            Files.writeString(root.resolve("pack.mcmeta"), "{\"pack\":{\"pack_format\":15,\"description\":\"Lads EBE QA\"}}");
            Files.writeString(root.resolve("assets/minecraft/optifine/cem/shulker_box.jem"), "{}");
            java.awt.image.BufferedImage image;
            try (var in = mc.getResourceManager().getResource(Identifier.withDefaultNamespace("textures/entity/chest/normal.png")).orElseThrow().open()) {
                image = javax.imageio.ImageIO.read(in);
            }
            for (int y = 0; y < image.getHeight(); y++)
                for (int x = 0; x < image.getWidth(); x++) {
                    int argb = image.getRGB(x, y);
                    image.setRGB(x, y, (argb & 0xFF000000) | (~argb & 0x00FFFFFF));
                }
            javax.imageio.ImageIO.write(image, "png", root.resolve("assets/minecraft/textures/entity/chest/normal.png").toFile());
        }
        var repository = mc.getResourcePackRepository();
        repository.reload();
        List<String> selected = new ArrayList<>(repository.getSelectedIds());
        selected.remove(id);
        if (on) selected.add(id);
        repository.setSelected(selected);
        LOG.info("Lads EBE probe: QA resource pack {}", on ? "on" : "off");
        return mc.reloadResourcePacks();
    }

    private static void allChanged(Minecraft mc) { mc.levelExtractor.allChanged(); }

    private static void command(IntegratedServer server, String command) {
        server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), command));
    }

    private static void shot(Minecraft mc, String state) {
        command(mc.getSingleplayerServer(), "time set noon");
        String file = "ebe-" + phase + "-" + state + (BakedBlockEntities.christmasForced() ? "-xmas" : "") + ".png";
        Path out = mc.gameDirectory.toPath().resolve("screenshots").resolve(file);
        shots++;
        try {
            Files.createDirectories(out.getParent());
            net.minecraft.client.Screenshot.takeScreenshot(mc.gameRenderer.mainRenderTarget(), image -> {
                try { image.writeToFile(out); shotsSaved++; LOG.info("Lads EBE probe frame {}", out); }
                catch (Exception e) { LOG.error("Lads EBE probe FAILED: frame {}", file, e); }
                finally { image.close(); }
            });
        } catch (Exception e) { LOG.error("Lads EBE probe FAILED: frame {}", file, e); }
    }

    private static void bench(Minecraft mc, boolean start) {
        if (start) {
            limitBefore = mc.options.framerateLimit().get();
            mc.options.framerateLimit().set(260); // unlimited while timing, put back right after
            FRAMES.clear();
            lastFrame = 0;
            recording = true;
            return;
        }
        recording = false;
        mc.options.framerateLimit().set(limitBefore);
        int skip = Math.min(FRAMES.size() / 4, 60); // warm-up and the frame-limit change
        long[] kept = Arrays.copyOfRange(FRAMES.stream().mapToLong(Long::longValue).toArray(), skip, FRAMES.size());
        Arrays.sort(kept);
        double mean = Arrays.stream(kept).average().orElse(0) / 1e6;
        double p50 = kept.length == 0 ? 0 : kept[kept.length / 2] / 1e6;
        double p99 = kept.length == 0 ? 0 : kept[Math.min(kept.length - 1, (int) (kept.length * 0.99))] / 1e6;
        String result = String.format(Locale.ROOT, "{\"frames\":%d,\"meanMs\":%.3f,\"p50Ms\":%.3f,\"p99Ms\":%.3f,\"fps\":%.1f}",
            kept.length, mean, p50, p99, mean > 0 ? 1000 / mean : 0);
        JSON.append(",\"bench_").append(phase).append("\":").append(result);
        LOG.info("Lads EBE probe bench {}: {}", phase, result);
    }

    private static int installedCount() { return BakedBlockEntities263.bakedStates(); }

    private static void finish(Minecraft mc) throws Exception {
        JSON.append(",\"shots\":").append(shots).append(",\"shotsSaved\":").append(shotsSaved).append('}');
        Files.writeString(mc.gameDirectory.toPath().resolve("lads-ebe-probe.json"), JSON, StandardCharsets.UTF_8);
        // Checks: every frame saved; with baking on, baked models exist before and after F3+T.
        int failed = shots - shotsSaved;
        if (BakedBlockEntities.active() && installedCount() == 0) failed++;
        LOG.info("Lads EBE probe END: {} passed, {} failed {}", shotsSaved, failed, JSON);
        tick = Integer.MIN_VALUE / 2;
    }
}
