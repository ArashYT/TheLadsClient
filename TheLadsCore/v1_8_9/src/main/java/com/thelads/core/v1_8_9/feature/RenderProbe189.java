package com.thelads.core.v1_8_9.feature;

import static com.thelads.core.v1_8_9.feature.CoreProbe.after;
import static com.thelads.core.v1_8_9.feature.CoreProbe.retry;

import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.modules.HudModule;
import com.thelads.core.perf.BuiltInRender;
import com.thelads.core.perf.RenderQa;
import com.thelads.core.perf.RenderQaPack;
import com.thelads.core.v1_8_9.render.AnimationCache189;
import com.thelads.core.v1_8_9.render.RenderProbeHooks189;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.IntBuffer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.imageio.ImageIO;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.ResourcePackRepository;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.item.EntityItemFrame;
import net.minecraft.entity.monster.EntitySkeleton;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ScreenShotHelper;
import net.minecraft.world.World;
import net.minecraft.world.WorldSettings;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

/**
 * QA only (LADS_VERIFY_189_ONLY=render185): the 1.8.5 built-in render remakes off and on, as on the Fabric versions (RenderProbe1201,
 * RenderProbe26), in a scene built in the sky above the QA player: item frames, dropped items, armed mobs, a hotbar and inventory of
 * items, a wall of animated blocks. Frame CPU time and item renderer time in alternating off/on windows; frames off, on and off again
 * of the paused scene (empty pause screen, animations held, Lads HUD modules and chat hidden) compared pixel for pixel; the block
 * atlas after Kerria's GPU copies and after the game's own uploads of the same frames compared texel for texel on every mip level,
 * also with an HD animated pack, after F3+T and with mipmaps off. Results in lads-qa/render185/results.json, frames in
 * lads-qa/screenshots/render185-*.png. Everything is put back.
 */
final class RenderProbe189 {
    private static final Logger LOG = LogManager.getLogger("TheLadsCore");
    private static final int MEASURE_TICKS = 60, SETTLE_TICKS = 10, ROUNDS = 4, POISON = 0x01FF00FF; // poison: magenta at alpha 1
    static final List<CoreProbe.Step> STEPS = new ArrayList<CoreProbe.Step>();
    private static final Map<BlockPos, IBlockState> CHANGED = new LinkedHashMap<BlockPos, IBlockState>();
    private static final List<Integer> SPAWNED = new ArrayList<Integer>();
    private static final Map<String, int[]> SHOTS = new LinkedHashMap<String, int[]>();
    private static final List<Module> HIDDEN = new ArrayList<Module>();
    private static volatile BlockPos base;
    private static String pendingShot;
    private static double[] posBefore;
    private static float yawBefore, pitchBefore;
    private static int cloudsBefore, particlesBefore, mipmapsBefore, viewBefore;
    private static boolean flyingBefore;
    private static WorldSettings.GameType modeBefore;
    private static final List<ItemStack> INVENTORY = new ArrayList<ItemStack>();

