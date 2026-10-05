package com.thelads.core.v1_8_9.feature;

import static com.thelads.core.v1_8_9.feature.CoreProbe.after;
import static com.thelads.core.v1_8_9.feature.CoreProbe.check;
import static com.thelads.core.v1_8_9.feature.CoreProbe.retry;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraft.client.gui.GuiUtilRenderComponents;
import net.minecraft.client.renderer.entity.RendererLivingEntity;
import net.minecraft.client.renderer.entity.layers.LayerBipedArmor;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntitySign;
import net.minecraft.util.BlockPos;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

/**
 * QA only (LADS_VERIFY_189_ONLY=rx): RenderTweaks189 A/B in the QA world. Two scenes in front of a stone backdrop: "signs" (200
 * wall signs, four formatted lines each) and "crowd" (45 armour stands with name tags and 40 players in teams, all in
 * leather/iron/diamond armour, half enchanted). Uncapped FPS at render distance 8 and 12 (benchmark exception: sandbox only, run
 * with no other QA game), tweaks off and on in turn, 8 rounds of 4 s: medians of FPS, the work per frame (RenderTickEvent START to
 * END: p50/p99/max ms and render-thread CPU ms) and GPU ms.
 * Visual parity: the world (before the HUD and hand) read back with the tweaks off and on must match pixel for pixel, also after
 * sign lines changed. Per-call costs and correctness checks for each path. Results: lads-qa/rx/rx.json and lads-qa/rx/*.png.
 * Everything built, spawned and changed is put back.
 */
final class Probe173Rx {
    private static final Logger LOG = LogManager.getLogger("TheLadsCore");
    private static final int ROUNDS = 8, SETTLE_MS = 1000, MEASURE_MS = 4000, PLAYERS = 40;
    static final List<CoreProbe.Step> STEPS = Arrays.<CoreProbe.Step>asList(Probe173Rx::setup, Probe173Rx::built,
        mc -> parity(mc, "signs", 0), mc -> bench(mc, "signs", 8), mc -> bench(mc, "signs", 12), Probe173Rx::editSigns, mc -> parity(mc, "signs-edited", 0),
        mc -> parity(mc, "crowd", 180), mc -> bench(mc, "crowd", 8), mc -> bench(mc, "crowd", 12), Probe173Rx::calls, Probe173Rx::restore);
    private static final JsonObject report = new JsonObject();
    private static final List<BlockPos> placed = new ArrayList<BlockPos>();
    private static final List<EntityArmorStand> stands = new ArrayList<EntityArmorStand>();
    private static final List<EntityOtherPlayerMP> players = new ArrayList<EntityOtherPlayerMP>();
    private static final ThreadMXBean THREADS = ManagementFactory.getThreadMXBean();
    private static int limitWas, distanceWas, x0, z0, round = -1;
    private static int cloudsWas, ofCloudsWas = -1;
    private static boolean enabledWas, bobbingWas;
    private static float yaw;
    private static long roundStart;
    private static Frames frames;
    private static Probe170r.GpuTimer gpu;
    private static final List<List<double[]>> results = new ArrayList<List<double[]>>(); // per mode: each round's stats

    private Probe173Rx() {}

