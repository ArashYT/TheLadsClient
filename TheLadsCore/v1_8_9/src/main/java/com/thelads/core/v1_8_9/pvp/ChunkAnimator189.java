package com.thelads.core.v1_8_9.pvp;

import com.thelads.core.client.ChunkAnimator;
import com.thelads.core.config.ModuleSupport;
import com.thelads.core.modules.ChunkAnimatorModule;
import com.thelads.core.v1_8_9.feature.Options189;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraftforge.fml.client.FMLClientHandler;

/**
 * Chunk Animator on 1.8.9: a chunk's first draw at a new position (RenderChunk.setPosition) starts its clock, and every draw adds
 * client.ChunkAnimator's offset to the chunk's translation (ChunkRenderContainer.preRenderChunk, used by vanilla's and OptiFine's
 * chunk lists). Ionium (Celeritas) draws a region of chunks in one call with no per-chunk translation, so there the module is
 * unavailable; OptiFine's Render Regions do the same, so with OptiFine it needs Render Regions off.
 */
public final class ChunkAnimator189 {
    static final boolean IONIUM = ChunkAnimator189.class.getClassLoader().getResource("org/taumc/ionium/launch/IoniumTweaker.class") != null;
    /** The time of this frame (RenderTickEvent start), so the chunks of one frame share it. */
    private static long now;
    private static ChunkAnimatorModule module;

    /** A RenderChunk's clock (ChunkAnimatorMixin189): 0 until it is first drawn where it now is. */
    public interface Clock {
        long lads$shownAt();

        void lads$setShownAt(long nanos);
    }

    private ChunkAnimator189() {}

    static void register() {
        if (IONIUM) {
            ModuleSupport.registerUnavailable(ChunkAnimatorModule.NAME, "Ionium draws a whole region of chunks at once on the GPU, so chunks "
                + "cannot move one by one; it works with OptiFine (Render Regions off).");
        } else if (FMLClientHandler.instance().hasOptifine()) {
            ModuleSupport.registerBuiltInLimited(ChunkAnimatorModule.NAME, "Needs OptiFine's Render Regions off: with them on, OptiFine draws "
                + "a region of chunks at once.");
        } else {
            ModuleSupport.registerBuiltIn(ChunkAnimatorModule.NAME);
        }
        module = (ChunkAnimatorModule) Options189.module(ChunkAnimatorModule.NAME);
    }

    static void frame(long nanos) {
        now = nanos;
    }

    /** ChunkRenderContainer.preRenderChunk, after the chunk's own translation: per chunk and layer, no allocation. */
    public static void translate(Clock chunk) {
        long shown = chunk.lads$shownAt();
        if (shown == 0) chunk.lads$setShownAt(shown = now);
        if (module == null || !module.isEnabled()) return;
        float offset = ChunkAnimator.offset(module.mode.getIndex() == 1, module.duration.getValue(), now - shown);
        if (offset != 0) GlStateManager.translate(0, offset, 0);
    }
}
