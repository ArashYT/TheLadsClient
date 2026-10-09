package com.thelads.core.v1_8_9.feature.ebe;

import com.thelads.core.client.ebe.BakedBlockEntities;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import net.minecraft.block.BlockChest;
import net.minecraft.block.BlockStandingSign;
import net.minecraft.block.BlockWallSign;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntitySign;
import net.minecraft.util.BlockPos;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ScreenShotHelper;
import net.minecraft.world.WorldServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * QA only (LADS_VERIFY_189_ONLY=ebe, a CoreProbe step in its QA world): builds ~500 chests, ender chests and signs in the
 * sky, photographs a showcase row closed / half open / open / closed again and times frames, first baked and then
 * suspended (vanilla renderers), then reloads resources (F3+T) and photographs again. Results: lads-ebe-probe.json and
 * screenshots/ebe-*.png in the game directory; the log ends with "Lads EBE probe END:".
 */
public final class BakedBlockEntitiesProbe189 {
    private static final Logger LOG = LoggerFactory.getLogger("TheLadsCore/BakedBlockEntities");
    private static final int BENCH_TICKS = 200;
    public static volatile boolean recording;
    private static final List<Long> FRAMES = new ArrayList<Long>();
    private static long lastFrame;
    private static int tick, placed, shots, shotsSaved, limitBefore;
    private static boolean vsyncBefore;
    private static BlockPos origin;
    private static final List<BlockPos> SHOWCASE_LIDS = new ArrayList<BlockPos>();
    private static float forced = -1F;
    private static final StringBuilder JSON = new StringBuilder("{");
    private static String phase = "baked";

    private BakedBlockEntitiesProbe189() {}

    /** Every rendered frame while recording (from the frame-counter mixin). */
    public static void frame() {
        long now = System.nanoTime();
        if (lastFrame != 0) FRAMES.add(now - lastFrame);
        lastFrame = now;
    }

    /** One client tick of the probe; true once it is done (CoreProbe then leaves the QA world). */
    public static boolean run(Minecraft mc) throws Exception {
        if (forced >= 0F) for (BlockPos pos : SHOWCASE_LIDS) BakedBlockEntities189.force(mc.theWorld.getTileEntity(pos), forced);
        MinecraftServer server = MinecraftServer.getServer();
        int t = tick++;
        switch (t) {
            case 0: {
                BlockPos p = new BlockPos(mc.thePlayer);
                origin = new BlockPos((p.getX() >> 4 << 4) + 8, 120, (p.getZ() >> 4 << 4) + 8);
                command(server, "gamemode 3 " + mc.thePlayer.getName());
                command(server, "time set 6000");
                command(server, "weather clear");
                build(server.worldServerForDimension(0));
                JSON.append("\"version\":\"1.8.9\",\"active\":").append(BakedBlockEntities.active())
                    .append(",\"xmasForced\":").append(BakedBlockEntities.christmasForced());
                break;
            }
            case 40: {
                EntityPlayerMP player = server.getConfigurationManager().getPlayerByUsername(mc.thePlayer.getName());
                player.playerNetServerHandler.setPlayerLocation(origin.getX() + 0.5, origin.getY() + 5, origin.getZ() - 3.5, 0F, 35F);
                JSON.append(",\"blockEntities\":").append(placed).append(",\"baked\":").append(BakedBlockEntities189.baking());
                LOG.info("Lads EBE probe: {} block entities placed, baking {}", placed, BakedBlockEntities189.baking());
                break;
            }
            case 260: shot(mc, server, "closed"); break;
            case 270: forced = 0.5F; break;
            case 300: shot(mc, server, "mid"); break;
            case 310: forced = 1F; break;
            case 340: shot(mc, server, "open"); break;
            case 350: forced = 0F; break;
            case 352: forced = -1F; break;
            case 400: shot(mc, server, "closed-again"); break;
            case 410: bench(mc, true); break;
            case 410 + BENCH_TICKS: bench(mc, false); break;
            case 420 + BENCH_TICKS:
                phase = "vanilla";
                BakedBlockEntities.setSuspended(true);
                mc.renderGlobal.loadRenderers();
                break;
            case 540 + BENCH_TICKS: shot(mc, server, "closed"); break;
            case 550 + BENCH_TICKS: forced = 0.5F; break;
            case 580 + BENCH_TICKS: shot(mc, server, "mid"); break;
            case 590 + BENCH_TICKS: forced = 1F; break;
            case 620 + BENCH_TICKS: shot(mc, server, "open"); break;
            case 630 + BENCH_TICKS: forced = 0F; break;
            case 632 + BENCH_TICKS: forced = -1F; break;
            case 680 + BENCH_TICKS: shot(mc, server, "closed-again"); break;
            case 690 + BENCH_TICKS: bench(mc, true); break;
            case 690 + 2 * BENCH_TICKS: bench(mc, false); break;
            case 700 + 2 * BENCH_TICKS:
                phase = "baked";
                BakedBlockEntities.setSuspended(false);
                mc.renderGlobal.loadRenderers();
                break;
            case 800 + 2 * BENCH_TICKS:
                phase = "reload";
                mc.refreshResources(); // F3+T, synchronous on 1.8.9
                mc.renderGlobal.loadRenderers();
                break;
            case 900 + 2 * BENCH_TICKS:
                shot(mc, server, "after-f3t");
                JSON.append(",\"bakedAfterReload\":").append(BakedBlockEntities189.baking());
                break;
            case 940 + 2 * BENCH_TICKS:
                finish(mc);
                return true;
            default:
                break;
        }
        return false;
    }