    private static boolean setup(Minecraft mc) {
        cloudsWas = mc.gameSettings.clouds;
        enabledWas = RenderTweaks189.enabled;
        limitWas = mc.gameSettings.limitFramerate;
        distanceWas = mc.gameSettings.renderDistanceChunks;
        mc.gameSettings.limitFramerate = 260; // unlimited: benchmark exception, sandbox only, run alone; put back in restore
        bobbingWas = mc.gameSettings.viewBobbing;
        mc.gameSettings.viewBobbing = false;
        mc.gameSettings.clouds = 0; // clouds drift between the parity frames (OptiFine has its own switch: Off is 3)
        ofCloudsWas = ofClouds(mc, 3);
        x0 = MathHelper.floor_double(mc.thePlayer.posX);
        z0 = MathHelper.floor_double(mc.thePlayer.posZ);
        for (String command : new String[] {"gamemode 1 @a", "difficulty 0", "gamerule doDaylightCycle false", "time set 6000", "weather clear 100000",
            "scoreboard teams add ladsRed", "scoreboard teams add ladsBlue", "scoreboard teams option ladsRed color red",
            "scoreboard teams option ladsBlue color blue"}) command(mc, command);
        onServer(mc, (server, player) -> {
            World world = player.worldObj;
            int y = world.getHeight(new BlockPos(x0, 0, z0)).getY();
            // What a failed earlier run left (the QA world is saved): its stands, signs and backdrops.
            for (Entity e : new ArrayList<Entity>(world.loadedEntityList))
                if (e instanceof EntityArmorStand && e.getName().startsWith("§6Hologram")) e.setDead();
            for (int dx = -16; dx <= 16; dx++) for (int dy = 0; dy < 20; dy++) for (int z : new int[] {z0 + 8, z0 + 9, z0 - 14}) {
                BlockPos pos = new BlockPos(x0 + dx, y + dy, z);
                if (world.getBlockState(pos).getBlock() == Blocks.stone || world.getBlockState(pos).getBlock() == Blocks.wall_sign) world.setBlockToAir(pos);
            }
            for (int dx = -16; dx <= 16; dx++) for (int dy = 0; dy < 20; dy++) { // backdrops: south (signs) and north (crowd)
                place(world, new BlockPos(x0 + dx, y + dy, z0 + 9), Blocks.stone);
                place(world, new BlockPos(x0 + dx, y + dy, z0 - 14), Blocks.stone);
            }
            int n = 0;
            for (int dx = -12; dx <= 12; dx++) for (int dy = 0; dy < 8; dy++) {
                BlockPos pos = new BlockPos(x0 + dx, y + dy, z0 + 8);
                place(world, pos, Blocks.wall_sign);
                world.setBlockState(pos, Blocks.wall_sign.getStateFromMeta(2)); // on the backdrop, facing the player
                TileEntitySign sign = (TileEntitySign) world.getTileEntity(pos);
                n++;
                sign.signText[0] = new ChatComponentText("§lLads §cRX");
                sign.signText[1] = new ChatComponentText("§9Sign §e#" + n);
                sign.signText[2] = new ChatComponentText("§aformatted §rline");
                sign.signText[3] = new ChatComponentText("§7benchmark " + (n % 7));
                sign.markDirty();
                world.markBlockForUpdate(pos);
            }
            for (int i = 0; i < 45; i++) {
                EntityArmorStand stand = new EntityArmorStand(world, x0 - 8 + (i % 9) * 2 + 0.5, y, z0 - 4 - (i / 9) * 2 + 0.5);
                stand.setCustomNameTag("§6Hologram §f#" + i);
                stand.setAlwaysRenderNameTag(true);
                for (int slot = 1; slot <= 4; slot++) stand.setCurrentItemOrArmor(slot, armour(slot, i));
                world.spawnEntityInWorld(stand);
                stands.add(stand);
            }
        });
        for (int i = 0; i < PLAYERS; i++) {
            EntityOtherPlayerMP other = new EntityOtherPlayerMP(mc.theWorld, new GameProfile(UUID.nameUUIDFromBytes(("ladsrx" + i).getBytes(StandardCharsets.UTF_8)), "RxPlayer" + i));
            other.setPositionAndRotation(x0 - 7 + (i % 8) * 2 + 0.5, 4, z0 - 5 - (i / 8) * 2 + 0.5, 0, 0);
            for (int slot = 1; slot <= 4; slot++) other.setCurrentItemOrArmor(slot, armour(slot, i + 1));
            mc.theWorld.addEntityToWorld(-17300 - i, other);
            players.add(other);
            command(mc, "scoreboard teams join " + (i % 2 == 0 ? "ladsRed" : "ladsBlue") + " RxPlayer" + i);
        }
        frames = new Frames();
        gpu = new Probe170r.GpuTimer();
        MinecraftForge.EVENT_BUS.register(frames);
        MinecraftForge.EVENT_BUS.register(gpu);
        LOG.info("Lads 1.8.9 rx probe BEGIN at {} {}", x0, z0);
        return after(60);
    }

