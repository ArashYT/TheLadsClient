package com.thelads.core.v1_8_9.feature;

import static com.thelads.core.v1_8_9.feature.CoreProbe.after;
import static com.thelads.core.v1_8_9.feature.CoreProbe.retry;

import com.thelads.core.perf.BuiltInRender;
import com.thelads.core.perf.RenderQa;
import com.thelads.core.perf.RenderQaPack;
import com.thelads.core.v1_8_9.render.AnimationCache189;
import com.thelads.core.v1_8_9.render.RenderProbeHooks189;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.imageio.ImageIO;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraft.client.resources.ResourcePackRepository;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

/**
 * QA only (LADS_VERIFY_189_ONLY=render185): the 1.8.5 Kerria remake off and on, as on the Fabric versions (RenderProbe1201,
 * RenderProbe26), in front of a wall of animated blocks built in the sky above the QA player: frame CPU and GPU time and the atlas
 * animation cost per tick in alternating off/on windows, with the game's textures and with a generated HD animated pack; the block
 * atlas after Kerria's GPU copies and after the game's own uploads of the same frames, compared texel for texel on every mip level,
 * also after F3+T and with mipmaps off. Results in lads-qa/render185/results.json, the wall in lads-qa/screenshots/render185-*.png.
 * Everything is put back.
 */
final class RenderProbe189 {
    private static final Logger LOG = LogManager.getLogger("TheLadsCore");
    private static final int MEASURE_TICKS = 60, SETTLE_TICKS = 10, ROUNDS = 4, POISON = 0x01FF00FF; // poison: magenta at alpha 1
    static final List<CoreProbe.Step> STEPS = new ArrayList<CoreProbe.Step>();
    private static final Map<BlockPos, IBlockState> CHANGED = new LinkedHashMap<BlockPos, IBlockState>();
    private static volatile BlockPos base;
    private static String pendingShot;
    private static double[] posBefore;
    private static float yawBefore, pitchBefore;
    private static int cloudsBefore, particlesBefore, mipmapsBefore, viewBefore;
    private static boolean flyingBefore;

