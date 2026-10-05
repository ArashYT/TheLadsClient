package com.thelads.core.v1_8_9.feature;

import com.thelads.core.client.Occlusion;
import java.lang.reflect.Field;
import java.nio.FloatBuffer;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.boss.EntityDragon;
import net.minecraft.entity.boss.EntityWither;
import net.minecraft.entity.monster.EntityGuardian;
import net.minecraft.entity.projectile.EntityFishHook;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityBanner;
import net.minecraft.tileentity.TileEntityBeacon;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.tileentity.TileEntityPiston;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

/**
 * Entity Culling on 1.8.9: entities and block entities hidden behind opaque blocks are not drawn (RenderManagerCullMixin,
 * TileEntityCullMixin). A daemon thread decides, from the camera through the client world's opaque cubes (Occlusion), which ones
 * are hidden, and stamps them with its pass number. The render thread trusts a stamp only while the camera is still inside the
 * cube that pass checked around its eye and the pass is fresh; anything else draws as usual. Name tags of culled entities are
 * still drawn (through walls, as vanilla). Never culled: the camera's own and ridden/riding entities, leashed mobs, fishing
 * hooks, guardians, bosses, beacons, big boxes, anything within 2 blocks or beyond 128, and everything in OptiFine's shadow pass
 * and the spectator outline pass.
 */
public final class EntityCulling189 {
    /** Implemented on Entity and TileEntity by mixins: the pass that found it hidden (0: visible), and when it was last drawn. */
    public interface Cullable {
        int ladsCullGen();
        void ladsCullGen(int gen);
        int ladsSeen();
        void ladsSeen(int frame);
    }

    public static final String MODULE = "EntityCulling";
    private static final Logger LOG = LogManager.getLogger("TheLadsCore");
    /** Half-size of the cube around the eye a pass checks; its answer holds while the camera stays inside it. */
    static final double EYE_CUBE = 0.5;
    private static final double NEAR = 2, FAR = 128, MAX_SIZE = 6, SIDE = 0.75, UP = 0.75, DOWN = 0.1, BLOCK_MARGIN = 0.25;
    /** A pass is trusted this long after it started; boxes reach as far as their entity moves in that time (5 ticks). */
    private static final long FRESH = 250_000_000L, CHANGED_FOR = 1_000_000_000L, CHUNK_SETTLE = 2_000_000_000L;
    private static final double AHEAD = 5;
    private static final int RADIUS = 9, SEEN_FRAMES = 600;

    /** One pass: its number, the eye it checked from and when it started. */
    private static final class Pass {
        final int gen; final double x, y, z; final long start;
        Pass(int gen, double[] eye, long start) { this.gen = gen; x = eye[0]; y = eye[1]; z = eye[2]; this.start = start; }
        boolean holds(double[] camera, long now) {
            return now - start < FRESH && Math.abs(camera[0] - x) <= EYE_CUBE && Math.abs(camera[1] - y) <= EYE_CUBE
                && Math.abs(camera[2] - z) <= EYE_CUBE;
        }
    }

    /** The loaded chunks around the camera (taken on the game thread each tick) and the opaque-cube table. */
    private static final class Snapshot {
        final WorldClient world; final Chunk[] chunks; final int x0, z0, size; final boolean[] opaque;
        Snapshot(WorldClient world, Chunk[] chunks, int x0, int z0, int size, boolean[] opaque) {
            this.world = world; this.chunks = chunks; this.x0 = x0; this.z0 = z0; this.size = size; this.opaque = opaque;
        }
    }