    /** Leather (dyed), iron and diamond in turn, every other one enchanted (glint). */
    private static ItemStack armour(int slot, int i) {
        Item[][] sets = {{Items.leather_boots, Items.leather_leggings, Items.leather_chestplate, Items.leather_helmet},
            {Items.iron_boots, Items.iron_leggings, Items.iron_chestplate, Items.iron_helmet},
            {Items.diamond_boots, Items.diamond_leggings, Items.diamond_chestplate, Items.diamond_helmet}};
        ItemStack stack = new ItemStack(sets[(i + slot) % 3][slot - 1]);
        if (stack.getItem() == sets[0][slot - 1]) ((ItemArmor) stack.getItem()).setColor(stack, 0x3060C0 + i * 0x0A0503);
        if (i % 2 == 0) stack.addEnchantment(Enchantment.protection, 1);
        return stack;
    }

    private static void place(World world, BlockPos pos, net.minecraft.block.Block block) {
        if (!world.isAirBlock(pos)) return;
        world.setBlockState(pos, block.getDefaultState());
        placed.add(pos);
    }

    private static boolean built(Minecraft mc) {
        int signs = 0;
        for (Object te : mc.theWorld.loadedTileEntityList) if (te instanceof TileEntitySign && "§lLads §cRX".equals(((TileEntitySign) te).signText[0].getUnformattedText())) signs++;
        int standsSeen = 0;
        for (Entity e : mc.theWorld.loadedEntityList) if (e instanceof EntityArmorStand && ((EntityArmorStand) e).getCurrentArmor(3) != null) standsSeen++;
        if (signs < 200 || standsSeen < 45) return retry(20);
        check(true, "rx scene: " + signs + " signs with text, " + standsSeen + " armoured name-tagged armour stands, " + players.size() + " armoured players in teams");
        return after(20);
    }

    /** Looking at a scene: still player, eye level, south (signs) or north (crowd). */
    private static void look(Minecraft mc, float facing) {
        yaw = facing;
        mc.thePlayer.capabilities.isFlying = true;
        mc.thePlayer.setPositionAndRotation(x0 + 0.5, 4, z0 + 0.5, facing, 5);
        mc.thePlayer.prevRotationYaw = facing;
        mc.thePlayer.prevRotationPitch = 5;
        mc.thePlayer.motionX = mc.thePlayer.motionY = mc.thePlayer.motionZ = 0;
        mc.thePlayer.prevPosX = mc.thePlayer.lastTickPosX = mc.thePlayer.posX;
        mc.thePlayer.prevPosY = mc.thePlayer.lastTickPosY = mc.thePlayer.posY;
        mc.thePlayer.prevPosZ = mc.thePlayer.lastTickPosZ = mc.thePlayer.posZ;
    }

    /**
     * The world, read back with the tweaks off, on, then off again (a few frames apart, after 3 s still): both "off" frames equal
     * means the view was steady (else up to 5 tries), and then "on" must equal them pixel for pixel.
     */
    private static boolean parity(Minecraft mc, String name, float facing) throws Exception {
        look(mc, facing);
        if (frames.capture == null) {
            frames.capture = name;
            frames.shots.clear();
            frames.due = System.nanoTime() + 3_000_000_000L; // chunks, entities, the flying FOV change
            RenderTweaks189.enabled = false;
            if (name.equals("crowd")) still(mc, true);
            return retry(1);
        }
        if (frames.shots.size() < 3) return retry(1);
        if (name.equals("crowd")) still(mc, false);
        int[] off = frames.shots.get(0), on = frames.shots.get(1), off2 = frames.shots.get(2);
        int differ = 0, unsteady = 0;
        for (int i = 0; i < off.length; i++) { if (off[i] != on[i]) differ++; if (off[i] != off2[i]) unsteady++; }
        if (unsteady > 0 && ++frames.tries < 5) {
            LOG.info("Lads rx parity {}: view not steady ({} pixels changed between the two vanilla frames), again", name, unsteady);
            frames.capture = null;
            return retry(10);
        }
        save(mc, "parity-" + name + "-off", off);
        save(mc, "parity-" + name + "-on", on);
        report.addProperty("parity-" + name + "-pixels-differing", differ);
        frames.capture = null;
        frames.tries = 0;
        RenderTweaks189.enabled = true;
        check(unsteady == 0, "rx parity " + name + ": the view is steady (the two vanilla frames match)");
        check(differ == 0, "rx parity " + name + ": the world drawn with RenderTweaks189 off and on is the same, pixel for pixel (" + off.length + " pixels, " + differ + " differ)");
        return after(5);
    }

