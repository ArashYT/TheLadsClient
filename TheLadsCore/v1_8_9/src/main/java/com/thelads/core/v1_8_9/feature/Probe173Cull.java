package com.thelads.core.v1_8_9.feature;

import static com.thelads.core.v1_8_9.feature.CoreProbe.after;
import static com.thelads.core.v1_8_9.feature.CoreProbe.check;
import static com.thelads.core.v1_8_9.feature.CoreProbe.retry;

import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.entity.passive.EntityCow;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * QA only (LADS_VERIFY_189_ONLY=cull): Entity Culling in a crowd behind a wall. A stone wall, 100 named players (client side),
 * 40 named armour stands, 15 cows and 25 chests behind it. (1) Six fixed views photographed with the module off and on
 * (lads-qa/screenshots/cull-*.png; artifacts compare them pixel by pixel) and the culled draws counted; (2) eight seconds of
 * fast turning and strafing round the wall's end with every culled draw audited against the current camera alone (none may be
 * visible from it); (3) module off and on in turn, at the QA cap (or, LADS_CULL_UNCAPPED=1 in a run alone, uncapped at
 * render distance 8 and 12): FPS and the work per frame with the crowd hidden, the crowd in view, and 3000+ particles behind the camera. Everything is put back.
 */
final class Probe173Cull {
    private static final Logger LOG = LogManager.getLogger("TheLadsCore");
    private static final int PLAYERS = 100, STANDS = 40, COWS = 15, CHESTS = 25;
    static final List<CoreProbe.Step> STEPS = Arrays.<CoreProbe.Step>asList(Probe173Cull::setup, Probe173Cull::views,
        Probe173Cull::motion, mc -> bench(mc, 0), mc -> bench(mc, 1), mc -> bench(mc, 2), mc -> bench(mc, 3), Probe173Cull::restore);
    private static final JsonObject report = new JsonObject();
    private static final Map<Module, Boolean> hudWas = new LinkedHashMap<Module, Boolean>();
    private static final List<BlockPos> placed = new ArrayList<BlockPos>();
    private static final List<EntityOtherPlayerMP> crowd = new ArrayList<EntityOtherPlayerMP>();
    private static int px, gy, pz, renderWas, limitWas, cloudsWas, ofCloudsWas = -1, step, ticks;
    private static boolean cullWas, vsyncWas;
    private static double homeX, homeY, homeZ;
    private static Frames frames;

    private Probe173Cull() {}