    static {
        STEPS.add(RenderProbe189::setup);
        STEPS.add(mc -> { mc.thePlayer.capabilities.isFlying = true; look(mc, 0, 0, 0, 12); fillInventory(mc, true); return after(100); });
        measure("items");
        STEPS.add(mc -> { fillInventory(mc, false); return after(10); });
        shots("items", true, false);
        STEPS.add(mc -> { look(mc, 0, -30, 0, 4); fillInventory(mc, true); return after(40); });
        measure("far");
        STEPS.add(mc -> { fillInventory(mc, false); return after(10); });
        shots("far", true, true);
        STEPS.add(mc -> { look(mc, 0, 0, 0, -90); fillInventory(mc, true); return after(20); });
        STEPS.add(mc -> { mc.displayGuiScreen(new GuiInventory(mc.thePlayer)); return after(20); });
        measure("inventory");
        STEPS.add(mc -> { fillInventory(mc, false); return after(10); });
        shots("inventory", false, false);
        STEPS.add(mc -> { mc.displayGuiScreen(null); return after(5); });
        STEPS.add(mc -> { look(mc, 60, 0, 0, 10); return after(60); });
        measure("animations");
        atlasParity("start");
        STEPS.add(mc -> {
            RenderQaPack.write(new File(mc.mcDataDir, "resourcepacks").toPath(), "\"pack_format\":1", 256,
                new RenderQaPack.Sprite("blocks/water_still", 32, 2, false), new RenderQaPack.Sprite("blocks/lava_still", 20, 2, false),
                new RenderQaPack.Sprite("blocks/sea_lantern", 5, 5, false), new RenderQaPack.Sprite("blocks/portal", 32, 1, false),
                new RenderQaPack.Sprite("blocks/fire_layer_0", 32, 1, false), new RenderQaPack.Sprite("blocks/prismarine_rough", 4, 20, true));
            RenderQa.check(pack(mc, true), "the HD animated pack is active");
            mc.refreshResources();
            return after(60);
        });
        measure("hd");
        atlasParity("hd");
        STEPS.add(mc -> { pack(mc, false); mc.refreshResources(); return after(20); });
        STEPS.add(mc -> { mc.refreshResources(); RenderQa.check(true, "F3+T resource reload completed"); RenderQa.startWindow(); return after(100); });
        STEPS.add(mc -> { RenderQa.endWindow("afterReload", "on"); return after(0); });
        atlasParity("reload");
        STEPS.add(mc -> { mc.gameSettings.mipmapLevels = 0; mc.getTextureMapBlocks().setMipmapLevels(0); mc.refreshResources(); return after(40); });
        atlasParity("nomips");
        STEPS.add(mc -> {
            mc.gameSettings.mipmapLevels = mipmapsBefore; mc.getTextureMapBlocks().setMipmapLevels(mipmapsBefore); mc.refreshResources();
            return after(20);
        });
        STEPS.add(RenderProbe189::restore);
    }

    private RenderProbe189() {}

    private static boolean setup(Minecraft mc) throws Exception {
        RenderQa.reset();
        RenderProbeHooks189.afterFrame = RenderProbe189::frame;
        posBefore = new double[]{mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ};
        yawBefore = mc.thePlayer.rotationYaw; pitchBefore = mc.thePlayer.rotationPitch;
        modeBefore = mc.playerController.getCurrentGameType();
        cloudsBefore = mc.gameSettings.clouds;
        particlesBefore = mc.gameSettings.particleSetting;
        mipmapsBefore = mc.gameSettings.mipmapLevels;
        viewBefore = mc.gameSettings.thirdPersonView;
        flyingBefore = mc.thePlayer.capabilities.isFlying;
        INVENTORY.clear();
        for (ItemStack stack : mc.thePlayer.inventory.mainInventory) INVENTORY.add(stack == null ? null : stack.copy());
        base = new BlockPos(MathHelper.floor_double(mc.thePlayer.posX), 200, MathHelper.floor_double(mc.thePlayer.posZ));
        mc.gameSettings.thirdPersonView = 0;
        mc.gameSettings.clouds = 0;
        mc.gameSettings.particleSetting = 2;
        mc.gameSettings.hideGUI = false;
        RenderQa.RESULTS.put("version", "1.8.9");
        RenderQa.RESULTS.put("optifine", classExists("Config") || classExists("net.optifine.Config") ? "present" : "absent");
        RenderQa.RESULTS.put("flerovium", BuiltInRender.standDownReason(BuiltInRender.FLEROVIUM) == null ? "on" : BuiltInRender.standDownReason(BuiltInRender.FLEROVIUM));
        RenderQa.RESULTS.put("kerria", BuiltInRender.standDownReason(BuiltInRender.KERRIA) == null ? "on" : BuiltInRender.standDownReason(BuiltInRender.KERRIA));
        LOG.info("Lads render 1.8.5 capture BEGIN: {} steps; Flerovium {}, Kerria {}", STEPS.size(), BuiltInRender.items ? "on" : "off", BuiltInRender.animations ? "on" : "off");
        onServer(mc, (server, player) -> { player.setGameType(WorldSettings.GameType.CREATIVE); build(player.worldObj); });
        return after(10);
    }

    private static boolean classExists(String name) {
        try { Class.forName(name, false, RenderProbe189.class.getClassLoader()); return true; } catch (Throwable absent) { return false; }
    }

    // ---- frames (RenderProbeHooks189.afterFrame, at the end of EntityRenderer.updateCameraAndRender) ----