    private static final java.util.Map<EntityLivingBase, ItemStack[]> armourWas = new java.util.HashMap<EntityLivingBase, ItemStack[]>();

    /**
     * The crowd held still for its parity frames: the game paused (no ticks, frozen partial ticks: idle arm sway stops) and the
     * armour shown without enchantments (the glint moves with the clock; its drawing is not something RenderTweaks189 touches).
     */
    private static void still(Minecraft mc, boolean on) {
        for (Entity e : mc.theWorld.loadedEntityList) {
            if (!(e instanceof EntityArmorStand || players.contains(e))) continue;
            EntityLivingBase living = (EntityLivingBase) e;
            for (int slot = 1; slot <= 4; slot++) {
                if (on) {
                    ItemStack stack = living.getCurrentArmor(slot - 1);
                    if (stack == null) continue;
                    if (!armourWas.containsKey(living)) armourWas.put(living, new ItemStack[5]);
                    armourWas.get(living)[slot] = stack;
                    ItemStack plain = stack.copy();
                    if (plain.getTagCompound() != null) plain.getTagCompound().removeTag("ench");
                    living.setCurrentItemOrArmor(slot, plain);
                } else if (armourWas.containsKey(living) && armourWas.get(living)[slot] != null) living.setCurrentItemOrArmor(slot, armourWas.get(living)[slot]);
            }
        }
        if (!on) armourWas.clear();
        mc.displayGuiScreen(on ? new net.minecraft.client.gui.GuiScreen() {} : null); // a screen that pauses singleplayer
    }

    /**
     * Uncapped A/B at one render distance (benchmark exception: sandbox only, the game alone): rounds alternate off/on, each
     * settles 1 s then measures 4 s; per mode the median over its rounds of FPS and of the work per frame.
     */
    private static boolean bench(Minecraft mc, String scene, int distance) {
        look(mc, scene.equals("crowd") ? 180 : 0);
        long now = System.nanoTime();
        if (round < 0) {
            results.clear();
            results.add(new ArrayList<double[]>());
            results.add(new ArrayList<double[]>());
            round = 0;
            mc.gameSettings.renderDistanceChunks = distance;
            startRound(now + 4_000_000_000L); // the first round also waits for the render distance's chunks
            return retry(1);
        }
        if (now < roundStart + (SETTLE_MS + MEASURE_MS) * 1_000_000L) {
            if (now >= roundStart + SETTLE_MS * 1_000_000L && !frames.measuring) { frames.begin(); gpu.reset(); }
            return retry(1);
        }
        double[] stats = frames.end(gpu.milliseconds());
        int mode = round % 2;
        results.get(mode).add(stats);
        LOG.info("Lads rx {} RD{} round {} tweaks {}: {}", scene, distance, round + 1, mode == 1 ? "on" : "off", line(stats));
        if (++round < ROUNDS) { startRound(now); return retry(1); }
        JsonObject result = new JsonObject();
        double[][] medians = new double[2][];
        for (int m = 0; m < 2; m++) {
            medians[m] = new double[KEYS.length];
            JsonObject o = new JsonObject();
            for (int i = 0; i < KEYS.length; i++) {
                double[] values = new double[results.get(m).size()];
                for (int r = 0; r < values.length; r++) values[r] = results.get(m).get(r)[i];
                Arrays.sort(values);
                medians[m][i] = values.length % 2 == 1 ? values[values.length / 2] : (values[values.length / 2 - 1] + values[values.length / 2]) / 2;
                o.addProperty(KEYS[i], Math.round(medians[m][i] * 1000) / 1000.0);
            }
            result.add(m == 1 ? "on" : "off", o);
        }
        report.add(scene + "-rd" + distance, result);
        LOG.info("Lads rx {} RD{} RESULT (medians of {} rounds each) off: {} | on: {}", scene, distance, ROUNDS / 2, line(medians[0]), line(medians[1]));
        round = -1;
        RenderTweaks189.enabled = true;
        return after(5);
    }

