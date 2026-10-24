package com.thelads.core.v1_8_9.pvp;

import com.thelads.core.client.ClearGlass;
import com.thelads.core.client.HideFoliage;
import com.thelads.core.config.ModuleSupport;
import com.thelads.core.modules.ClearGlassModule;
import com.thelads.core.modules.ColorSaturationModule;
import com.thelads.core.modules.HideFoliageModule;
import com.thelads.core.modules.MenuBlurModule;
import com.thelads.core.modules.MotionBlurModule;
import com.thelads.core.v1_8_9.feature.Options189;
import java.util.List;
import net.minecraft.block.Block;
import net.minecraft.block.BlockDoublePlant;
import net.minecraft.block.BlockTallGrass;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.init.Blocks;
import net.minecraft.util.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * PvP visuals on 1.8.9: Color Saturation, Motion Blur and Menu Blur (ScreenEffects189), Hide Foliage (HideFoliageMixin189, with
 * OptiFine's and Ionium's chunk builders alike: both draw blocks through BlockRendererDispatcher.renderBlock) and Clear Glass
 * (ClearGlassMixin189). 1.8.9 has grass, ferns, dead bushes and the double tall grass and large fern; no seagrass, nether plants or
 * leaf litter. The client tick keeps the hidden groups current and reloads the chunks when they change; Clear Glass reloads resources
 * (F3+T) once when it is turned on or off.
 */
public final class PvpVisuals189 {
    private static volatile int hiddenMask;
    /** Clear Glass's state when the glass textures last loaded. */
    private static volatile boolean glassLoadedClear;
    private final HideFoliageModule foliage;
    private final ClearGlassModule glass;

    public PvpVisuals189() {
        ModuleSupport.registerBuiltIn(ColorSaturationModule.NAME, MotionBlurModule.NAME, MenuBlurModule.NAME, HideFoliageModule.NAME, ClearGlassModule.NAME);
        foliage = (HideFoliageModule) Options189.module(HideFoliageModule.NAME);
        glass = (ClearGlassModule) Options189.module(ClearGlassModule.NAME);
        ScreenEffects189.modules((ColorSaturationModule) Options189.module(ColorSaturationModule.NAME),
            (MotionBlurModule) Options189.module(MotionBlurModule.NAME), (MenuBlurModule) Options189.module(MenuBlurModule.NAME));
    }

    @SubscribeEvent
    public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();
        int mask = HideFoliage.mask(foliage);
        if (mask != hiddenMask) {
            hiddenMask = mask;
            if (mc.renderGlobal != null && mc.theWorld != null) mc.renderGlobal.loadRenderers();
        }
        if (mc.theWorld != null && glass.isEnabled() != glassLoadedClear) {
            glassLoadedClear = glass.isEnabled(); // once, even if the reload fails
            mc.refreshResources();
        }
    }

    @SubscribeEvent
    public void frame(TickEvent.RenderTickEvent event) {
        if (event.phase == TickEvent.Phase.START) ScreenEffects189.frame();
    }

    /** After every other handler (Better Resolution's composite comes first), even cancelled: the world is done, the hand comes next. */
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public void worldDone(RenderHandEvent event) {
        ScreenEffects189.afterWorld(Minecraft.getMinecraft());
    }

    /** Asked by the chunk builders for every block (BlockRendererDispatcher.renderBlock): no allocation but for a tall plant's top. */
    public static boolean hidden(IBlockState state, BlockPos pos, IBlockAccess world) {
        int mask = hiddenMask;
        if (mask == 0) return false;
        Block block = state.getBlock();
        if (block == Blocks.tallgrass)
            return HideFoliage.hides(mask, state.getValue(BlockTallGrass.TYPE) == BlockTallGrass.EnumType.DEAD_BUSH ? HideFoliage.BUSHES : HideFoliage.GRASS);
        if (block == Blocks.deadbush) return HideFoliage.hides(mask, HideFoliage.BUSHES);
        if (block != Blocks.double_plant || !HideFoliage.hides(mask, HideFoliage.TALL)) return false;
        // ponytail: a top half keeps no variant of its own, so it reads the half below (one BlockPos); fine for plant tops only
        if (state.getValue(BlockDoublePlant.HALF) == BlockDoublePlant.EnumBlockHalf.UPPER) state = world.getBlockState(pos.down());
        if (state.getBlock() != Blocks.double_plant) return false;
        BlockDoublePlant.EnumPlantType type = state.getValue(BlockDoublePlant.VARIANT);
        return type == BlockDoublePlant.EnumPlantType.GRASS || type == BlockDoublePlant.EnumPlantType.FERN;
    }

    /** TextureAtlasSprite.loadSprite: each frame's full-size pixels, before the mipmaps are made. */
    public static void glassLoaded(String iconName, List<int[][]> frames, int width, int height) {
        if (iconName == null || !ClearGlass.isGlass189(iconName)) return;
        boolean clear = Options189.enabled(ClearGlassModule.NAME);
        glassLoadedClear = clear;
        if (!clear) return;
        for (int[][] frame : frames) if (frame != null && frame.length > 0 && frame[0] != null) ClearGlass.clear(frame[0], width, height);
    }
}