    static {
        STEPS.add(RenderProbe189::setup);
        STEPS.add(mc -> { mc.thePlayer.capabilities.isFlying = true; look(mc, 0, 10); return after(100); });
        measure("animations");
        STEPS.add(mc -> { pendingShot = "animations"; return after(2); });
        STEPS.add(mc -> pendingShot != null ? retry(1) : after(0));
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
        STEPS.add(mc -> { pendingShot = "animations-hd"; return after(2); });
        STEPS.add(mc -> pendingShot != null ? retry(1) : after(0));
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

    private static boolean setup(Minecraft mc) {
        RenderQa.reset();
        RenderProbeHooks189.afterFrame = RenderProbe189::frame;
        posBefore = new double[]{mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ};
        yawBefore = mc.thePlayer.rotationYaw; pitchBefore = mc.thePlayer.rotationPitch;
        cloudsBefore = mc.gameSettings.clouds;
        particlesBefore = mc.gameSettings.particleSetting;
        mipmapsBefore = mc.gameSettings.mipmapLevels;
        viewBefore = mc.gameSettings.thirdPersonView;
        flyingBefore = mc.thePlayer.capabilities.isFlying;
        base = new BlockPos(MathHelper.floor_double(mc.thePlayer.posX), 200, MathHelper.floor_double(mc.thePlayer.posZ));
        mc.gameSettings.thirdPersonView = 0;
        mc.gameSettings.clouds = 0;
        mc.gameSettings.particleSetting = 2;
        RenderQa.RESULTS.put("version", "1.8.9");
        RenderQa.RESULTS.put("optifine", classExists("Config") ? "present" : "absent");
        RenderQa.RESULTS.put("kerria", BuiltInRender.standDownReason(BuiltInRender.KERRIA) == null ? "on" : BuiltInRender.standDownReason(BuiltInRender.KERRIA));
        LOG.info("Lads render 1.8.5 capture BEGIN: {} steps; Kerria {}", STEPS.size(), BuiltInRender.animations ? "on" : "off");
        onServer(mc, (server, player) -> build(player.worldObj));
        return after(10);
    }

    private static boolean classExists(String name) {
        try { Class.forName(name, false, RenderProbe189.class.getClassLoader()); return true; } catch (Throwable absent) { return false; }
    }

    /** RenderProbeHooks189.afterFrame: the game framebuffer is still bound at the end of updateCameraAndRender; saved as asked. */
    private static void frame() {
        if (pendingShot == null) return;
        String name = pendingShot;
        pendingShot = null;
        Minecraft mc = Minecraft.getMinecraft();
        try {
            int w = mc.displayWidth, h = mc.displayHeight;
            IntBuffer buffer = BufferUtils.createIntBuffer(w * h);
            GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);
            GL11.glReadPixels(0, 0, w, h, GL12.GL_BGRA, GL12.GL_UNSIGNED_INT_8_8_8_8_REV, buffer);
            int[] raw = new int[w * h], pixels = new int[w * h];
            buffer.get(raw);
            for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) pixels[(h - 1 - y) * w + x] = raw[y * w + x] | 0xFF000000;
            BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            image.setRGB(0, 0, w, h, pixels, 0, w);
            File folder = new File(mc.mcDataDir, "lads-qa/screenshots");
            folder.mkdirs();
            ImageIO.write(image, "png", new File(folder, "render185-" + name + ".png"));
            LOG.info("Lads render 1.8.5 frame lads-qa/screenshots/render185-{}.png", name);
        } catch (Exception failure) { RenderQa.failures++; LOG.error("Lads render 1.8.5 capture FAILED: frame " + name, failure); }
    }

    // ---- block atlas parity ----

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
            TextureUtil.uploadTextureMipmap(poison, sprite.getIconWidth(), sprite.getIconHeight(), sprite.getOriginX(), sprite.getOriginY(), false, false);
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
                    TextureUtil.uploadTextureMipmap(frames.get(0), sprite.getIconWidth(), sprite.getIconHeight(), sprite.getOriginX(), sprite.getOriginY(), false, false);
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

    /** Server thread: a floor and, 8 blocks south of the player, a wall of animated blocks (water and lava in a basin, fire, portal). */
    private static void build(World world) {
        BlockPos b = base;
        IBlockState stone = Blocks.stone.getDefaultState();
        for (int x = -12; x <= 12; x++) for (int z = -4; z <= 12; z++) set(world, b.add(x, -1, z), stone);
        BlockPos a = b.add(0, 0, 8);
        for (int x = -7; x <= 7; x++) for (int y = 0; y <= 5; y++) set(world, a.add(x, y, 1), stone);
        IBlockState[] row = {Blocks.sea_lantern.getDefaultState(), Blocks.prismarine.getDefaultState(), Blocks.lit_furnace.getDefaultState()};
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

    private static void look(Minecraft mc, float yaw, float pitch) {
        double x = base.getX() + 0.5, y = base.getY(), z = base.getZ() + 0.5;
        onServer(mc, (server, player) -> player.setPositionAndUpdate(x, y, z));
        mc.thePlayer.rotationYaw = mc.thePlayer.prevRotationYaw = yaw;
        mc.thePlayer.rotationPitch = mc.thePlayer.prevRotationPitch = pitch;
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

    private static void measure(String scene) {
        for (int round = 0; round < ROUNDS; round++) for (boolean on : new boolean[]{false, true}) {
            final boolean mode = on;
            STEPS.add(mc -> { BuiltInRender.animations = mode; return after(SETTLE_TICKS); });
            STEPS.add(mc -> { RenderQa.startWindow(); return after(MEASURE_TICKS); });
            STEPS.add(mc -> { RenderQa.endWindow(scene, mode ? "on" : "off"); return after(0); });
        }
        STEPS.add(mc -> { BuiltInRender.animations = true; return after(0); });
    }

    private static boolean restore(Minecraft mc) {
        RenderQa.stop();
        RenderProbeHooks189.animationsHeld = false;
        RenderProbeHooks189.afterFrame = null;
        BuiltInRender.animations = BuiltInRender.enabled(BuiltInRender.KERRIA);
        final Map<BlockPos, IBlockState> blocks = new LinkedHashMap<BlockPos, IBlockState>(CHANGED);
        onServer(mc, (server, player) -> {
            for (Map.Entry<BlockPos, IBlockState> entry : blocks.entrySet()) player.worldObj.setBlockState(entry.getKey(), entry.getValue(), 2);
            if (posBefore != null) player.setPositionAndUpdate(posBefore[0], posBefore[1], posBefore[2]);
        });
        CHANGED.clear();
        mc.thePlayer.rotationYaw = yawBefore; mc.thePlayer.rotationPitch = pitchBefore;
        mc.thePlayer.capabilities.isFlying = flyingBefore;
        mc.gameSettings.thirdPersonView = viewBefore;
        mc.gameSettings.clouds = cloudsBefore;
        mc.gameSettings.particleSetting = particlesBefore;
        File out = new File(mc.mcDataDir, "lads-qa/render185");
        out.mkdirs();
        RenderQa.finish(out.toPath());
        CoreProbe.check(RenderQa.failures == 0, "the 1.8.5 Kerria remake keeps every atlas texel identical (" + RenderQa.checks + " render checks passed)");
        return after(5);
    }
}