    private static final String[] KEYS = {"fps", "workP50Ms", "workP99Ms", "workMaxMs", "workCpuMs", "gpuMs"};

    private static void startRound(long now) {
        RenderTweaks189.enabled = round % 2 == 1;
        roundStart = now;
        frames.measuring = false;
    }

    private static String line(double[] s) {
        return String.format(Locale.ROOT, "%.1f FPS, work p50 %.3f ms, p99 %.3f ms, max %.3f ms, CPU %.3f ms, GPU %.3f ms per frame", s[0], s[1], s[2], s[3], s[4], s[5]);
    }

    /** Ten signs get a new second line: the drawn text must follow (parity-signs-edited after this). */
    private static boolean editSigns(Minecraft mc) {
        onServer(mc, (server, player) -> {
            int y = player.worldObj.getHeight(new BlockPos(x0, 0, z0)).getY();
            for (int dx = -12; dx < -2; dx++) {
                BlockPos pos = new BlockPos(x0 + dx, y + 1, z0 + 8);
                TileEntitySign sign = (TileEntitySign) player.worldObj.getTileEntity(pos);
                if (sign == null) continue;
                sign.signText[1] = new ChatComponentText("§dEdited §f" + dx);
                sign.markDirty();
                player.worldObj.markBlockForUpdate(pos);
            }
        });
        return after(40);
    }

    /** Per-call cost of each path, off vs on, and that each answer is vanilla's. */
    private static boolean calls(Minecraft mc) {
        LayerBipedArmor layer = new LayerBipedArmor((RendererLivingEntity<?>) mc.getRenderManager().getSkinMap().get("default"));
        List<EntityLivingBase> tagged = new ArrayList<EntityLivingBase>();
        for (Entity e : mc.theWorld.loadedEntityList) if (e instanceof EntityArmorStand && ((EntityArmorStand) e).getCurrentArmor(3) != null || players.contains(e)) tagged.add((EntityLivingBase) e);
        int armourChecked = 0, namesChecked = 0;
        List<String> wrong = new ArrayList<String>();
        for (EntityLivingBase e : tagged) {
            for (int slot = 1; slot <= 4; slot++) {
                ItemStack stack = e.getCurrentArmor(slot - 1);
                for (String type : new String[] {null, "overlay"}) {
                    String texture = ((ItemArmor) stack.getItem()).getArmorMaterial().getName();
                    String vanilla = ForgeHooksClient.getArmorTexture(e, stack, String.format("%s:textures/models/armor/%s_layer_%d%s.png", "minecraft", texture,
                        slot == 2 ? 2 : 1, type == null ? "" : String.format("_%s", type)), slot, type);
                    if (!RenderTweaks189.armourTexture(stack, slot, type).toString().equals(vanilla)) wrong.add(vanilla);
                    armourChecked++;
                }
            }
            if (RenderTweaks189.formattedName(e) == null || !RenderTweaks189.formattedName(e).equals(e.getDisplayName().getFormattedText())) wrong.add(e.getName());
            namesChecked++;
        }
        check(wrong.isEmpty() && armourChecked > 0 && namesChecked > 0, "rx: " + armourChecked + " armour textures and " + namesChecked
            + " name tags are vanilla's (" + RenderTweaks189.formattedName(tagged.get(0)) + " ...); wrong: " + wrong);
        check(RenderTweaks189.namesOn(), "rx name tags: the every-256th comparison with vanilla never found a difference");
        check(RenderTweaks189.armourHits > 0 && RenderTweaks189.signHits > 0 && RenderTweaks189.nameHits > 0, "rx hooks active in game (OptiFine "
            + optiFine() + "): armour " + RenderTweaks189.armourHits + ", sign lines " + RenderTweaks189.signHits + ", name tags " + RenderTweaks189.nameHits);
        EntityLivingBase stand = null;
        for (EntityLivingBase e : tagged) if (e instanceof EntityArmorStand && e.getCurrentArmor(3).getItem() == Items.leather_helmet) stand = e;
        final EntityLivingBase armoured = stand;
        TileEntitySign sign = null;
        for (Object te : mc.theWorld.loadedTileEntityList) if (te instanceof TileEntitySign) sign = (TileEntitySign) te;
        final TileEntitySign oneSign = sign;
        JsonObject costs = new JsonObject();
        costs.add("armourTexture", nanos(() -> { for (int s = 1; s <= 4; s++) { layer.getArmorResource(armoured, armoured.getCurrentArmor(s - 1), s, null);
            layer.getArmorResource(armoured, armoured.getCurrentArmor(s - 1), s, "overlay"); } }, 8));
        costs.add("nameTag", nanos(() -> { for (EntityLivingBase e : tagged) RenderTweaks189.formattedName(RenderTweaks189.displayName(e)); }, tagged.size()));
        costs.add("signLine", nanos(() -> {
            RenderTweaks189.sign(oneSign);
            for (IChatComponent text : oneSign.signText) {
                List<IChatComponent> split = RenderTweaks189.splitSignLine(text, 90, mc.fontRendererObj, false, true);
                RenderTweaks189.signLineText(split.get(0));
            }
            RenderTweaks189.sign(null);
        }, 4));
        report.add("nanosPerCall", costs);
        check(true, "rx per-call costs in ns, tweaks off/on: " + costs);
        String vanillaLine = GuiUtilRenderComponents.splitText(oneSign.signText[1], 90, mc.fontRendererObj, false, true).get(0).getFormattedText();
        RenderTweaks189.sign(oneSign);
        String ours = RenderTweaks189.signLineText(RenderTweaks189.splitSignLine(oneSign.signText[1], 90, mc.fontRendererObj, false, true).get(0));
        RenderTweaks189.sign(null);
        check(vanillaLine.equals(ours), "rx sign line is vanilla's: " + vanillaLine);
        return after(5);
    }