    private static boolean setup(Minecraft mc) {
        Module culling = Options189.module(EntityCulling189.MODULE);
        check(culling != null && culling.isEnabled(), "Entity Culling is a module, on by default");
        cullWas = culling.isEnabled();
        for (Module module : ModuleManager.getInstance().getModules()) // a still HUD (FPS, clocks and Autohide change between shots)
            if (module.getCategory() == Module.Category.HUD && module.isEnabled()) { hudWas.put(module, true); module.setEnabled(false); }
        renderWas = mc.gameSettings.renderDistanceChunks;
        limitWas = mc.gameSettings.limitFramerate;
        vsyncWas = mc.gameSettings.enableVsync;
        cloudsWas = mc.gameSettings.clouds;
        mc.gameSettings.clouds = 0;
        try { // OptiFine's own Clouds option (3: off) decides over vanilla's
            java.lang.reflect.Field of = mc.gameSettings.getClass().getField("ofClouds");
            ofCloudsWas = of.getInt(mc.gameSettings);
            of.setInt(mc.gameSettings, 3);
        } catch (ReflectiveOperationException notOptiFine) { ofCloudsWas = -1; }
        homeX = mc.thePlayer.posX; homeY = mc.thePlayer.posY; homeZ = mc.thePlayer.posZ;
        px = (int) Math.floor(homeX);
        pz = (int) Math.floor(homeZ);
        gy = mc.theWorld.getHeight(new BlockPos(px, 0, pz)).getY();
        mc.thePlayer.capabilities.isFlying = true;
        // No "Press E" tutorial toast in the photos.
        mc.thePlayer.getStatFileWriter().unlockAchievement(mc.thePlayer, net.minecraft.stats.AchievementList.openInventory, 1);
        mc.guiAchievement.clearAchievements();
        onServer(mc, player -> {
            World world = player.worldObj;
            for (int z = pz - 15; z <= pz + 15; z++) for (int y = gy; y <= gy + 8; y++) place(world, new BlockPos(px + 6, y, z), Blocks.stone);
            for (int i = 0; i < CHESTS; i++)
                place(world, new BlockPos(px + (i < 20 ? 8 : 25), gy, pz - 10 + (i < 20 ? i : 2 * (i - 20))), i < 20 ? Blocks.chest : Blocks.ender_chest);
            for (int i = 0; i < STANDS; i++) {
                EntityArmorStand stand = new EntityArmorStand(world, px + 10.5 + (i % 8) * 1.8, gy, pz - 9.2 + (i / 8) * 4.1);
                stand.setCustomNameTag("Stand " + i);
                stand.setAlwaysRenderNameTag(true);
                stand.getEntityData().setBoolean("ladsCullQa", true);
                world.spawnEntityInWorld(stand);
            }
            for (int i = 0; i < COWS; i++) {
                EntityCow cow = new EntityCow(world);
                cow.setLocationAndAngles(px + 12 + (i % 5) * 3, gy, pz + 11 + (i / 5) * 1.5, 90, 0);
                NBTTagCompound tag = new NBTTagCompound();
                cow.writeToNBT(tag);
                tag.setByte("NoAI", (byte) 1);
                tag.setByte("Silent", (byte) 1);
                cow.readFromNBT(tag);
                cow.getEntityData().setBoolean("ladsCullQa", true);
                world.spawnEntityInWorld(cow);
            }
            player.playerNetServerHandler.setPlayerLocation(px + 0.5, gy, pz + 0.5, -90, 0);
        });
        for (String command : new String[] {"time set 6000", "gamerule doDaylightCycle false", "weather clear 100000", "difficulty 0"}) command(mc, command);
        for (int i = 0; i < PLAYERS; i++) { // facing the wall, half of them armed and armoured
            EntityOtherPlayerMP fake = new EntityOtherPlayerMP(mc.theWorld, new GameProfile(UUID.randomUUID(), "Crowd" + i));
            fake.setPositionAndRotation(px + 10.5 + (i % 10) * 1.5, gy, pz - 7 + (i / 10) * 1.5, 90, 0);
            fake.rotationYawHead = fake.prevRotationYawHead = fake.renderYawOffset = fake.prevRenderYawOffset = 90;
            if (i % 2 == 0) {
                fake.setCurrentItemOrArmor(0, new ItemStack(Items.diamond_sword));
                fake.setCurrentItemOrArmor(4, new ItemStack(Items.iron_helmet));
                fake.setCurrentItemOrArmor(3, new ItemStack(Items.diamond_chestplate));
            }
            mc.theWorld.addEntityToWorld(-20000 - i, fake);
            crowd.add(fake);
        }
        frames = new Frames();
        frames.pin(px + 0.5, gy + 1, pz + 0.5, -90, 0);
        MinecraftForge.EVENT_BUS.register(frames);
        LOG.info("Lads cull QA BEGIN: wall at x={}, {} players, {} stands, {} cows, {} chests behind it", px + 6, PLAYERS, STANDS, COWS, CHESTS);
        return after(60);
    }

    /** name, x and y offset, z offset, yaw, pitch. */
    private static final Object[][] VIEWS = {
        {"front", 0.5, 1.0, 0.5, -90f, 0f}, {"left", 0.5, 1.0, 0.5, -60f, 0f}, {"right", 0.5, 1.0, 0.5, -120f, 0f},
        {"close", 4.5, 1.0, 0.5, -90f, 10f}, {"over", 0.5, 15.0, 0.5, -90f, 35f}, {"edge", 3.5, 1.0, 17.5, -143f, 0f},
        {"over2", 0.5, 15.0, 0.5, -90f, 35f}}; // "over" again: how far the crowd's own picture varies between shots

