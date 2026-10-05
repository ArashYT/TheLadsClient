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
import org.lwjgl.opengl.Display;

/**
 * QA only (LADS_VERIFY_189_ONLY=cull): Entity Culling in a crowd behind a wall. A stone wall, 100 named players (client side),
 * 40 named armour stands, 15 cows and 25 chests behind it. (1) Six fixed views photographed with the module off and on
 * (lads-qa/screenshots/cull-*.png; artifacts compare them pixel by pixel) and the culled draws counted; (2) eight seconds of
 * fast turning and strafing round the wall's end with every culled draw audited against the current camera alone (none may be
 * visible from it); (3) frame times, uncapped at render distance 8 and 12 (the user's benchmark exception, sandbox only), with
 * the module off and on: crowd hidden, crowd in view, and 3000+ particles behind the camera. Everything is put back.
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
        {"close", 4.5, 1.0, 0.5, -90f, 10f}, {"over", 0.5, 15.0, 0.5, -90f, 35f}, {"edge", 3.5, 1.0, 17.5, -143f, 0f}};

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
        if (++ticks == 20) { EntityCulling189.culled = 0; EntityCulling189.culledTiles = 0; frames.count = 0; }
        if (ticks < 40) return retry(1);
        String name = "cull-" + v[0] + "-" + (on ? "on" : "off");
        double entities = EntityCulling189.culled / (double) Math.max(1, frames.count), tiles = EntityCulling189.culledTiles / (double) Math.max(1, frames.count);
        CoreProbe.screenshot(mc, name);
        JsonObject entry = new JsonObject();
        entry.addProperty("culledEntitiesPerFrame", entities);
        entry.addProperty("culledBlockEntitiesPerFrame", tiles);
        report.add(name, entry);
        LOG.info("Lads cull QA view {}: {} entities and {} block entities culled per frame", name, entities, tiles);
        if (!on) check(entities == 0 && tiles == 0, "Entity Culling off culls nothing (" + name + ")");
        else if (v[0].equals("front")) check(entities >= 0.75 * (PLAYERS + STANDS + COWS) && tiles >= CHESTS * 0.75,
            "the crowd and chests behind the wall are culled from the front (" + entities + " entities, " + tiles + " block entities per frame)");
        else if (v[0].equals("over")) check(entities < 0.1 * (PLAYERS + STANDS + COWS), "seen from above the wall the crowd is drawn (" + entities + " culled)");
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

    /** render distance, view, particles behind the camera. Each run: module off, on, off, on. */
    private static final Object[][] BENCH = {{8, "front", false}, {12, "front", false}, {8, "over", false}, {8, "front", true}};
    private static final int RUN_TICKS = 120, WARM_TICKS = 40;

    private static boolean bench(Minecraft mc, int scene) {
        int run = step;
        if (run == 4) { step = 0; return after(1); }
        Object[] b = BENCH[scene];
        Object[] v = b[1].equals("over") ? VIEWS[4] : VIEWS[0];
        boolean on = run % 2 == 1, particles = (Boolean) b[2];
        if (ticks == 0) {
            mc.gameSettings.renderDistanceChunks = (Integer) b[0];
            mc.gameSettings.limitFramerate = 260; // unlimited
            mc.gameSettings.enableVsync = false;
            Display.setVSyncEnabled(false);
            Options189.module(EntityCulling189.MODULE).setEnabled(on);
            frames.pin(px + (Double) v[1], gy + (Double) v[2], pz + (Double) v[3], (Float) v[4], (Float) v[5]);
        }
        if (particles) for (int i = 0; i < 150; i++) // behind the camera (it looks east)
            mc.theWorld.spawnParticle(EnumParticleTypes.REDSTONE, px - 8 + Math.random() * 6, gy + Math.random() * 6, pz - 6 + Math.random() * 12, 0, 0, 0);
        int warm = run == 0 ? WARM_TICKS * 3 : WARM_TICKS; // the first run of a scene also waits for chunks
        if (++ticks == warm) { frames.record(); Particles189.drawn = Particles189.skipped = 0; EntityCulling189.passes = 0; EntityCulling189.passNanos = 0; }
        if (ticks < warm + RUN_TICKS) return retry(1);
        String name = "rd" + b[0] + "-" + b[1] + (particles ? "-particles" : "") + "-" + (on ? "on" : "off") + (run < 2 ? "1" : "2");
        JsonObject entry = frames.stats();
        if (particles) { entry.addProperty("particlesDrawn", Particles189.drawn); entry.addProperty("particlesSkipped", Particles189.skipped); }
        if (on) entry.addProperty("cullPassMs", EntityCulling189.passNanos / 1e6 / Math.max(1, EntityCulling189.passes));
        report.add(name, entry);
        LOG.info("Lads cull QA bench {}: {}", name, entry);
        ticks = 0;
        step++;
        return retry(1);
    }

    private static boolean restore(Minecraft mc) throws Exception {
        MinecraftForge.EVENT_BUS.unregister(frames);
        Options189.module(EntityCulling189.MODULE).setEnabled(cullWas);
        hudWas.forEach(Module::setEnabled);
        mc.gameSettings.renderDistanceChunks = renderWas;
        mc.gameSettings.limitFramerate = limitWas;
        mc.gameSettings.enableVsync = vsyncWas;
        Display.setVSyncEnabled(vsyncWas);
        mc.gameSettings.clouds = cloudsWas;
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

    private static boolean shaders() {
        try { return (Boolean) Class.forName("Config").getMethod("isShaders").invoke(null); } catch (Throwable notOptiFine) { return false; }
    }

    private static void place(World world, BlockPos pos, net.minecraft.block.Block block) {
        if (!world.isAirBlock(pos)) return;
        world.setBlockState(pos, block.getDefaultState());
        placed.add(pos);
    }

    /** Each frame: the camera pinned to the view (or moving on the motion path), and frame times while recording. */
    public static final class Frames {
        int count;
        long moving, last;
        private double x, y, z;
        private float yaw, pitch;
        private final List<Long> times = new ArrayList<Long>();
        private boolean recording;

        void pin(double x, double y, double z, float yaw, float pitch) { this.x = x; this.y = y; this.z = z; this.yaw = yaw; this.pitch = pitch; }

        void record() { times.clear(); recording = true; last = 0; }

        JsonObject stats() {
            recording = false;
            long[] t = new long[times.size()];
            long total = 0;
            for (int i = 0; i < t.length; i++) { t[i] = times.get(i); total += t[i]; }
            Arrays.sort(t);
            int worst = Math.max(1, t.length / 100);
            long worstSum = 0;
            for (int i = t.length - worst; i < t.length; i++) worstSum += t[i];
            JsonObject o = new JsonObject();
            o.addProperty("frames", t.length);
            o.addProperty("avgFps", t.length * 1e9 / Math.max(1, total));
            o.addProperty("onePercentLowFps", worst * 1e9 / Math.max(1, worstSum));
            o.addProperty("p50Ms", t.length == 0 ? 0 : t[t.length / 2] / 1e6);
            o.addProperty("p99Ms", t.length == 0 ? 0 : t[Math.min(t.length - 1, (int) (t.length * 0.99))] / 1e6);
            return o;
        }

        @SubscribeEvent
        public void frame(TickEvent.RenderTickEvent event) {
            if (event.phase != TickEvent.Phase.START) return;
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.thePlayer == null) return;
            long now = System.nanoTime();
            if (recording) { if (last != 0) times.add(now - last); last = now; }
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