    private static void build(WorldServer world) {
        int ox = origin.getX(), oy = origin.getY(), oz = origin.getZ();
        IBlockState stone = Blocks.stone.getDefaultState();
        for (int x = ox - 24; x < ox + 24; x++)
            for (int z = oz - 6; z < oz + 46; z++) world.setBlockState(new BlockPos(x, oy - 1, z), stone, 2);
        // Showcase row facing the camera: chest, double chest, trapped, ender, signs.
        int x = ox - 6, z = oz + 2;
        lid(world, new BlockPos(x, oy, z), chest(Blocks.chest));
        lid(world, new BlockPos(x + 2, oy, z), chest(Blocks.chest));
        lid(world, new BlockPos(x + 3, oy, z), chest(Blocks.chest));
        lid(world, new BlockPos(x + 5, oy, z), chest(Blocks.trapped_chest));
        lid(world, new BlockPos(x + 7, oy, z), Blocks.ender_chest.getDefaultState().withProperty(BlockChest.FACING, EnumFacing.NORTH));
        sign(world, new BlockPos(x + 9, oy, z), Blocks.standing_sign.getDefaultState().withProperty(BlockStandingSign.ROTATION, 8));
        world.setBlockState(new BlockPos(x + 11, oy, z + 1), stone, 2);
        sign(world, new BlockPos(x + 11, oy, z), Blocks.wall_sign.getDefaultState().withProperty(BlockWallSign.FACING, EnumFacing.NORTH));
        // Benchmark field behind it.
        int i = 0;
        for (int gz = oz + 6; gz < oz + 45; gz += 2) {
            for (int gx = ox - 20; gx <= ox + 20; gx += 2, i++) {
                BlockPos pos = new BlockPos(gx, oy, gz);
                switch (i % 5) {
                    case 0: set(world, pos, chest(Blocks.chest)); break;
                    case 1: set(world, pos, chest(Blocks.trapped_chest)); break;
                    case 2: set(world, pos, Blocks.ender_chest.getDefaultState()); break;
                    case 3: sign(world, pos, Blocks.standing_sign.getDefaultState().withProperty(BlockStandingSign.ROTATION, 8)); break;
                    default:
                        set(world, pos, chest(Blocks.chest));
                        set(world, pos.east(), chest(Blocks.chest));
                }
            }
        }
    }

    private static IBlockState chest(net.minecraft.block.Block block) {
        return block.getDefaultState().withProperty(BlockChest.FACING, EnumFacing.NORTH);
    }