    /** Each view with the module off, then on: settle, count culled draws over 20 ticks, photograph. */
    private static boolean views(Minecraft mc) {
        int view = step / 2;
        if (view >= VIEWS.length) {
            boolean shaders = shaders();
            report.addProperty("shaders", shaders);
            report.addProperty("shadowPassDraws", EntityCulling189.shadowDraws);
            LOG.info("Lads cull QA: OptiFine shaders {}, {} entity draws in the shadow pass (never culled)", shaders ? "on" : "off", EntityCulling189.shadowDraws);
            if (shaders) check(EntityCulling189.shadowDraws > 0, "with shaders on, the shadow pass draws entities without the camera's culling");
            step = 0;
            return after(1);
        }
        boolean on = step % 2 == 1;
        Object[] v = VIEWS[view];
        if (ticks == 0) {
            Options189.module(EntityCulling189.MODULE).setEnabled(on);
            frames.pin(px + (Double) v[1], gy + (Double) v[2], pz + (Double) v[3], (Float) v[4], (Float) v[5]);
        }
        if (++ticks == 20) {
            EntityCulling189.culled = 0; EntityCulling189.culledTiles = 0; frames.count = 0;
            EntityCulling189.drawnFrames = 0; EntityCulling189.trustedFrames = 0;
            EntityCulling189.passes = 0; EntityCulling189.passNanos = 0;
        }
        if (ticks < 40) return retry(1);
        String name = "cull-" + v[0] + "-" + (on ? "on" : "off");
        double entities = EntityCulling189.culled / (double) Math.max(1, frames.count), tiles = EntityCulling189.culledTiles / (double) Math.max(1, frames.count);
        capture(mc, name);
        JsonObject entry = new JsonObject();
        entry.addProperty("cullPassMs", EntityCulling189.passNanos / 1e6 / Math.max(1, EntityCulling189.passes));
        entry.addProperty("frames", frames.count);
        entry.addProperty("worldFrames", EntityCulling189.drawnFrames);
        entry.addProperty("trustedFrames", EntityCulling189.trustedFrames);
        entry.addProperty("culledEntitiesPerFrame", entities);
        entry.addProperty("culledBlockEntitiesPerFrame", tiles);
        report.add(name, entry);
        LOG.info("Lads cull QA view {}: {} entities and {} block entities culled per frame, {}", name, entities, tiles, entry);
        if (!on) check(entities == 0 && tiles == 0, "Entity Culling off culls nothing (" + name + ")");
        else if (v[0].equals("front")) check(entities >= 0.75 * (PLAYERS + STANDS + COWS) && tiles >= CHESTS * 0.75,
            "the crowd and chests behind the wall are culled from the front (" + entities + " entities, " + tiles + " block entities per frame)");
        else if (((String) v[0]).startsWith("over")) check(entities < 0.1 * (PLAYERS + STANDS + COWS), "seen from above the wall the crowd is drawn (" + entities + " culled)");
        ticks = 0;
        step++;
        return retry(1);
    }

    /** Eight seconds of fast turning and strafing round the wall's end, every culled draw audited. */
    private static boolean motion(Minecraft mc) {
        if (ticks++ == 0) {
            Options189.module(EntityCulling189.MODULE).setEnabled(true);
            EntityCulling189.culled = 0;
            EntityCulling189.wrong = 0;
            EntityCulling189.audit = true;
            frames.moving = System.nanoTime();
            frames.count = 0;
        }
        if (ticks < 160) return retry(1);
        frames.moving = 0;
        EntityCulling189.audit = false;
        JsonObject entry = new JsonObject();
        entry.addProperty("frames", frames.count);
        entry.addProperty("culledDraws", EntityCulling189.culled);
        entry.addProperty("culledDrawsVisibleFromCamera", EntityCulling189.wrong);
        report.add("motion", entry);
        LOG.info("Lads cull QA motion: {} frames, {} culled draws, {} of them visible from the camera", frames.count, EntityCulling189.culled, EntityCulling189.wrong);
        check(EntityCulling189.wrong == 0, "fast turning and strafing: no culled draw was visible from the camera (" + EntityCulling189.culled + " culled draws)");
        check(EntityCulling189.culled > 0, "culling keeps working while moving");
        ticks = 0;
        return after(1);
    }