    // Game/render thread -> culling thread.
    private static volatile double[] camera;
    private static volatile Snapshot snapshot;
    private static volatile boolean enabled;
    private static volatile int frame;
    /** Chunk sections whose blocks changed lately (their new state may not be drawn yet): counted as open air for a second. */
    private static final Map<Long, Long> CHANGED = new ConcurrentHashMap<Long, Long>();
    // Culling thread -> render thread.
    private static volatile Pass done, running;
    // Render thread only.
    private static int cameraFrame = -1, doneGen, runGen;
    private static boolean doneOk, runOk;
    private static final FloatBuffer MODELVIEW = BufferUtils.createFloatBuffer(16);
    private static final float[] MATRIX = new float[16];
    private static final Map<Chunk, Long> FIRST_SEEN = new WeakHashMap<Chunk, Long>();
    private static WorldClient tableWorld;
    private static boolean[] opaque;
    private static Thread thread;
    private static Field shadowPass;
    private static boolean shadowLooked;
    /** QA (Probe173Cull): culled draws, and culled draws the current camera alone would have shown, counted when auditing. */
    static volatile boolean audit;
    static int culled, culledTiles, wrong, shadowDraws, drawnFrames, trustedFrames;
    /** QA: finished passes and the time they took (culling thread). */
    static volatile long passNanos;
    static volatile int passes;

    public EntityCulling189() {} // registered on Forge's bus for its render tick