    private static void frame() {
        if (pendingShot == null) return;
        String name = pendingShot;
        pendingShot = null;
        Minecraft mc = Minecraft.getMinecraft();
        try {
            File folder = new File(mc.mcDataDir, "lads-qa");
            new File(folder, "screenshots").mkdirs();
            ScreenShotHelper.saveScreenshot(folder, "render185-" + name + ".png", mc.displayWidth, mc.displayHeight, mc.getFramebuffer());
            BufferedImage image = ImageIO.read(new File(folder, "screenshots/render185-" + name + ".png"));
            SHOTS.put(name, image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth()));
            LOG.info("Lads render 1.8.5 frame lads-qa/screenshots/render185-{}.png", name);
        } catch (Exception failure) { RenderQa.failures++; LOG.error("Lads render 1.8.5 capture FAILED: frame " + name, failure); }
    }

    // ---- block atlas parity (Kerria) ----

    private static void atlasParity(String tag) {
        STEPS.add(mc -> { RenderProbeHooks189.animationsHeld = true; return after(3); });
        STEPS.add(mc -> {
            int[][] copies = redrawAndRead(mc, true), game = redrawAndRead(mc, false);
            RenderQa.compareAtlas(tag + ": Kerria's GPU copies vs the game's uploads", copies, game, POISON);
            RenderQa.RESULTS.put(tag + "AnimatedSprites", AnimationCache189.animated.size());
            RenderProbeHooks189.animationsHeld = false;
            return after(2);
        });
    }

    /** Every animated sprite poisoned on every level, its first frame written by Kerria's copy or the game's upload, the atlas read back. */
    private static int[][] redrawAndRead(Minecraft mc, boolean copies) {
        int atlas = mc.getTextureMapBlocks().getGlTextureId();
        GlStateManager.bindTexture(atlas);
        int levels = AnimationCache189.animated.isEmpty() ? 1 : AnimationCache189.animated.get(0).getFrameTextureData(0).length;
        for (TextureAtlasSprite sprite : AnimationCache189.animated) {
            int[][] poison = new int[sprite.getFrameTextureData(0).length][];
            for (int level = 0; level < poison.length; level++) {
                poison[level] = new int[Math.max(1, (sprite.getIconWidth() >> level) * (sprite.getIconHeight() >> level))];
                java.util.Arrays.fill(poison[level], POISON);
            }
            net.minecraft.client.renderer.texture.TextureUtil.uploadTextureMipmap(poison, sprite.getIconWidth(), sprite.getIconHeight(), sprite.getOriginX(), sprite.getOriginY(), false, false);
        }
        boolean animations = BuiltInRender.animations;
        BuiltInRender.animations = copies;
        AnimationCache189.cyclingAtlas = atlas;
        try {
            for (TextureAtlasSprite sprite : AnimationCache189.animated) {
                List<int[][]> frames = new ArrayList<int[][]>();
                for (int i = 0; i < sprite.getFrameCount(); i++) frames.add(sprite.getFrameTextureData(i));
                if (frames.isEmpty()) continue;
                if (!copies || !AnimationCache189.copy(sprite, frames, 0, sprite.getIconWidth(), sprite.getIconHeight(), sprite.getOriginX(), sprite.getOriginY()))
                    net.minecraft.client.renderer.texture.TextureUtil.uploadTextureMipmap(frames.get(0), sprite.getIconWidth(), sprite.getIconHeight(), sprite.getOriginX(), sprite.getOriginY(), false, false);
            }
        } finally {
            AnimationCache189.cyclingAtlas = 0;
            BuiltInRender.animations = animations;
        }
        GlStateManager.bindTexture(atlas);
        int[][] out = new int[levels][];
        for (int level = 0; level < levels; level++) {
            int w = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, level, GL11.GL_TEXTURE_WIDTH);
            int h = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, level, GL11.GL_TEXTURE_HEIGHT);
            IntBuffer buffer = BufferUtils.createIntBuffer(w * h);
            GL11.glGetTexImage(GL11.GL_TEXTURE_2D, level, GL12.GL_BGRA, GL12.GL_UNSIGNED_INT_8_8_8_8_REV, buffer);
            out[level] = new int[w * h];
            buffer.get(out[level]);
        }
        return out;
    }

    // ---- scene ----

    private interface ServerTask { void run(MinecraftServer server, EntityPlayerMP player); }

    private static void onServer(Minecraft mc, ServerTask task) {
        MinecraftServer server = mc.getIntegratedServer();
        UUID id = mc.thePlayer.getUniqueID();
        server.addScheduledTask(() -> task.run(server, server.getConfigurationManager().getPlayerByUUID(id)));
    }

    private static void set(World world, BlockPos pos, IBlockState state) {
        if (!CHANGED.containsKey(pos)) CHANGED.put(pos, world.getBlockState(pos));
        world.setBlockState(pos, state, 2);
    }

    private static void add(World world, Entity entity) {
        world.spawnEntityInWorld(entity);
        SPAWNED.add(entity.getEntityId());
    }

    private static final Item[] ITEMS = {Items.diamond_sword, Items.iron_pickaxe, Items.apple, Items.bread, Items.golden_apple, Items.diamond,
        Items.emerald, Items.stick, Items.bow, Items.fishing_rod, Items.arrow, Items.redstone, Items.cooked_beef, Items.feather, Items.bone,
        Items.ender_pearl, Items.blaze_rod, Items.gold_ingot, Items.iron_ingot, Items.coal, Item.getItemFromBlock(Blocks.torch),
        Item.getItemFromBlock(Blocks.stone), Item.getItemFromBlock(Blocks.grass), Item.getItemFromBlock(Blocks.log), Item.getItemFromBlock(Blocks.glass),
        Item.getItemFromBlock(Blocks.leaves), Item.getItemFromBlock(Blocks.crafting_table), Item.getItemFromBlock(Blocks.furnace),
        Item.getItemFromBlock(Blocks.tnt), Item.getItemFromBlock(Blocks.bookshelf), Items.iron_chestplate, Items.golden_helmet, Items.shears,
        Items.flint_and_steel, Items.wheat, Items.carrot, Items.potato, Items.melon, Items.reeds, Items.paper, Items.book, Items.slime_ball,
        Items.gunpowder, Items.string, Items.leather, Item.getItemFromBlock(Blocks.glass_pane), Item.getItemFromBlock(Blocks.web),
        Item.getItemFromBlock(Blocks.cactus), Item.getItemFromBlock(Blocks.pumpkin), Item.getItemFromBlock(Blocks.redstone_torch),
        Item.getItemFromBlock(Blocks.ladder), Items.oak_door, Item.getItemFromBlock(Blocks.iron_bars)};

    private static ItemStack item(int i, boolean glint) {
        ItemStack stack = new ItemStack(ITEMS[(i % ITEMS.length + ITEMS.length) % ITEMS.length]);
        if (glint) stack.addEnchantment(Enchantment.unbreaking, 1);
        return stack;
    }

    /** Server thread: the scene around base (the player stands at base looking south, +z), as RenderProbe1201 builds it. */
    private static void build(World world) {
        BlockPos b = base;
        IBlockState stone = Blocks.stone.getDefaultState();
        for (int x = -24; x <= 76; x++) for (int z = -40; z <= 20; z++) set(world, b.add(x, -1, z), stone);
        for (int x = -10; x <= 10; x++) for (int y = 0; y <= 7; y++) set(world, b.add(x, y, 16), stone);
        int n = 0;
        for (int x = -9; x <= 9; x++) for (int y = 1; y <= 6; y++) {
            EntityItemFrame frame = new EntityItemFrame(world, b.add(x, y, 15), EnumFacing.NORTH);
            frame.setDisplayedItem(item(n++, false));
            add(world, frame);
        }
        for (int x = -8; x <= 8; x++) for (int z = 3; z <= 11; z += 2) for (int k = 0; k < 2; k++) {
            EntityItem entity = new EntityItem(world, b.getX() + x + 0.5 + k * 0.3, b.getY() + 0.05, b.getZ() + z + 0.5, item(n++, false));
            entity.motionX = entity.motionY = entity.motionZ = 0;
            entity.setInfinitePickupDelay();
            entity.lifespan = Integer.MAX_VALUE;
            add(world, entity);
        }
        for (int i = 0; i < 12; i++) {
            EntityLiving mob = i % 2 == 0 ? new EntityZombie(world) : new EntitySkeleton(world);
            mob.setLocationAndAngles(b.getX() + (i < 6 ? -11.5 : 11.5), b.getY(), b.getZ() + 4.5 + (i % 6) * 1.8, i < 6 ? -90 : 90, 0);
            mob.setNoAI(true);
            mob.enablePersistence();
            mob.setCurrentItemOrArmor(0, item(i, false));
            mob.setCurrentItemOrArmor(4, new ItemStack(i % 3 == 0 ? Items.iron_helmet : Items.golden_helmet));
            mob.setCurrentItemOrArmor(3, new ItemStack(Items.chainmail_chestplate));
            add(world, mob);
        }
        BlockPos a = b.add(60, 0, 8);
        for (int x = -7; x <= 7; x++) for (int y = 0; y <= 5; y++) set(world, a.add(x, y, 1), stone);
        IBlockState[] row = {Blocks.sea_lantern.getDefaultState(), Blocks.prismarine.getDefaultState(), Blocks.lit_furnace.getDefaultState(),
            Blocks.sea_lantern.getDefaultState(), Blocks.prismarine.getDefaultState()};
        for (int x = -7; x <= 7; x++) set(world, a.add(x, 2, 0), row[(x + 7) % row.length]);
        for (int x = -7; x <= 7; x++) set(world, a.add(x, 3, 0), Blocks.portal.getDefaultState());
        for (int x = -7; x <= 7; x++) {
            set(world, a.add(x, 0, -1), stone);
            set(world, a.add(x, 0, 0), x < 0 ? Blocks.water.getDefaultState() : Blocks.lava.getDefaultState());
        }
        for (int x = -7; x <= 7; x += 2) {
            set(world, a.add(x, 1, -1), Blocks.netherrack.getDefaultState());
            set(world, a.add(x, 2, -1), Blocks.fire.getDefaultState());
        }
    }

    private static void look(Minecraft mc, double dx, double dz, float yaw, float pitch) {
        double x = base.getX() + 0.5 + dx, y = base.getY(), z = base.getZ() + 0.5 + dz;
        onServer(mc, (server, player) -> player.setPositionAndUpdate(x, y, z));
        mc.thePlayer.rotationYaw = mc.thePlayer.prevRotationYaw = yaw;
        mc.thePlayer.rotationPitch = mc.thePlayer.prevRotationPitch = pitch;
    }

    private static void fillInventory(Minecraft mc, boolean glint) {
        onServer(mc, (server, player) -> {
            for (int i = 0; i < 36; i++) player.inventory.mainInventory[i] = item(i * 7 + 3, glint);
            player.inventory.currentItem = 4;
            player.inventoryContainer.detectAndSendChanges();
        });
    }

    /** Selects or drops the generated HD pack; true when it is now selected as asked. */
    private static boolean pack(Minecraft mc, boolean on) {
        ResourcePackRepository repository = mc.getResourcePackRepository();
        repository.updateRepositoryEntriesAll();
        List<ResourcePackRepository.Entry> selected = new ArrayList<ResourcePackRepository.Entry>(repository.getRepositoryEntries());
        ResourcePackRepository.Entry ours = null;
        for (ResourcePackRepository.Entry entry : repository.getRepositoryEntriesAll())
            if (entry.getResourcePackName().equals(RenderQaPack.NAME)) ours = entry;
        for (int i = selected.size() - 1; i >= 0; i--) if (selected.get(i).getResourcePackName().equals(RenderQaPack.NAME)) selected.remove(i);
        if (on && ours != null) selected.add(ours);
        repository.setRepositories(selected);
        return !on || ours != null;
    }

    /** An empty pause screen: the singleplayer game stops ticking, the world and HUD keep drawing, nothing moves. */
    private static final class Still extends GuiScreen {
        @Override public boolean doesGuiPauseGame() { return true; }
        @Override public void drawScreen(int mouseX, int mouseY, float partialTicks) {}
    }

    private static void hudModules(boolean hide) {
        if (hide) {
            HIDDEN.clear();
            for (Module module : ModuleManager.getInstance().getModules())
                if (module instanceof HudModule && module.isEnabled()) { HIDDEN.add(module); module.setEnabled(false); }
        } else {
            for (Module module : HIDDEN) module.setEnabled(true);
            HIDDEN.clear();
        }
    }

    private static void measure(String scene) {
        for (int round = 0; round < ROUNDS; round++) for (boolean on : new boolean[]{false, true}) {
            final boolean mode = on;
            STEPS.add(mc -> { BuiltInRender.items = mode; BuiltInRender.animations = mode; return after(SETTLE_TICKS); });
            STEPS.add(mc -> { RenderQa.startWindow(); return after(MEASURE_TICKS); });
            STEPS.add(mc -> { RenderQa.endWindow(scene, mode ? "on" : "off"); return after(0); });
        }
        STEPS.add(mc -> { BuiltInRender.items = true; BuiltInRender.animations = true; return after(0); });
    }

    private static void shots(String scene, boolean pause, boolean lod) {
        STEPS.add(mc -> {
            RenderProbeHooks189.animationsHeld = true;
            hudModules(true);
            mc.ingameGUI.getChatGUI().clearChatMessages();
            if (pause) mc.displayGuiScreen(new Still());
            BuiltInRender.items = false; BuiltInRender.animations = false;
            return after(pause ? 60 : 6);
        });
        STEPS.add(mc -> { pendingShot = scene + "-off"; return after(2); });
        STEPS.add(mc -> pendingShot != null ? retry(1) : after(0));
        STEPS.add(mc -> { BuiltInRender.items = true; BuiltInRender.animations = true; return after(6); });
        STEPS.add(mc -> { pendingShot = scene + "-on"; return after(2); });
        STEPS.add(mc -> pendingShot != null ? retry(1) : after(0));
        STEPS.add(mc -> { BuiltInRender.items = false; BuiltInRender.animations = false; return after(6); });
        STEPS.add(mc -> { pendingShot = scene + "-off2"; return after(2); });
        STEPS.add(mc -> pendingShot != null ? retry(1) : after(0));
        STEPS.add(mc -> {
            RenderQa.compareShots(scene, SHOTS.get(scene + "-off"), SHOTS.get(scene + "-on"), SHOTS.get(scene + "-off2"), lod);
            BuiltInRender.items = true; BuiltInRender.animations = true;
            if (pause) mc.displayGuiScreen(null);
            hudModules(false);
            RenderProbeHooks189.animationsHeld = false;
            return after(2);
        });
    }

    private static boolean restore(Minecraft mc) {
        RenderQa.stop();
        RenderProbeHooks189.animationsHeld = false;
        RenderProbeHooks189.afterFrame = null;
        hudModules(false);
        BuiltInRender.items = BuiltInRender.enabled(BuiltInRender.FLEROVIUM);
        BuiltInRender.animations = BuiltInRender.enabled(BuiltInRender.KERRIA);
        if (mc.currentScreen != null) mc.displayGuiScreen(null);
        final List<Integer> ids = new ArrayList<Integer>(SPAWNED);
        final Map<BlockPos, IBlockState> blocks = new LinkedHashMap<BlockPos, IBlockState>(CHANGED);
        onServer(mc, (server, player) -> {
            for (int id : ids) { Entity e = player.worldObj.getEntityByID(id); if (e != null) e.setDead(); }
            for (Map.Entry<BlockPos, IBlockState> entry : blocks.entrySet()) player.worldObj.setBlockState(entry.getKey(), entry.getValue(), 2);
            for (int i = 0; i < INVENTORY.size() && i < player.inventory.mainInventory.length; i++) player.inventory.mainInventory[i] = INVENTORY.get(i);
            if (modeBefore != null) player.setGameType(modeBefore);
            if (posBefore != null) player.setPositionAndUpdate(posBefore[0], posBefore[1], posBefore[2]);
        });
        SPAWNED.clear(); CHANGED.clear();
        mc.thePlayer.rotationYaw = yawBefore; mc.thePlayer.rotationPitch = pitchBefore;
        mc.thePlayer.capabilities.isFlying = flyingBefore;
        mc.gameSettings.thirdPersonView = viewBefore;
        mc.gameSettings.clouds = cloudsBefore;
        mc.gameSettings.particleSetting = particlesBefore;
        Path out = new File(mc.mcDataDir, "lads-qa/render185").toPath();
        out.toFile().mkdirs();
        RenderQa.finish(out);
        CoreProbe.check(RenderQa.failures == 0, "the 1.8.5 render remakes keep every frame and atlas texel identical (" + RenderQa.checks + " render checks passed)");
        return after(5);
    }
}
