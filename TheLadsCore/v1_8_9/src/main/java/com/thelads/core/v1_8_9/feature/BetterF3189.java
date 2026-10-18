package com.thelads.core.v1_8_9.feature;

import com.thelads.core.client.hud.HudCover;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.ModuleSupport;
import com.thelads.core.modules.BetterF3Module;
import com.thelads.core.v1_8_9.adapter.GuiLadsAdapter;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;

/**
 * Better F3 on 1.8.9: Forge collects the debug screen's text (vanilla's, OptiFine's and other mods' lines) in its text event and
 * draws it after; last in that event, BetterF3Module draws both columns instead (with what the game knows, {@link #info}) and leaves
 * Forge nothing to draw. The event comes every frame, so with the debug screen closed the columns slide out from here too.
 */
public final class BetterF3189 {
    private static String glVendor, glRenderer, glVersion;

    private BetterF3189() {}

    public static void register() {
        if (Loader.isModLoaded("betterf3")) {
            ModuleSupport.registerExternal("BetterF3", "BetterF3", "betterf3", true);
            return;
        }
        ModuleSupport.registerBuiltIn("BetterF3");
        // 1.8.9 has no server-side light or heightmap lines and no GPU load figure.
        Module module = ModuleManager.getInstance().getModule("BetterF3");
        if (module instanceof BetterF3Module) ((BetterF3Module) module).unsupported(BetterF3Module.Row.SERVER_LIGHT,
            BetterF3Module.Row.HEIGHT, BetterF3Module.Row.SERVER_HEIGHT, BetterF3Module.Row.GPU_UTIL);
        MinecraftForge.EVENT_BUS.register(new BetterF3189());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void text(RenderGameOverlayEvent.Text event) {
        Minecraft mc = Minecraft.getMinecraft();
        Module module = ModuleManager.getInstance().getModule("BetterF3");
        int width = event.resolution.getScaledWidth();
        boolean enabled = module instanceof BetterF3Module && module.isEnabled();
        if (!mc.gameSettings.showDebugInfo) {
            if (enabled && ((BetterF3Module) module).closing())
                ((BetterF3Module) module).drawClosed(new GuiLadsAdapter(mc.fontRendererObj, width, event.resolution.getScaledHeight()));
            return;
        }
        if (!enabled) {
            // Forge draws the plain text itself (a null line takes no row): the Lads HUD widgets under its lines step aside (HudCover).
            HudCover.debugColumn(rows(event.left), true, width, mc.fontRendererObj::getStringWidth);
            HudCover.debugColumn(rows(event.right), false, width, mc.fontRendererObj::getStringWidth);
            return;
        }
        GuiLadsAdapter graphics = new GuiLadsAdapter(mc.fontRendererObj, width, event.resolution.getScaledHeight());
        BetterF3Module.Info info = info(mc);
        ((BetterF3Module) module).draw(graphics, rows(event.left), true, width, true, info);
        ((BetterF3Module) module).draw(graphics, rows(event.right), false, width, true, info);
        event.left.clear();
        event.right.clear();
    }

    /** What the BetterF3 style shows, from the game, as its own debug screen reads it. */
    static BetterF3Module.Info info(Minecraft mc) {
        BetterF3Module.Info info = new BetterF3Module.Info();
        info.fps = Minecraft.getDebugFPS();
        info.fpsLimit = mc.gameSettings.limitFramerate;
        info.particles = mc.effectRenderer.getStatistics();
        info.entities = mc.renderGlobal.getDebugInfoEntities();
        info.cpu = OpenGlHelper.getCpu();
        if (glVersion == null) {
            glVendor = GL11.glGetString(GL11.GL_VENDOR);
            glRenderer = GL11.glGetString(GL11.GL_RENDERER);
            glVersion = GL11.glGetString(GL11.GL_VERSION);
        }
        info.gpuVendor = glVendor;
        info.gpu = glRenderer;
        info.glVersion = glVersion;
        info.displayWidth = Display.getWidth();
        info.displayHeight = Display.getHeight();
        Entity camera = mc.getRenderViewEntity();
        if (camera == null || mc.theWorld == null) return info;
        BlockPos pos = new BlockPos(camera.posX, camera.getEntityBoundingBox().minY, camera.posZ);
        info.x = camera.posX;
        info.y = camera.getEntityBoundingBox().minY;
        info.z = camera.posZ;
        info.yaw = camera.rotationYaw;
        info.pitch = camera.rotationPitch;
        int dimension = mc.theWorld.provider.getDimensionId();
        info.dimension = dimension == 0 ? "minecraft:overworld" : dimension == -1 ? "minecraft:the_nether" : dimension == 1 ? "minecraft:the_end"
            : mc.theWorld.provider.getDimensionName();
        info.dayTime = mc.theWorld.getWorldTime();
        if (mc.isIntegratedServerRunning() && mc.getIntegratedServer() != null && mc.getIntegratedServer().worldServers.length > 0)
            info.seed = mc.getIntegratedServer().worldServers[0].getSeed();
        if (mc.theWorld.isBlockLoaded(pos)) {
            Chunk chunk = mc.theWorld.getChunkFromBlockCoords(pos);
            info.biome = chunk.getBiome(pos, mc.theWorld.getWorldChunkManager()).biomeName;
            info.light = chunk.getLightSubtracted(pos, 0);
            info.skyLight = chunk.getLightFor(EnumSkyBlock.SKY, pos);
            info.blockLight = chunk.getLightFor(EnumSkyBlock.BLOCK, pos);
            DifficultyInstance difficulty = mc.theWorld.getDifficultyForLocation(pos);
            if (mc.isIntegratedServerRunning() && mc.getIntegratedServer() != null && mc.thePlayer != null) {
                EntityPlayerMP player = mc.getIntegratedServer().getConfigurationManager().getPlayerByUUID(mc.thePlayer.getUniqueID());
                if (player != null) difficulty = player.worldObj.getDifficultyForLocation(new BlockPos(player));
            }
            info.difficulty = difficulty.getAdditionalDifficulty();
            info.clampedDifficulty = difficulty.getClampedAdditionalDifficulty();
        }
        MovingObjectPosition hit = mc.objectMouseOver;
        if (hit != null && hit.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK && hit.getBlockPos() != null) {
            BlockPos at = hit.getBlockPos();
            info.target = new int[] {at.getX(), at.getY(), at.getZ()};
            Object id = Block.blockRegistry.getNameForObject(mc.theWorld.getBlockState(at).getBlock());
            info.targetId = id == null ? null : id.toString();
        }
        return info;
    }

    private static java.util.List<String> rows(java.util.List<String> lines) {
        java.util.List<String> rows = new java.util.ArrayList<String>(lines);
        rows.removeAll(java.util.Collections.singleton((String) null));
        return rows;
    }
}