    /**
     * Render distance, view and particles behind the camera, uncapped (FPS unlimited, VSync off: the user's benchmark
     * exception, sandbox only, run alone). Each run measures FPS and 1% low from frame-to-frame times, the work per frame
     * (render-thread ms from RenderTickEvent START to END: world, entities, particles, HUD; not the tick or the swap) and GPU ms.
     * Runs alternate off, on, off, on, off, on; medians per state.
     */
    private static final Object[][] BENCH = {{8, "front", false}, {12, "front", false}, {8, "over", false}, {8, "front", true}};
    /** LADS_CULL_UNCAPPED=1 (a run alone, in the final benchmark): FPS unlimited at render distance 8 and 12; otherwise the QA cap. */
    private static final boolean UNCAPPED = "1".equals(System.getenv("LADS_CULL_UNCAPPED"));
    private static final int RUNS = 6, RUN_TICKS = 160, WARM_TICKS = 40;
    private static final Map<String, List<double[]>> RESULTS = new LinkedHashMap<String, List<double[]>>();
    private static Probe170r.GpuTimer gpu;

    private static boolean bench(Minecraft mc, int scene) {
        if ("0".equals(System.getenv("LADS_CULL_BENCH"))) return true; // views and motion only
        Object[] b = BENCH[scene];
        if (!UNCAPPED && scene == 1) return true; // render distance 12 only uncapped
        String sceneName = (UNCAPPED ? "rd" + b[0] : "capped") + "-" + b[1] + ((Boolean) b[2] ? "-particles" : "");
        if (step == RUNS) {
            for (String state : new String[] {"off", "on"}) {
                List<double[]> runs = RESULTS.get(sceneName + "-" + state);
                JsonObject median = new JsonObject();
                String[] keys = {"workP50Ms", "workP99Ms", "workMaxMs", "workMeanMs", "gpuMs", "avgFps", "low1Fps"};
                for (int k = 0; k < keys.length; k++) {
                    double[] values = new double[runs.size()];
                    for (int i = 0; i < values.length; i++) values[i] = runs.get(i)[k];
                    Arrays.sort(values);
                    median.addProperty(keys[k], values[values.length / 2]);
                }
                report.add(sceneName + "-" + state + "-median", median);
                LOG.info("Lads cull QA bench {} {} (median of {} runs{}): {}", sceneName, state, runs.size(), UNCAPPED ? ", uncapped" : ", at the QA cap", median);
            }
            step = 0;
            return after(1);
        }
        Object[] v = b[1].equals("over") ? VIEWS[4] : VIEWS[0];
        boolean on = step % 2 == 1, particles = (Boolean) b[2];
        if (ticks == 0) {
            if (UNCAPPED) { // otherwise the harness's cap and render distance
                mc.gameSettings.renderDistanceChunks = (Integer) b[0];
                mc.gameSettings.limitFramerate = 260; // unlimited
                mc.gameSettings.enableVsync = false;
                org.lwjgl.opengl.Display.setVSyncEnabled(false);
            }
            if (gpu == null) { gpu = new Probe170r.GpuTimer(); MinecraftForge.EVENT_BUS.register(gpu); }
            Options189.module(EntityCulling189.MODULE).setEnabled(on);
            frames.pin(px + (Double) v[1], gy + (Double) v[2], pz + (Double) v[3], (Float) v[4], (Float) v[5]);
        }
        if (particles) for (int i = 0; i < 150; i++) // behind the camera (it looks east)
            mc.theWorld.spawnParticle(EnumParticleTypes.REDSTONE, px - 8 + Math.random() * 6, gy + Math.random() * 6, pz - 6 + Math.random() * 12, 0, 0, 0);
        int warm = step == 0 ? WARM_TICKS * 3 : WARM_TICKS; // the first run of a scene also waits for chunks
        if (++ticks == warm) {
            frames.record();
            gpu.reset();
            Particles189.drawn = Particles189.skipped = 0;
            EntityCulling189.passes = 0;
            EntityCulling189.passNanos = 0;
        }
        if (ticks < warm + RUN_TICKS) return retry(1);
        String name = sceneName + "-" + (on ? "on" : "off") + (step / 2 + 1);
        double[] work = frames.stats();
        JsonObject entry = new JsonObject();
        entry.addProperty("frames", (int) work[5]);
        entry.addProperty("workP50Ms", work[0]);
        entry.addProperty("workP99Ms", work[1]);
        entry.addProperty("workMaxMs", work[2]);
        entry.addProperty("workMeanMs", work[3]);
        entry.addProperty("gpuMs", gpu.milliseconds());
        entry.addProperty("avgFps", work[6]);
        entry.addProperty("low1Fps", work[7]);
        if (particles) { entry.addProperty("particlesDrawn", Particles189.drawn); entry.addProperty("particlesSkipped", Particles189.skipped); }
        if (on) entry.addProperty("cullPassMs", EntityCulling189.passNanos / 1e6 / Math.max(1, EntityCulling189.passes));
        report.add(name, entry);
        String key = sceneName + "-" + (on ? "on" : "off");
        if (!RESULTS.containsKey(key)) RESULTS.put(key, new ArrayList<double[]>());
        RESULTS.get(key).add(new double[] {work[0], work[1], work[2], work[3], gpu.milliseconds(), work[6], work[7]});
        LOG.info("Lads cull QA bench {}: {}", name, entry);
        ticks = 0;
        step++;
        return retry(1);
    }

