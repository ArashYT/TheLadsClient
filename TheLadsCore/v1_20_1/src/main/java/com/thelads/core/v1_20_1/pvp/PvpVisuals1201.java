package com.thelads.core.v1_20_1.pvp;

import com.mojang.blaze3d.platform.NativeImage;
import com.thelads.core.client.ClearGlass;
import com.thelads.core.client.HideFoliage;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.ModuleSupport;
import com.thelads.core.modules.ClearGlassModule;
import com.thelads.core.modules.ColorSaturationModule;
import com.thelads.core.modules.HideFoliageModule;
import com.thelads.core.modules.MenuBlurModule;
import com.thelads.core.modules.MotionBlurModule;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;

/**
 * PvP visuals on 1.20.1: Color Saturation, Motion Blur and Menu Blur (ScreenEffects1201), Hide Foliage (HideFoliageMixin1201),
 * Clear Glass (ClearGlassMixin1201) and Custom Skies (CustomSky1201). The client tick keeps the hidden blocks current (the module,
 * its groups and the server rules for Tall Plants) and rebuilds the chunk meshes when they change; Clear Glass reloads resources once
 * when it is turned on or off.
 */
public final class PvpVisuals1201 {
    private static volatile Block[] hidden = new Block[0];
    private static int hiddenMask;
    /** Clear Glass's state when the glass textures last loaded. */
    private static volatile boolean glassLoadedClear;
    private static HideFoliageModule foliage;
    private static ClearGlassModule glass;

    private PvpVisuals1201() {}

    public static void register() {
        ModuleSupport.registerBuiltIn(ColorSaturationModule.NAME, MotionBlurModule.NAME, MenuBlurModule.NAME, HideFoliageModule.NAME, ClearGlassModule.NAME,
            com.thelads.core.modules.CustomSkyModule.NAME);
        ModuleSupport.registerUnavailable(com.thelads.core.modules.ChunkAnimatorModule.NAME, "Sodium draws a whole region of chunks at once on "
            + "the GPU, so chunks cannot move one by one; Chunk Animator works on 1.8.9 with OptiFine.");
        ClientTickEvents.END_CLIENT_TICK.register(PvpVisuals1201::tick);
        CustomSky1201.register();
    }

    private static void tick(Minecraft mc) {
        if (foliage == null) {
            foliage = (HideFoliageModule) ModuleManager.getInstance().getModule(HideFoliageModule.NAME);
            glass = (ClearGlassModule) ModuleManager.getInstance().getModule(ClearGlassModule.NAME);
        }
        int mask = HideFoliage.mask(foliage);
        if (mask != hiddenMask) {
            hiddenMask = mask;
            hidden = blocks(mask);
            mc.levelRenderer.allChanged();
        }
        if (mc.level != null && mc.getOverlay() == null && glass.isEnabled() != glassLoadedClear) {
            glassLoadedClear = glass.isEnabled(); // once, even if the reload is refused
            mc.reloadResourcePacks();
        }
    }

    /** The blocks hidden for this mask: exact ids, and only those this version has. */
    private static Block[] blocks(int mask) {
        List<Block> blocks = new ArrayList<>();
        for (int group = 0; group < HideFoliage.IDS.length; group++) {
            if (!HideFoliage.hides(mask, group)) continue;
            for (String id : HideFoliage.IDS[group]) {
                ResourceLocation key = new ResourceLocation("minecraft", id);
                if (BuiltInRegistries.BLOCK.containsKey(key)) blocks.add(BuiltInRegistries.BLOCK.get(key));
            }
        }
        return blocks.toArray(new Block[0]);
    }

    /** Asked by the chunk builders for every block: no allocation. */
    public static boolean hidden(Block block) {
        for (Block b : hidden) if (b == block) return true;
        return false;
    }

    /** SpriteContents, as each texture loads (on the resource reload's worker threads). */
    public static void glassLoaded(ResourceLocation name, int frameWidth, int frameHeight, NativeImage image) {
        if (!"minecraft".equals(name.getNamespace()) || !ClearGlass.isGlass(name.getPath())) return;
        boolean clear = glass != null ? glass.isEnabled() : ModuleManager.getInstance().getModule(ClearGlassModule.NAME).isEnabled();
        glassLoadedClear = clear;
        if (!clear || image.getWidth() != frameWidth) return;
        int[] pixels = new int[image.getWidth() * image.getHeight()];
        for (int y = 0; y < image.getHeight(); y++)
            for (int x = 0; x < frameWidth; x++) pixels[y * frameWidth + x] = image.getPixelRGBA(x, y);
        ClearGlass.clear(pixels, frameWidth, frameHeight);
        for (int y = 0; y < image.getHeight(); y++)
            for (int x = 0; x < frameWidth; x++) image.setPixelRGBA(x, y, pixels[y * frameWidth + x]);
    }
}
