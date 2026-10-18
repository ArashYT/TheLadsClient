package com.thelads.core.v1_20_1.hud;

import com.mojang.blaze3d.platform.GlUtil;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.ModuleSupport;
import com.thelads.core.modules.BetterF3Module;
import com.thelads.core.v1_20_1.adapter.GuiGraphicsLadsAdapter1201;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Better F3 on 1.20.1: the game keeps building the debug text; BetterF3Mixin hands each finished column to BetterF3Module instead of
 * drawing it as plain grey text, with what the game knows ({@link #info}), and each frame with the debug screen closed the columns
 * slide out ({@link #closed}, HudPipeline1201). A BetterF3 jar left in the mods folder keeps the debug screen to itself.
 */
public final class NativeBetterF3_1201 {
    private static boolean active;

    private NativeBetterF3_1201() {}

    public static void register() {
        if (active) return;
        if (Mods1201.loaded("betterf3")) {
            ModuleSupport.registerExternal("BetterF3", "BetterF3", "betterf3", true);
            return;
        }
        active = true;
        ModuleSupport.registerBuiltIn("BetterF3");
    }

    private static BetterF3Module module() {
        return active && ModuleManager.getInstance().getModule("BetterF3") instanceof BetterF3Module f3 && f3.isEnabled() ? f3 : null;
    }

    /** Whether Better F3 draws the debug screen (else the game does). */
    public static boolean enabled() { return module() != null; }

    /** One column of the debug screen, drawn by Better F3. */
    public static void draw(GuiGraphics graphics, Font font, List<String> column, boolean left, Level level, LevelChunk clientChunk, LevelChunk serverChunk) {
        BetterF3Module f3 = module();
        if (f3 != null) f3.draw(new GuiGraphicsLadsAdapter1201(graphics, font), column, left, graphics.guiWidth(), true, info(level, clientChunk, serverChunk));
    }

    /** Each frame after the GUI: with the debug screen closed, the columns it showed slide out. */
    public static void closed(GuiGraphics graphics) {
        Minecraft mc = Minecraft.getInstance();
        BetterF3Module f3 = module();
        if (f3 != null && f3.closing() && !mc.options.renderDebug) f3.drawClosed(new GuiGraphicsLadsAdapter1201(graphics, mc.font));
    }

    /** What the BetterF3 style shows, from the game, as its own debug screen reads it. */
    static BetterF3Module.Info info(Level level, LevelChunk clientChunk, LevelChunk serverChunk) {
        Minecraft mc = Minecraft.getInstance();
        BetterF3Module.Info info = new BetterF3Module.Info();
        info.fps = mc.getFps();
        info.fpsLimit = mc.options.framerateLimit().get();
        info.particles = mc.particleEngine.countParticles();
        info.entities = mc.levelRenderer.getEntityStatistics();
        info.cpu = GlUtil.getCpuInfo();
        info.gpu = GlUtil.getRenderer();
        info.gpuVendor = GlUtil.getVendor();
        info.glVersion = GlUtil.getOpenGLVersion();
        info.displayWidth = mc.getWindow().getWidth();
        info.displayHeight = mc.getWindow().getHeight();
        if (mc.getGpuUtilization() > 0) info.gpuUtilization = mc.getGpuUtilization();
        Entity camera = mc.getCameraEntity();
        if (camera == null || mc.level == null) return info;
        BlockPos pos = camera.blockPosition();
        info.x = camera.getX();
        info.y = camera.getY();
        info.z = camera.getZ();
        info.yaw = camera.getYRot();
        info.pitch = camera.getXRot();
        info.dimension = mc.level.dimension().location().toString();
        info.dayTime = mc.level.getDayTime();
        if (mc.getSingleplayerServer() != null) info.seed = mc.getSingleplayerServer().overworld().getSeed();
        boolean inside = pos.getY() >= mc.level.getMinBuildHeight() && pos.getY() < mc.level.getMaxBuildHeight();
        if (inside) info.biome = mc.level.getBiome(pos).unwrap().map(key -> key.location().toString(), biome -> "[unregistered " + biome + "]");
        if (clientChunk != null && !clientChunk.isEmpty()) {
            info.light = mc.level.getChunkSource().getLightEngine().getRawBrightness(pos, 0);
            info.skyLight = mc.level.getBrightness(LightLayer.SKY, pos);
            info.blockLight = mc.level.getBrightness(LightLayer.BLOCK, pos);
            info.heights = heights(clientChunk, pos, true);
        }
        if (serverChunk != null) {
            var light = serverChunk.getLevel().getLightEngine();
            info.serverSky = light.getLayerListener(LightLayer.SKY).getLightValue(pos);
            info.serverBlock = light.getLayerListener(LightLayer.BLOCK).getLightValue(pos);
            info.serverHeights = heights(serverChunk, pos, false);
            if (inside && level != null) {
                DifficultyInstance difficulty = new DifficultyInstance(level.getDifficulty(), level.getDayTime(), serverChunk.getInhabitedTime(), level.getMoonBrightness());
                info.difficulty = difficulty.getEffectiveDifficulty();
                info.clampedDifficulty = difficulty.getSpecialMultiplier();
            }
        }
        HitResult hit = camera.pick(20.0, 0.0F, false);
        if (hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK) {
            BlockPos at = block.getBlockPos();
            info.target = new int[] {at.getX(), at.getY(), at.getZ()};
            info.targetId = BuiltInRegistries.BLOCK.getKey(mc.level.getBlockState(at).getBlock()).toString();
        }
        return info;
    }

    /** "S: 62  M: 62": the heightmaps the client gets (client), or those the server keeps after world generation. */
    private static String heights(LevelChunk chunk, BlockPos pos, boolean client) {
        StringBuilder out = new StringBuilder();
        for (Heightmap.Types type : Heightmap.Types.values()) {
            if (client ? type != Heightmap.Types.WORLD_SURFACE && type != Heightmap.Types.MOTION_BLOCKING : !type.keepAfterWorldgen()) continue;
            if (out.length() > 0) out.append("  ");
            out.append(BetterF3Module.heightName(type.name())).append(": ").append(chunk.getHeight(type, pos.getX(), pos.getZ()));
        }
        return out.toString();
    }
}