    /** Each frame (RenderTickEvent START): a new frame number, and the module switch. */
    @SubscribeEvent
    public void renderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        frame++;
        enabled = Options189.enabled(MODULE);
        if (enabled && thread == null) {
            thread = new Thread(EntityCulling189::loop, "Lads Entity Culling");
            thread.setDaemon(true);
            thread.setPriority(Thread.NORM_PRIORITY - 1);
            thread.start();
        }
    }

    /** Each client tick: the chunks around the camera for the thread, and changed sections forgotten after a second. */
    public static void tick(Minecraft mc) {
        WorldClient world = mc.theWorld;
        Entity view = mc.getRenderViewEntity();
        if (!enabled || world == null || view == null) { snapshot = null; return; }
        if (world != tableWorld) {
            tableWorld = world;
            opaque = opaqueTable();
            FIRST_SEEN.clear();
            CHANGED.clear();
        }
        long now = System.nanoTime();
        int size = RADIUS * 2 + 1, x0 = (int) Math.floor(view.posX / 16) - RADIUS, z0 = (int) Math.floor(view.posZ / 16) - RADIUS;
        Chunk[] chunks = new Chunk[size * size];
        for (int i = 0; i < size; i++) for (int j = 0; j < size; j++) {
            if (!world.getChunkProvider().chunkExists(x0 + i, z0 + j)) continue;
            Chunk chunk = world.getChunkFromChunkCoords(x0 + i, z0 + j);
            Long seen = FIRST_SEEN.get(chunk);
            if (seen == null) FIRST_SEEN.put(chunk, now); // just arrived: its blocks may not be drawn yet
            else if (now - seen > CHUNK_SETTLE) chunks[i * size + j] = chunk;
        }
        snapshot = new Snapshot(world, chunks, x0, z0, size, opaque);
        for (Map.Entry<Long, Long> entry : CHANGED.entrySet()) if (now - entry.getValue() > CHANGED_FOR) CHANGED.remove(entry.getKey());
    }

    /** World.markBlockForUpdate / markBlockRangeForRenderUpdate on the client: those sections are not trusted for a second. */
    public static void blocksChanged(int x1, int y1, int z1, int x2, int y2, int z2) {
        if (!enabled) return;
        long now = System.nanoTime();
        for (int x = x1 >> 4; x <= x2 >> 4; x++) for (int y = Math.max(0, y1 >> 4); y <= Math.min(15, y2 >> 4); y++)
            for (int z = z1 >> 4; z <= z2 >> 4; z++) CHANGED.put(section(x, y, z), now);
    }

    /** RenderManager.renderEntityStatic: true to draw only the name tag. {@code originX..} is where the world is drawn from. */
    public static boolean skip(Entity entity, boolean outlines, double originX, double originY, double originZ) {
        if (!enabled || outlines) return false;
        if (inShadowPass()) { shadowDraws++; return false; }
        if (!hidden(((Cullable) entity).ladsCullGen(), originX, originY, originZ)) return false;
        culled++;
        if (audit) auditEntity(entity);
        return true;
    }

    /** TileEntityRendererDispatcher.renderTileEntity: true to skip it. Every block entity drawn is marked seen for the thread. */
    public static boolean skip(TileEntity tile, int destroyStage, double originX, double originY, double originZ) {
        if (!enabled) return false;
        Cullable cullable = (Cullable) tile;
        cullable.ladsSeen(frame);
        if (destroyStage >= 0 || inShadowPass() || !hidden(cullable.ladsCullGen(), originX, originY, originZ)) return false;
        culledTiles++;
        return true;
    }

    private static boolean hidden(int gen, double originX, double originY, double originZ) {
        if (cameraFrame != frame) frameCamera(originX, originY, originZ);
        return gen != 0 && (gen == doneGen && doneOk || gen == runGen && runOk);
    }

    /**
     * Once per frame, at the first entity or block entity drawn: the camera's world position, from the modelview matrix the
     * world is drawn with (bobbing, third person and other camera mods included), and which passes still hold for it.
     */
    private static void frameCamera(double originX, double originY, double originZ) {
        cameraFrame = frame;
        doneOk = runOk = false;
        MODELVIEW.clear();
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, MODELVIEW);
        MODELVIEW.get(MATRIX);
        double[] eye = new double[3];
        if (!Occlusion.cameraOf(MATRIX, eye)) return;
        eye[0] += originX; eye[1] += originY; eye[2] += originZ;
        camera = eye;
        long now = System.nanoTime();
        Pass d = done, r = running;
        if (d != null) { doneGen = d.gen; doneOk = d.holds(eye, now); }
        if (r != null) { runGen = r.gen; runOk = r.holds(eye, now); }
        drawnFrames++;
        if (doneOk || runOk) trustedFrames++;
    }

    /** OptiFine's shadow pass draws entities from the sun: the camera's culling must not apply there. */
    private static boolean inShadowPass() {
        if (!shadowLooked) {
            shadowLooked = true;
            try { shadowPass = Class.forName("net.optifine.shaders.Shaders").getField("isShadowPass"); }
            catch (Throwable notOptiFine) { shadowPass = null; }
            LOG.info("Entity Culling: OptiFine shadow pass {}", shadowPass != null ? "found" : "absent");
        }
        try { return shadowPass != null && shadowPass.getBoolean(null); } catch (Throwable unreadable) { return true; }
    }

    // ---- culling thread ----

    private static void loop() {
        Grid grid = new Grid();
        double[] eyes = new double[27], last = null;
        long lastPass = 0;
        int gen = 0, failures = 0;
        while (true) {
            try {
                Thread.sleep(10);
                double[] eye = camera;
                Snapshot snap = snapshot;
                if (!enabled || eye == null || snap == null) continue;
                long now = System.nanoTime();
                boolean moved = last == null || Math.abs(eye[0] - last[0]) + Math.abs(eye[1] - last[1]) + Math.abs(eye[2] - last[2]) > 0.05;
                if (!moved && now - lastPass < 50_000_000L) continue;
                last = eye;
                lastPass = now;
                if (++gen == 0) gen = 1;
                Pass pass = new Pass(gen, eye, now);
                running = pass;
                grid.snap = snap;
                grid.sx = Integer.MIN_VALUE;
                pass(grid, snap, eye, Occlusion.eyes(grid, eye[0], eye[1], eye[2], EYE_CUBE, eyes), eyes, gen);
                done = pass;
                passNanos += System.nanoTime() - now;
                passes++;
            } catch (InterruptedException stop) {
                return;
            } catch (Throwable failure) { // a race with the game thread: this pass is dropped, everything draws until the next
                running = null;
                if (failures++ < 5) LOG.warn("Entity Culling pass failed; drawing everything until the next pass", failure);
            }
        }
    }

    private static void pass(Grid grid, Snapshot snap, double[] eye, int eyeCount, double[] eyes, int gen) {
        Minecraft mc = Minecraft.getMinecraft();
        Entity player = mc.thePlayer, view = mc.getRenderViewEntity();
        List<Entity> entities = snap.world.loadedEntityList;
        for (int i = 0; i < entities.size(); i++) {
            Entity entity;
            try { entity = entities.get(i); } catch (IndexOutOfBoundsException shrunk) { break; }
            if (entity == null) continue;
            ((Cullable) entity).ladsCullGen(eyeCount > 0 && cullable(entity, player, view) && hidden(grid, entity, eye, eyes, eyeCount) ? gen : 0);
        }
        List<TileEntity> tiles = snap.world.loadedTileEntityList;
        int now = frame;
        for (int i = 0; i < tiles.size(); i++) {
            TileEntity tile;
            try { tile = tiles.get(i); } catch (IndexOutOfBoundsException shrunk) { break; }
            if (tile == null) continue;
            Cullable cullable = (Cullable) tile;
            if (now - cullable.ladsSeen() > SEEN_FRAMES) { cullable.ladsCullGen(0); continue; } // not drawn lately: not worth a trace
            cullable.ladsCullGen(eyeCount > 0 && hidden(grid, tile, eye, eyes, eyeCount) ? gen : 0);
        }
    }

    private static boolean cullable(Entity entity, Entity player, Entity view) {
        if (entity == player || entity == view || entity.ignoreFrustumCheck) return false;
        if (player != null && (player.ridingEntity == entity || player.riddenByEntity == entity)) return false;
        // Drawn beyond their own box: leads, fishing lines, guardian beams, boss beams and bars.
        if (entity instanceof EntityLiving && ((EntityLiving) entity).getLeashed()) return false;
        return !(entity instanceof EntityFishHook || entity instanceof EntityGuardian || entity instanceof EntityDragon || entity instanceof EntityWither);
    }

    private static boolean hidden(Grid grid, Entity entity, double[] eye, double[] eyes, int eyeCount) {
        AxisAlignedBB box = entity.getEntityBoundingBox();
        if (box == null || box.maxX - box.minX > MAX_SIZE || box.maxY - box.minY > MAX_SIZE || box.maxZ - box.minZ > MAX_SIZE) return false;
        if (!inRange(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ, eye)) return false;
        // Where it is drawn between ticks (back to the last tick's position) and where it is heading while the pass is trusted,
        // then room for held items, capes, cosmetics, the name tag and the shadow.
        double dx = entity.posX - entity.lastTickPosX, dy = entity.posY - entity.lastTickPosY, dz = entity.posZ - entity.lastTickPosZ;
        return !Occlusion.visible(grid, eyes, eyeCount,
            box.minX + Math.min(-Math.max(0, dx), AHEAD * dx) - SIDE, box.minY + Math.min(-Math.max(0, dy), AHEAD * dy) - DOWN,
            box.minZ + Math.min(-Math.max(0, dz), AHEAD * dz) - SIDE, box.maxX + Math.max(-Math.min(0, dx), AHEAD * dx) + SIDE,
            box.maxY + Math.max(-Math.min(0, dy), AHEAD * dy) + UP, box.maxZ + Math.max(-Math.min(0, dz), AHEAD * dz) + SIDE);
    }

    private static boolean hidden(Grid grid, TileEntity tile, double[] eye, double[] eyes, int eyeCount) {
        if (tile instanceof TileEntityBeacon || tile.isInvalid()) return false; // the beam is seen from afar
        BlockPos pos = tile.getPos();
        double x0 = pos.getX(), y0 = pos.getY(), z0 = pos.getZ(), x1 = x0 + 1, y1 = y0 + 1, z1 = z0 + 1;
        if (tile instanceof TileEntityChest) { x1++; z1++; } // a double chest is drawn by one of its halves
        else if (tile instanceof TileEntityBanner) y1++; // standing banners are two blocks tall
        else if (tile instanceof TileEntityPiston) { x0--; y0--; z0--; x1++; y1++; z1++; }
        if (!inRange(x0, y0, z0, x1, y1, z1, eye)) return false;
        return !Occlusion.visible(grid, eyes, eyeCount, x0 - BLOCK_MARGIN, y0 - BLOCK_MARGIN, z0 - BLOCK_MARGIN,
            x1 + BLOCK_MARGIN, y1 + BLOCK_MARGIN, z1 + BLOCK_MARGIN);
    }

    /** Between 2 and 128 blocks from the eye (nearest point of the box). */
    private static boolean inRange(double x0, double y0, double z0, double x1, double y1, double z1, double[] eye) {
        double dx = Math.max(0, Math.max(x0 - eye[0], eye[0] - x1)), dy = Math.max(0, Math.max(y0 - eye[1], eye[1] - y1));
        double dz = Math.max(0, Math.max(z0 - eye[2], eye[2] - z1)), d2 = dx * dx + dy * dy + dz * dz;
        return d2 >= NEAR * NEAR && d2 <= FAR * FAR;
    }

    /** Opaque full cubes by block-state id (as chunk sections store them). Leaves are left out: OptiFine draws them see-through. */
    private static boolean[] opaqueTable() {
        boolean[] table = new boolean[65536];
        for (int id = 0; id < table.length; id++) {
            IBlockState state = Block.BLOCK_STATE_IDS.getByValue(id);
            if (state == null) continue;
            Block block = state.getBlock();
            table[id] = block.isOpaqueCube() && block.getMaterial() != Material.leaves;
        }
        return table;
    }

    private static long section(int x, int y, int z) { return ((long) x & 0x3FFFFFF) << 30 | ((long) z & 0x3FFFFFF) << 4 | (y & 15); }

    /** The snapshot's cells, one 16x16x16 section cached at a time. Unknown, unsettled or changed sections are open air. */
    private static final class Grid implements Occlusion.Grid {
        Snapshot snap;
        int sx, sy, sz;
        char[] data;

        public boolean opaque(int x, int y, int z) {
            if (y < 0 || y > 255) return false;
            int cx = x >> 4, cy = y >> 4, cz = z >> 4;
            if (cx != sx || cy != sy || cz != sz) { sx = cx; sy = cy; sz = cz; data = load(cx, cy, cz); }
            if (data == null) return false;
            int id = data[(y & 15) << 8 | (z & 15) << 4 | (x & 15)];
            return snap.opaque[id];
        }

        private char[] load(int cx, int cy, int cz) {
            int i = cx - snap.x0, j = cz - snap.z0;
            if (i < 0 || j < 0 || i >= snap.size || j >= snap.size) return null;
            Chunk chunk = snap.chunks[i * snap.size + j];
            if (chunk == null || CHANGED.containsKey(EntityCulling189.section(cx, cy, cz))) return null;
            ExtendedBlockStorage storage = chunk.getBlockStorageArray()[cy];
            return storage == null ? null : storage.getData();
        }
    }

    // ---- QA ----

    /** Auditing: would the current camera alone (no cube, no lag) see this culled entity? Counted as wrong. */
    private static void auditEntity(Entity entity) {
        Snapshot snap = snapshot;
        double[] eye = camera;
        if (snap == null || eye == null) return;
        Grid grid = new Grid();
        grid.snap = snap;
        grid.sx = Integer.MIN_VALUE;
        double[] one = new double[27];
        int n = Occlusion.eyes(grid, eye[0], eye[1], eye[2], 0, one);
        if (n == 0) { wrong++; return; }
        AxisAlignedBB box = entity.getEntityBoundingBox();
        if (Occlusion.visible(grid, one, 1, box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ)) wrong++;
    }

    static double[] cameraForQa() { return camera; }
}