    private static boolean restore(Minecraft mc) throws Exception {
        MinecraftForge.EVENT_BUS.unregister(frames);
        if (gpu != null) { MinecraftForge.EVENT_BUS.unregister(gpu); gpu.close(); gpu = null; }
        Options189.module(EntityCulling189.MODULE).setEnabled(cullWas);
        hudWas.forEach(Module::setEnabled);
        mc.gameSettings.clouds = cloudsWas;
        mc.gameSettings.renderDistanceChunks = renderWas;
        mc.gameSettings.limitFramerate = limitWas;
        mc.gameSettings.enableVsync = vsyncWas;
        org.lwjgl.opengl.Display.setVSyncEnabled(vsyncWas);
        if (ofCloudsWas >= 0) mc.gameSettings.getClass().getField("ofClouds").setInt(mc.gameSettings, ofCloudsWas);
        for (int i = 0; i < crowd.size(); i++) mc.theWorld.removeEntityFromWorld(-20000 - i);
        crowd.clear();
        onServer(mc, player -> {
            for (Entity entity : new ArrayList<Entity>(player.worldObj.loadedEntityList))
                if (entity.getEntityData().getBoolean("ladsCullQa")) entity.setDead();
            for (BlockPos pos : placed) player.worldObj.setBlockToAir(pos);
            player.playerNetServerHandler.setPlayerLocation(homeX, homeY, homeZ, -90, 0);
        });
        command(mc, "gamerule doDaylightCycle true");
        File folder = new File(mc.mcDataDir, "lads-qa/cull");
        folder.mkdirs();
        Files.write(new File(folder, "cull.json").toPath(), report.toString().getBytes(StandardCharsets.UTF_8));
        LOG.info("Lads cull QA END: {}", report);
        return after(20);
    }

