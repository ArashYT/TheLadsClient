package com.thelads.core.v26_2.input;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.commands.GpuFence;
import com.thelads.core.perf.FramePacer;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Built-in input-latency pacing for 26.3 (the Reflex AntiLag remake): FramePacer on RenderPearl's GpuFence, which both the
 * OpenGL and the Vulkan backend implement (a GL sync object, a Vulkan timeline semaphore value), so it paces either backend.
 * The wait runs right before Minecraft polls input for the frame; the fence is made right before the frame's submit, so it
 * belongs to that frame. Always on, no setting; kill switch -Dlads.reflex=false; stands down while the original mod is
 * installed; any failure turns it off for the session with vanilla frame timing.
 *
 * <p>Buffered raw mouse input (the Ixeris remake) has nothing to do here: 26.x reads the mouse through SDL3, whose Windows
 * backend already reads raw input in batches (GetRawInputBuffer) on its own thread, and 26.x's frame limiter already sleeps
 * precisely without pumping events.
 */
public final class InputLatency26 {
    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore");
    private static final String[] REFLEX_IDS = {"reflex", "reflexantilag", "reflex_antilag", "antilag"};
    static final FramePacer PACER = new FramePacer();
    static boolean reflex;
    private static GpuFence previous, older;
    /** QA: the probe pauses the pacing for its with/without comparison. */
    public static boolean qaPaused;

    private InputLatency26() {}

    public static void init() {
        reflex = !"false".equalsIgnoreCase(System.getProperty("lads.reflex"));
        for (String id : REFLEX_IDS) {
            if (reflex && FabricLoader.getInstance().isModLoaded(id)) {
                reflex = false;
                LOGGER.info("Lads input: Reflex AntiLag is installed ({}); its built-in remake stands down.", id);
            }
        }
        LOGGER.info("Lads input: input-latency pacing {}; buffered raw mouse input is SDL3's own on 26.x", reflex ? "on" : "off");
    }

    /** Head of RenderSystem.pollEvents. */
    public static void frameStart() {
        if (!reflex || qaPaused) return;
        try {
            PACER.frameStart(() -> done(previous), () -> done(older));
        } catch (Throwable t) {
            stop(t);
        }
    }

    /** Right before the frame's CommandEncoder.submit. */
    public static void beforeSubmit() {
        if (!reflex || qaPaused) return;
        try {
            PACER.frameEnd(done(previous));
            if (older != null) older.close();
            older = previous;
            previous = RenderSystem.getDevice().createCommandEncoder().createFence();
        } catch (Throwable t) {
            stop(t);
        }
    }

    private static boolean done(GpuFence fence) {
        return fence == null || fence.awaitCompletion(0L);
    }

    private static void stop(Throwable t) {
        reflex = false;
        older = previous = null;
        LOGGER.warn("Input-latency pacing failed; vanilla frame timing from now: " + t);
    }
}