    private interface Work { void run(); }

    /** Nanoseconds per call {"off": .., "on": ..}: 5k warm-up runs then 20k timed, each mode. */
    private static JsonObject nanos(Work work, int callsPerRun) {
        JsonObject o = new JsonObject();
        for (int mode = 0; mode < 2; mode++) {
            RenderTweaks189.enabled = mode == 1;
            for (int i = 0; i < 5_000; i++) work.run();
            long start = System.nanoTime();
            for (int i = 0; i < 20_000; i++) work.run();
            o.addProperty(mode == 1 ? "on" : "off", Math.round((System.nanoTime() - start) / (20_000.0 * callsPerRun) * 10) / 10.0);
        }
        RenderTweaks189.enabled = true;
        return o;
    }

    /** OptiFine's cloud setting set to value; what it was, or -1 without OptiFine. */
    private static int ofClouds(Minecraft mc, int value) {
        try {
            java.lang.reflect.Field field = mc.gameSettings.getClass().getField("ofClouds");
            int was = field.getInt(mc.gameSettings);
            field.setInt(mc.gameSettings, value);
            return was;
        } catch (ReflectiveOperationException absent) { return -1; }
    }

    private static String optiFine() {
        try { Class.forName("Config"); return "loaded"; } catch (Throwable absent) { return "absent"; }
    }

    private static boolean restore(Minecraft mc) throws Exception {
        MinecraftForge.EVENT_BUS.unregister(frames);
        MinecraftForge.EVENT_BUS.unregister(gpu);
        gpu.close();
        RenderTweaks189.enabled = enabledWas;
        mc.gameSettings.limitFramerate = limitWas;
        mc.gameSettings.renderDistanceChunks = distanceWas;
        mc.gameSettings.clouds = cloudsWas;
        mc.gameSettings.viewBobbing = bobbingWas;
        if (ofCloudsWas >= 0) ofClouds(mc, ofCloudsWas);
        for (EntityOtherPlayerMP other : players) mc.theWorld.removeEntityFromWorld(other.getEntityId());
        onServer(mc, (server, player) -> {
            for (EntityArmorStand stand : stands) stand.setDead();
            for (int i = placed.size() - 1; i >= 0; i--) player.worldObj.setBlockToAir(placed.get(i));
        });
        for (String command : new String[] {"scoreboard teams remove ladsRed", "scoreboard teams remove ladsBlue", "gamerule doDaylightCycle true"}) command(mc, command);
        File folder = new File(mc.mcDataDir, "lads-qa/rx");
        folder.mkdirs();
        Files.write(new File(folder, "rx.json").toPath(), new GsonBuilder().setPrettyPrinting().create().toJson(report).getBytes(StandardCharsets.UTF_8));
        check(true, "rx: scene removed, settings put back, results in lads-qa/rx/rx.json");
        LOG.info("Lads 1.8.9 rx probe END: {}", report);
        return after(20);
    }

