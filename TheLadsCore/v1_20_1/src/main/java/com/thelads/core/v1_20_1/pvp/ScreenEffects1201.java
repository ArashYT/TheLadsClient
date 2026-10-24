package com.thelads.core.v1_20_1.pvp;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.shaders.AbstractUniform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.thelads.core.client.ColorGrade;
import com.thelads.core.client.ScreenEffects;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.modules.ColorSaturationModule;
import com.thelads.core.modules.MenuBlurModule;
import com.thelads.core.modules.MotionBlurModule;
import com.thelads.core.v1_20_1.game.RenderScale1201;
import com.thelads.core.v1_20_1.pvp.mixin.PostChainAccessor1201;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostPass;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.LoggerFactory;

/**
 * Color Saturation, Motion Blur and Menu Blur on 1.20.1: vanilla post chains on the game's main target. Color and motion blur run
 * once the world (hand included) is drawn and before the HUD (RenderScaleMixin's finishWorld, after Better Resolution's composite),
 * Menu Blur when a menu starts its background (ScreenBlurMixin1201), over the world and the HUD. The chains' programs are
 * assets/minecraft/shaders/program/lads_*; the blur is vanilla's shaders/post/blur.json. All three stand aside while an Iris shader
 * pack renders the world. A chain that fails to load is logged once and stays off until resources reload.
 */
public final class ScreenEffects1201 {
    private static final ResourceLocation COLOR = new ResourceLocation("theladscore", "shaders/post/lads_color.json");
    private static final ResourceLocation MOTION = new ResourceLocation("theladscore", "shaders/post/lads_motion_blur.json");
    private static final ResourceLocation BLUR = new ResourceLocation("minecraft", "shaders/post/blur.json");
    private static final ColorGrade GRADE = new ColorGrade();
    private static PostChain color, motion, blur;
    /** The uniforms set every frame, found once per chain: the motion blend and the blur radius of each pass. */
    private static AbstractUniform[] motionBlend, blurRadius;
    private static boolean failed, colorStale = true, motionFresh;
    private static int width, height;
    private static long frameStart, blurredFrame;
    private static ColorSaturationModule colorModule;
    private static MotionBlurModule motionModule;
    private static MenuBlurModule menuModule;

    private ScreenEffects1201() {}

    /** GameRenderer.render: the world is done, the HUD comes next. */
    public static void afterWorld(Minecraft mc) {
        long now = System.nanoTime(), frame = now - frameStart;
        frameStart = now;
        if (colorModule == null) {
            ModuleManager modules = ModuleManager.getInstance();
            colorModule = (ColorSaturationModule) modules.getModule(ColorSaturationModule.NAME);
            motionModule = (MotionBlurModule) modules.getModule(MotionBlurModule.NAME);
            menuModule = (MenuBlurModule) modules.getModule(MenuBlurModule.NAME);
        }
        boolean motionOn = motionModule.isEnabled();
        if (!motionOn && motion != null) {
            motion.close(); // its last frame must not come back when it is turned on again
            motion = null;
        }
        if (failed || mc.level == null || !motionOn && !colorModule.isEnabled() || RenderScale1201.shadersInUse()) return;
        RenderTarget main = mc.getMainRenderTarget();
        resize(main);
        if (colorModule.isEnabled()) {
            if (GRADE.update(colorModule)) colorStale = true;
            if (GRADE.active && (color != null || (color = load(mc, main, COLOR)) != null)) {
                if (colorStale) upload();
                run(color, main, mc);
            }
        }
        if (motionOn && (motion != null || (motion = load(mc, main, MOTION)) != null)) {
            float retain = motionFresh ? 0 : ScreenEffects.motionRetain(motionModule.strength.getValue(), frame);
            motionFresh = false;
            for (AbstractUniform u : motionBlend) u.set(retain);
            run(motion, main, mc);
        }
    }

    /** Screen.renderBackground in a world: blurs what is on screen so far, once a frame. */
    public static void menuBackground(Minecraft mc, Screen screen, GuiGraphics graphics) {
        if (menuModule == null || mc.level == null || blurredFrame == frameStart || failed) return;
        int kind = screen instanceof AbstractContainerScreen ? ScreenEffects.INVENTORY : screen instanceof PauseScreen ? ScreenEffects.PAUSE : ScreenEffects.OTHER;
        int radius = menuModule.radius(kind);
        if (radius <= 0 || RenderScale1201.shadersInUse()) return;
        RenderTarget main = mc.getMainRenderTarget();
        resize(main);
        if (blur == null && (blur = load(mc, main, BLUR)) == null) return;
        blurredFrame = frameStart;
        graphics.flush(); // the HUD and anything batched so far, so it is in the picture that gets blurred
        for (AbstractUniform u : blurRadius) u.set((float) radius);
        boolean blend = org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_BLEND), depth = org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_DEPTH_TEST);
        run(blur, main, mc);
        if (blend) RenderSystem.enableBlend(); // the menu draws on with the states it had
        if (depth) RenderSystem.enableDepthTest();
    }

    /** Resources reloaded (GameRenderer.reloadShaders): the chains load again on next use. */
    public static void reload() {
        for (PostChain chain : new PostChain[]{color, motion, blur}) if (chain != null) chain.close();
        color = motion = blur = null;
        failed = false;
        colorStale = true;
    }

    private static PostChain load(Minecraft mc, RenderTarget main, ResourceLocation id) {
        try {
            PostChain chain = new PostChain(mc.getTextureManager(), mc.getResourceManager(), main, id);
            chain.resize(main.width, main.height);
            if (id == COLOR) colorStale = true;
            if (id == MOTION) {
                motionFresh = true;
                motionBlend = uniforms(chain, "BlendFactor");
            }
            if (id == BLUR) blurRadius = uniforms(chain, "Radius");
            return chain;
        } catch (Exception broken) {
            LoggerFactory.getLogger("TheLadsCore").error("Screen effects: {} did not load; color, motion and menu blur stay off until resources reload", id, broken);
            failed = true;
            return null;
        }
    }

    private static void resize(RenderTarget main) {
        if (main.width == width && main.height == height) return;
        width = main.width;
        height = main.height;
        if (color != null) color.resize(width, height);
        if (blur != null) blur.resize(width, height);
        if (motion != null) {
            motion.resize(width, height);
            motionFresh = true; // its saved frame is gone
        }
    }

    private static void upload() {
        float[] m = GRADE.matrix;
        for (PostPass pass : ((PostChainAccessor1201) color).lads$passes()) {
            var effect = pass.getEffect();
            effect.safeGetUniform("RedRow").set(m[0], m[1], m[2], m[3]);
            effect.safeGetUniform("GreenRow").set(m[4], m[5], m[6], m[7]);
            effect.safeGetUniform("BlueRow").set(m[8], m[9], m[10], m[11]);
            effect.safeGetUniform("Vibrance").set(GRADE.vibrance);
        }
        colorStale = false;
    }

    private static AbstractUniform[] uniforms(PostChain chain, String name) {
        List<PostPass> passes = ((PostChainAccessor1201) chain).lads$passes();
        AbstractUniform[] found = new AbstractUniform[passes.size()];
        for (int i = 0; i < found.length; i++) found[i] = passes.get(i).getEffect().safeGetUniform(name);
        return found;
    }

    /** As GameRenderer runs its own post effect, then the main target again for what comes next. */
    private static void run(PostChain chain, RenderTarget main, Minecraft mc) {
        RenderSystem.disableBlend();
        RenderSystem.disableDepthTest();
        RenderSystem.resetTextureMatrix();
        chain.process(mc.getFrameTime());
        main.bindWrite(true);
    }
}
