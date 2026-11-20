package com.thelads.core.v1_8_9.ladsapi.minigame.world;

import com.thelads.core.client.ladsapi.minigame.FxState;
import com.thelads.core.client.ladsapi.minigame.Minigame;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.shader.ShaderGroup;
import net.minecraft.util.ResourceLocation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.thelads.core.v1_8_9.render.IoniumFastRender189;

/**
 * The fx shader kinds (blur, grayscale, sepia, invert) are post chains built from vanilla's own shader programs (blur,
 * color_convolve, invert, blit). They run through vanilla's own post-effect slot (EntityRenderer.loadShader), which already runs
 * after the world and before the HUD, so the HUD stays sharp. That slot is also vanilla's spectator effect (the creeper view): the
 * fx replaces it while a kind is active and drops it when the kind ends. Where the GPU has no shader support the kinds are ignored;
 * tint, vignette and flash do not need them.
 */
public final class FxShader189 {
    private static final Map<FxState.Shader, Boolean> BROKEN = new EnumMap<>(FxState.Shader.class);
    private static FxState.Shader loaded = FxState.Shader.NONE;
    private static ShaderGroup ours;
    private static final Logger LOGGER = LogManager.getLogger("TheLadsCore-1.8.9");


    private FxShader189() {}

    /**
     * renderWorld RETURN: keeps vanilla's post slot on the wanted kind; vanilla then runs it this frame. Fast Render with Ionium (on
     * by default) has no main framebuffer and cancels loadShader, so it steps aside while a kind is live (IoniumFastRender189.shaderFx).
     */
    public static void run(Minecraft mc, float partial) {
        FxState.Shader kind = Minigame.fx.shader(Minigame.now());
        EntityRenderer er = mc.entityRenderer;
        if (kind == FxState.Shader.NONE || !OpenGlHelper.shadersSupported || Boolean.TRUE.equals(BROKEN.get(kind))) {
            release(er);
            IoniumFastRender189.shaderFx(false);
            return;
        }
        IoniumFastRender189.shaderFx(true);
        if (!OpenGlHelper.isFramebufferEnabled()) { // OptiFine's Fast Render: no main framebuffer to run the chain on
            release(er);
            return;
        }
        if (loaded == kind && ours != null && er.getShaderGroup() == ours) return;
        er.loadShader(new ResourceLocation("theladscore", "shaders/post/mg_" + kind.name().toLowerCase(java.util.Locale.ROOT) + ".json"));
        ours = er.getShaderGroup();
        loaded = ours == null ? FxState.Shader.NONE : kind;
        if (ours == null) BROKEN.put(kind, Boolean.TRUE); // vanilla logged why
        LOGGER.info("Lads minigame fx shader {}: {}", kind, ours == null ? "not loaded" : "loaded");
    }

    private static void release(EntityRenderer er) {
        if (ours != null && er.getShaderGroup() == ours) er.stopUseShader();
        ours = null;
        loaded = FxState.Shader.NONE;
    }
}
