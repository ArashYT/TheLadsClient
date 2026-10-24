package com.thelads.core.v26_2.pvp;

import com.mojang.blaze3d.platform.NativeImage;
import com.thelads.core.client.ClearGlass;
import com.thelads.core.client.HideFoliage;
import com.thelads.core.client.ScreenEffects;
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
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;

/**
 * PvP visuals on 26.x: Color Saturation and Motion Blur (ScreenEffects26), Menu Blur through vanilla's own menu blur (the
 * MenuBlur*Mixin26), Hide Foliage (HideFoliageMixin26) and Clear Glass (ClearGlassMixin26). The client tick keeps the hidden blocks
 * current (the module, its groups and the server rules for Tall Plants) and rebuilds the chunk meshes when they change; Clear Glass
 * reloads resources once when it is turned on or off.
 */
public final class PvpVisuals26 {
    private static volatile Block[] hidden = new Block[0];
    private static int hiddenMask;
    /** Clear Glass's state when the glass textures last loaded. */
    private static volatile boolean glassLoadedClear;
    private static HideFoliageModule foliage;
    private static ClearGlassModule glass;
    private static MenuBlurModule menuBlur;

    private PvpVisuals26() {}

    public static void register() {
        ModuleSupport.registerBuiltIn(ColorSaturationModule.NAME, MotionBlurModule.NAME, HideFoliageModule.NAME, ClearGlassModule.NAME);
        ModuleSupport.registerBuiltInLimited(MenuBlurModule.NAME, "Minecraft 26.x blurs menus itself (Video Settings > Menu Background Blur): "
            + "while this module is on, its strength and toggles replace that setting in a world, and inventories, which 26.x only dims, are blurred too.");
        ClientTickEvents.END_CLIENT_TICK.register(PvpVisuals26::tick);
    }

    private static void tick(Minecraft mc) {
        if (foliage == null) {
            foliage = (HideFoliageModule) ModuleManager.getInstance().getModule(HideFoliageModule.NAME);
            glass = (ClearGlassModule) ModuleManager.getInstance().getModule(ClearGlassModule.NAME);
            menuBlur = (MenuBlurModule) ModuleManager.getInstance().getModule(MenuBlurModule.NAME);
        }
        int mask = HideFoliage.mask(foliage);
        if (mask != hiddenMask) {
            hiddenMask = mask;
            hidden = blocks(mask);
            if (mc.level != null) mc.levelExtractor.allChanged();
        }
        if (mc.level != null && glass.isEnabled() != glassLoadedClear) {
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
                Identifier key = Identifier.withDefaultNamespace(id);
                if (BuiltInRegistries.BLOCK.containsKey(key)) blocks.add(BuiltInRegistries.BLOCK.getValue(key));
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
    public static void glassLoaded(Identifier name, int frameWidth, int frameHeight, NativeImage image) {
        if (!"minecraft".equals(name.getNamespace()) || !ClearGlass.isGlass(name.getPath())) return;
        boolean clear = glass != null ? glass.isEnabled() : ModuleManager.getInstance().getModule(ClearGlassModule.NAME).isEnabled();
        glassLoadedClear = clear;
        if (!clear || image.getWidth() != frameWidth) return;
        int[] pixels = new int[image.getWidth() * image.getHeight()];
        for (int y = 0; y < image.getHeight(); y++)
            for (int x = 0; x < frameWidth; x++) pixels[y * frameWidth + x] = image.getPixel(x, y);
        ClearGlass.clear(pixels, frameWidth, frameHeight);
        for (int y = 0; y < image.getHeight(); y++)
            for (int x = 0; x < frameWidth; x++) image.setPixel(x, y, pixels[y * frameWidth + x]);
    }

    private static int kind(Screen screen) {
        return screen instanceof AbstractContainerScreen<?> ? ScreenEffects.INVENTORY : screen instanceof PauseScreen ? ScreenEffects.PAUSE : ScreenEffects.OTHER;
    }

    /** Screen.extractBackground of in-game UI (inventories and the like), which vanilla only dims. */
    public static void inGameBlur(Minecraft mc, Screen screen, GuiGraphicsExtractor graphics) {
        if (menuBlur == null || mc.level == null || menuBlur.radius(kind(screen)) < 1) return;
        try {
            graphics.blurBeforeThisStratum();
        } catch (IllegalStateException alreadyBlurred) {
            // the screen blurred this frame itself; one blur a frame is all vanilla allows
        }
    }

    /** Screen.extractBlurredBackground's Menu Background Blur: the module's choice for this kind of screen, in a world. */
    public static int menuBlur(Minecraft mc, Screen screen, int vanilla) {
        return menuBlur == null || !menuBlur.isEnabled() || mc.level == null ? vanilla : menuBlur.radius(kind(screen));
    }

    /** The blur radius every menu blur this frame uses (GameRenderer.extractOptions). */
    public static int blurStrength(int vanilla) {
        return menuBlur == null || !menuBlur.isEnabled() || Minecraft.getInstance().level == null ? vanilla : menuBlur.strength.getIntValue();
    }
}