    private static void set(WorldServer world, BlockPos pos, IBlockState state) {
        world.setBlockState(pos, state, 2);
        placed++;
    }

    private static void lid(WorldServer world, BlockPos pos, IBlockState state) {
        set(world, pos, state);
        SHOWCASE_LIDS.add(pos);
    }

    private static void sign(WorldServer world, BlockPos pos, IBlockState state) {
        set(world, pos, state);
        TileEntity te = world.getTileEntity(pos);
        if (te instanceof TileEntitySign) {
            ((TileEntitySign) te).signText[1] = new ChatComponentText("Lads EBE");
            ((TileEntitySign) te).signText[2] = new ChatComponentText("baked board");
            te.markDirty();
            world.markBlockForUpdate(pos);
        }
    }

    private static void command(MinecraftServer server, String command) {
        server.getCommandManager().executeCommand(server, command);
    }

    private static void shot(Minecraft mc, MinecraftServer server, String state) {
        command(server, "time set 6000");
        String file = "ebe-" + phase + "-" + state + (BakedBlockEntities.christmasForced() ? "-xmas" : "") + ".png";
        shots++;
        try {
            File folder = mc.mcDataDir;
            new File(folder, "screenshots").mkdirs();
            ScreenShotHelper.saveScreenshot(folder, file, mc.displayWidth, mc.displayHeight, mc.getFramebuffer());
            shotsSaved++;
            LOG.info("Lads EBE probe frame {}", new File(new File(folder, "screenshots"), file));
        } catch (Exception e) { LOG.error("Lads EBE probe FAILED: frame {}", file, e); }
    }

    private static void bench(Minecraft mc, boolean start) {
        if (start) {
            limitBefore = mc.gameSettings.limitFramerate;
            vsyncBefore = mc.gameSettings.enableVsync;
            mc.gameSettings.limitFramerate = 260; // unlimited while timing, put back right after
            mc.gameSettings.enableVsync = false;
            FRAMES.clear();
            lastFrame = 0;
            recording = true;
            return;
        }
        recording = false;
        mc.gameSettings.limitFramerate = limitBefore;
        mc.gameSettings.enableVsync = vsyncBefore;
        long[] all = new long[FRAMES.size()];
        for (int i = 0; i < all.length; i++) all[i] = FRAMES.get(i);
        int skip = Math.min(all.length / 4, 60); // warm-up and the frame-limit change
        long[] kept = Arrays.copyOfRange(all, skip, all.length);
        Arrays.sort(kept);
        double sum = 0;
        for (long ns : kept) sum += ns;
        double mean = kept.length == 0 ? 0 : sum / kept.length / 1e6;
        double p50 = kept.length == 0 ? 0 : kept[kept.length / 2] / 1e6;
        double p99 = kept.length == 0 ? 0 : kept[Math.min(kept.length - 1, (int) (kept.length * 0.99))] / 1e6;
        String result = String.format(Locale.ROOT, "{\"frames\":%d,\"meanMs\":%.3f,\"p50Ms\":%.3f,\"p99Ms\":%.3f,\"fps\":%.1f}",
            kept.length, mean, p50, p99, mean > 0 ? 1000 / mean : 0);
        JSON.append(",\"bench_").append(phase).append("\":").append(result);
        LOG.info("Lads EBE probe bench {}: {}", phase, result);
    }

    private static void finish(Minecraft mc) throws Exception {
        JSON.append(",\"shots\":").append(shots).append(",\"shotsSaved\":").append(shotsSaved).append('}');
        Files.write(new File(mc.mcDataDir, "lads-ebe-probe.json").toPath(), JSON.toString().getBytes(StandardCharsets.UTF_8));
        int failed = shots - shotsSaved + (BakedBlockEntities.active() && !BakedBlockEntities189.baking() ? 1 : 0);
        LOG.info("Lads EBE probe END: {} passed, {} failed {}", shotsSaved, failed, JSON);
    }
}