    /** The last frame from the framebuffer (not ScreenShotHelper: the Lads screenshot preview would show in the next one). */
    private static void capture(Minecraft mc, String name) {
        int w = mc.displayWidth, h = mc.displayHeight, tw = mc.getFramebuffer().framebufferTextureWidth, th = mc.getFramebuffer().framebufferTextureHeight;
        java.nio.IntBuffer buffer = org.lwjgl.BufferUtils.createIntBuffer(tw * th);
        org.lwjgl.opengl.GL11.glPixelStorei(org.lwjgl.opengl.GL11.GL_PACK_ALIGNMENT, 1);
        net.minecraft.client.renderer.GlStateManager.bindTexture(mc.getFramebuffer().framebufferTexture);
        org.lwjgl.opengl.GL11.glGetTexImage(org.lwjgl.opengl.GL11.GL_TEXTURE_2D, 0, org.lwjgl.opengl.GL12.GL_BGRA,
            org.lwjgl.opengl.GL12.GL_UNSIGNED_INT_8_8_8_8_REV, buffer);
        java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_RGB);
        int[] row = new int[w];
        for (int y = 0; y < h; y++) { buffer.position(y * tw); buffer.get(row); image.setRGB(0, h - 1 - y, w, 1, row, 0, w); }
        File folder = new File(mc.mcDataDir, "lads-qa/screenshots");
        folder.mkdirs();
        try { javax.imageio.ImageIO.write(image, "png", new File(folder, name + ".png")); }
        catch (java.io.IOException failure) { throw new IllegalStateException("cull QA: could not save " + name, failure); }
    }

    private static boolean shaders() {
        try { return (Boolean) Class.forName("Config").getMethod("isShaders").invoke(null); } catch (Throwable notOptiFine) { return false; }
    }

    private static void place(World world, BlockPos pos, net.minecraft.block.Block block) {
        if (!world.isAirBlock(pos)) return;
        world.setBlockState(pos, block.getDefaultState());
        placed.add(pos);
    }

    /** Each frame: the camera pinned to the view (or moving on the motion path), and the work per frame while recording. */
    public static final class Frames {
        int count;
        long moving, start;
        private double x, y, z;
        private float yaw, pitch;
        private final List<Long> times = new ArrayList<Long>();
        private boolean recording;

        void pin(double x, double y, double z, float yaw, float pitch) { this.x = x; this.y = y; this.z = z; this.yaw = yaw; this.pitch = pitch; }

        private final List<Long> gaps = new ArrayList<Long>();
        private long lastStart;

        void record() { times.clear(); gaps.clear(); lastStart = 0; recording = true; }

        /** Work per frame (START to END): p50, p99, max and mean ms, unused, frame count; then average FPS and 1% low FPS. */
        double[] stats() {
            recording = false;
            long[] t = new long[times.size()];
            long total = 0;
            for (int i = 0; i < t.length; i++) { t[i] = times.get(i); total += t[i]; }
            Arrays.sort(t);
            if (t.length == 0) return new double[8];
            long[] g = new long[gaps.size()];
            long gapTotal = 0;
            for (int i = 0; i < g.length; i++) { g[i] = gaps.get(i); gapTotal += g[i]; }
            Arrays.sort(g);
            int worst = Math.max(1, g.length / 100);
            long worstSum = 0;
            for (int i = g.length - worst; i < g.length; i++) worstSum += g[i];
            return new double[] {t[t.length / 2] / 1e6, t[Math.min(t.length - 1, (int) (t.length * 0.99))] / 1e6, t[t.length - 1] / 1e6,
                total / 1e6 / t.length, 0, t.length, g.length * 1e9 / Math.max(1, gapTotal), worst * 1e9 / Math.max(1, worstSum)};
        }

        @SubscribeEvent
        public void frameEnd(TickEvent.RenderTickEvent event) {
            if (event.phase == TickEvent.Phase.END && recording && start != 0) times.add(System.nanoTime() - start);
        }

        @SubscribeEvent
        public void frame(TickEvent.RenderTickEvent event) {
            if (event.phase != TickEvent.Phase.START) return;
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.thePlayer == null) return;
            long now = System.nanoTime();
            if (recording && lastStart != 0) gaps.add(now - lastStart);
            lastStart = now;
            start = now;
            count++;
            double px = x, py = y, pz = z;
            float yaw = this.yaw, pitch = this.pitch;
            if (moving != 0) { // strafing past the wall's end at up to 11 blocks/s while flicking the view up to 1000 degrees/s
                double t = (now - moving) / 1e9;
                px = Probe173Cull.px + 2.5 + 1.5 * Math.sin(t * 1.3);
                py = gy + 1;
                pz = Probe173Cull.pz + 14 + 5 * Math.sin(t * 2.2);
                yaw = (float) (-90 + 170 * Math.sin(t * 6));
                pitch = (float) (10 * Math.sin(t * 3));
            }
            mc.thePlayer.capabilities.isFlying = true;
            mc.thePlayer.setPosition(px, py, pz);
            mc.thePlayer.prevPosX = mc.thePlayer.lastTickPosX = px;
            mc.thePlayer.prevPosY = mc.thePlayer.lastTickPosY = py;
            mc.thePlayer.prevPosZ = mc.thePlayer.lastTickPosZ = pz;
            mc.thePlayer.motionX = mc.thePlayer.motionY = mc.thePlayer.motionZ = 0;
            mc.thePlayer.rotationYaw = mc.thePlayer.prevRotationYaw = yaw;
            mc.thePlayer.rotationPitch = mc.thePlayer.prevRotationPitch = pitch;
        }
    }

    private interface ServerTask { void run(EntityPlayerMP player); }

    private static void onServer(Minecraft mc, ServerTask task) {
        MinecraftServer server = mc.getIntegratedServer();
        UUID id = mc.thePlayer.getUniqueID();
        server.addScheduledTask(() -> task.run(server.getConfigurationManager().getPlayerByUUID(id)));
    }

    private static void command(Minecraft mc, String command) {
        MinecraftServer server = mc.getIntegratedServer();
        server.addScheduledTask(() -> server.getCommandManager().executeCommand(server, command));
    }
}