    private static void save(Minecraft mc, String name, int[] pixels) throws Exception {
        int w = frames.width, h = frames.height;
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++) image.setRGB(0, h - 1 - y, w, 1, pixels, y * w, w); // GL rows are bottom-up
        File folder = new File(mc.mcDataDir, "lads-qa/rx");
        folder.mkdirs();
        ImageIO.write(image, "png", new File(folder, name + ".png"));
    }

    /**
     * Work per frame: RenderTickEvent START to END (the world and HUD drawn, before Display.update and the frame cap's sync),
     * wall time and render-thread CPU time; frames per second; and the parity read-backs (RenderWorldLastEvent).
     */
    public static final class Frames {
        final long[] work = new long[100_000], cpu = new long[100_000];
        final List<int[]> shots = new ArrayList<int[]>();
        int count, wait, width, height, tries;
        long start, startCpu, begun, due;
        boolean measuring;
        String capture;
        private IntBuffer buffer;

        void begin() {
            count = 0;
            begun = System.nanoTime();
            measuring = true;
        }

        /** FPS, work p50/p99/max ms, mean work CPU ms, GPU ms per frame. */
        double[] end(double gpuMs) {
            measuring = false;
            long[] sorted = Arrays.copyOf(work, count);
            Arrays.sort(sorted);
            long cpuTotal = 0;
            for (int i = 0; i < count; i++) cpuTotal += cpu[i];
            int n = Math.max(1, count);
            return new double[] {count / ((System.nanoTime() - begun) / 1e9), sorted[count / 2] / 1e6, sorted[Math.min(count - 1, (int) (count * 0.99))] / 1e6,
                sorted[Math.max(0, count - 1)] / 1e6, cpuTotal / 1e6 / n, gpuMs};
        }

        @SubscribeEvent
        public void frame(TickEvent.RenderTickEvent event) {
            if (event.phase == TickEvent.Phase.START) {
                start = System.nanoTime();
                startCpu = THREADS.getCurrentThreadCpuTime();
            } else if (measuring && start != 0 && count < work.length) {
                work[count] = System.nanoTime() - start;
                cpu[count++] = THREADS.getCurrentThreadCpuTime() - startCpu;
            }
        }

        @SubscribeEvent
        public void world(RenderWorldLastEvent event) {
            if (capture == null || shots.size() >= 3) return;
            Minecraft mc = Minecraft.getMinecraft();
            look(mc, yaw);
            if (System.nanoTime() < due || wait-- > 0) return;
            width = mc.displayWidth;
            height = mc.displayHeight;
            if (buffer == null || buffer.capacity() < width * height) buffer = BufferUtils.createIntBuffer(width * height);
            buffer.clear();
            GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);
            GL11.glReadPixels(0, 0, width, height, GL12.GL_BGRA, GL12.GL_UNSIGNED_INT_8_8_8_8_REV, buffer);
            int[] pixels = new int[width * height];
            buffer.get(pixels);
            shots.add(pixels);
            RenderTweaks189.enabled = shots.size() == 1; // off, on, off; each shot 5 frames after the switch
            due = 0;
            wait = 5;
        }
    }

    private interface ServerTask { void run(MinecraftServer server, EntityPlayerMP player); }

    private static void onServer(Minecraft mc, ServerTask task) {
        MinecraftServer server = mc.getIntegratedServer();
        UUID id = mc.thePlayer.getUniqueID();
        server.addScheduledTask(() -> task.run(server, server.getConfigurationManager().getPlayerByUUID(id)));
    }

    private static void command(Minecraft mc, String command) {
        MinecraftServer server = mc.getIntegratedServer();
        server.addScheduledTask(() -> server.getCommandManager().executeCommand(server, command));
    }
}
