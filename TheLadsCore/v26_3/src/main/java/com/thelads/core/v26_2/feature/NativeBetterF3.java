package com.thelads.core.v26_2.feature;

import com.mojang.blaze3d.systems.RenderSystem;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.ModuleSupport;
import com.thelads.core.modules.BetterF3Module;
import com.thelads.core.v26_2.adapter.GuiGraphicsExtractorLadsAdapter;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.debug.DebugEntrySystemSpecs;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Better F3 on 26.x: the game keeps choosing and building the debug entries (its Debug Options screen still picks them);
 * BetterF3Mixin hands each finished column to BetterF3Module instead of drawing it as plain grey text, with what the game knows
 * ({@link #info}), and each frame with the screen closed lets the columns slide out ({@link #closed}). A BetterF3 jar left in the
 * mods folder keeps the debug screen to itself.
 */
public final class NativeBetterF3 {
    private static boolean active;
    /** The debug GUI scale (Options.debugGuiScale) the columns were last drawn at, for their slide out. */
    private static float scale = 1;
    private NativeBetterF3() {}

    public static void register() {
        if (active || FabricLoader.getInstance().isModLoaded("betterf3")) return;
        active = true;
        ModuleSupport.registerBuiltIn("BetterF3");
    }

    private static BetterF3Module module() {
        return active && ModuleManager.getInstance().getModule("BetterF3") instanceof BetterF3Module f3 && f3.isEnabled() ? f3 : null;
    }

    /** Whether the game should measure its GPU load: Better F3 shows its GPU Utilization line now. */
    public static boolean measuresGpu() {
        BetterF3Module f3 = module();
        return f3 != null && f3.shows(BetterF3Module.Row.GPU_UTIL) && Minecraft.getInstance().debugEntries.isOverlayVisible();
    }

    /** Whether Better F3 draws the debug screen (else the game does). */
    public static boolean enabled() { return module() != null; }

    /** One column of the debug screen, drawn by Better F3 (true), or left to the game (false). */
    public static boolean draw(GuiGraphicsExtractor graphics, Font font, List<String> column, boolean left, int width,
                               Level level, LevelChunk clientChunk, LevelChunk serverChunk) {
        BetterF3Module f3 = module();
        if (f3 == null) return false;
        boolean visible = Minecraft.getInstance().debugEntries.isOverlayVisible();
        if (visible) scale = graphics.pose().m00();
        f3.draw(new GuiGraphicsExtractorLadsAdapter(graphics, font), column, left, width, visible, visible ? info(level, clientChunk, serverChunk) : null);
        return true;
    }

    /** Each frame (DebugScreenOverlay.extractRenderState): with the debug screen closed, the columns it showed slide out. */
    public static void closed(GuiGraphicsExtractor graphics, Font font) {
        Minecraft mc = Minecraft.getInstance();
        BetterF3Module f3 = module();
        if (f3 == null || !f3.closing() || mc.debugEntries.isOverlayVisible() || mc.gui.hud.isHidden() && mc.gui.screen() == null) return;
        GuiGraphicsExtractorLadsAdapter g = new GuiGraphicsExtractorLadsAdapter(graphics, font);
        g.pushPose();
        try {
            if (scale != 1) g.scale(scale, scale);
            f3.drawClosed(g);
        } finally {
            g.popPose();
        }
    }

    /** What the BetterF3 style shows, from the game, as its own debug entries read it. */
    static BetterF3Module.Info info(Level level, LevelChunk clientChunk, LevelChunk serverChunk) {
        Minecraft mc = Minecraft.getInstance();
        BetterF3Module.Info info = new BetterF3Module.Info();
        info.fps = mc.getFps();
        info.fpsLimit = mc.getFramerateLimitTracker().getFramerateLimit();
        info.particles = mc.particleEngine.countParticles();
        info.entities = mc.levelExtractor.entityStatistics();
        info.cpu = DebugEntrySystemSpecs.getCpuInfo();
        var device = RenderSystem.tryGetDevice();
        if (device != null) {
            var gpu = device.getDeviceInfo();
            info.gpu = gpu.name();
            info.gpuVendor = gpu.vendorName();
            info.glVersion = gpu.driverInfo().lines().findFirst().orElse(gpu.driverInfo());
        }
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
        info.dimension = mc.level.dimension().identifier().toString();
        info.dayTime = mc.level.getOverworldClockTime();
        if (mc.getSingleplayerServer() != null) info.seed = mc.getSingleplayerServer().overworld().getSeed();
        if (mc.level.isInsideBuildHeight(pos.getY()))
            info.biome = mc.level.getBiome(pos).unwrap().map(key -> key.identifier().toString(), biome -> "[unregistered " + biome + "]");
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
            if (level instanceof ServerLevel server && server.isInsideBuildHeight(pos.getY())) {
                DifficultyInstance difficulty = new DifficultyInstance(server.getDifficulty(), server.getOverworldClockTime(), serverChunk.getInhabitedTime(), server.getMoonBrightness(pos));
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
