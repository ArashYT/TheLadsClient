package com.thelads.core.v1_20_1.ladsapi.minigame.world;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.thelads.core.client.ladsapi.minigame.FxState;
import com.thelads.core.client.ladsapi.minigame.Minigame;
import com.thelads.core.v1_20_1.game.RenderScale1201;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.LoggerFactory;

/**
 * Minigame fx shaders on 1.20.1 (blur, grayscale, sepia, invert): our own PostChains on the main target, run once the world is drawn and
 * before the HUD. Vanilla's single post effect (spectator creeper/spider/invert) is a different PostChain that GameRenderer processes just
 * before; both stack and vanilla's is never touched. A chain that fails to load is logged once and that shader stays off until resources
 * reload. Stands aside while an Iris shader pack renders the world.
 */
public final class FxPost1201 {
    private static final Map<FxState.Shader, PostChain> CHAINS = new EnumMap<>(FxState.Shader.class);
    private static boolean failed;
    private static int width, height;

    private FxPost1201() {}

    private static ResourceLocation id(FxState.Shader shader) {
        return new ResourceLocation("theladscore", "shaders/post/mg_" + shader.name().toLowerCase(java.util.Locale.ROOT) + ".json");
    }

    /** GameRenderer.render: the world is done, the HUD comes next. */
    public static void afterWorld(Minecraft mc) {
        FxState.Shader shader = mc.level == null ? FxState.Shader.NONE : Minigame.fx.shader(Minigame.now());
        if (shader == FxState.Shader.NONE) {
            if (!CHAINS.isEmpty()) close(); // nothing stale stays in memory once the effect has ended
            return;
        }
        if (failed || RenderScale1201.shadersInUse()) return;
        RenderTarget main = mc.getMainRenderTarget();
        if (main.width != width || main.height != height) {
            width = main.width;
            height = main.height;
            for (PostChain c : CHAINS.values()) c.resize(width, height);
        }
        PostChain chain = CHAINS.get(shader);
        if (chain == null) {
            try {
                chain = new PostChain(mc.getTextureManager(), mc.getResourceManager(), main, id(shader));
                chain.resize(main.width, main.height);
                CHAINS.put(shader, chain);
            } catch (Exception broken) {
                LoggerFactory.getLogger("TheLadsCore").error("Minigame fx: {} did not load; it stays off until resources reload", id(shader), broken);
                failed = true;
                return;
            }
        }
        boolean blend = org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_BLEND), depth = org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_DEPTH_TEST);
        RenderSystem.disableBlend();
        RenderSystem.disableDepthTest();
        RenderSystem.resetTextureMatrix();
        chain.process(mc.getFrameTime());
        main.bindWrite(true);
        if (blend) RenderSystem.enableBlend();
        if (depth) RenderSystem.enableDepthTest();
    }

    /** Resources reloaded (GameRenderer.reloadShaders): the chains load again on next use. */
    public static void reload() {
        close();
        failed = false;
    }

    private static void close() {
        for (PostChain c : CHAINS.values()) c.close();
        CHAINS.clear();
    }
}
