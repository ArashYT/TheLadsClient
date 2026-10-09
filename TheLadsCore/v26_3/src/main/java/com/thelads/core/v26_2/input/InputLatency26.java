package com.thelads.core.v26_2.input;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.commands.GpuFence;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.thelads.core.perf.FramePacer;
import com.thelads.core.perf.LatencyMeter;
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
    /** The thread that submits frames; with a threaded renderer (Ixeris in the 26.3 pack) the event poll may run elsewhere. */
    private static volatile Thread renderThread;
    /** QA (InputProbe26): pacing paused for the with/without comparison, the latency meter, GPU load (4096x4096 texture copies). */
    public static boolean qaPaused;
    static LatencyMeter qaMeter;
    static int qaCopies;
    private static GpuTexture loadA, loadB;

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

    static LatencyMeter meter() {
        if (qaMeter == null) qaMeter = new LatencyMeter(() -> RenderSystem.getDevice().createCommandEncoder().createFence(),
            fence -> ((GpuFence) fence).awaitCompletion(0L), fence -> ((GpuFence) fence).close());
        return qaMeter;
    }

    /** Head of RenderSystem.pollEvents. */
    public static void frameStart() {
        LatencyMeter meter = Thread.currentThread() == renderThread ? qaMeter : null;
        if (meter != null) meter.poll();
        pace();
        if (meter != null) meter.input();
    }

    private static void pace() {
        if (!reflex || qaPaused || Thread.currentThread() != renderThread) return; // fences belong to the submitting thread
        try {
            PACER.frameStart(() -> done(previous), () -> done(older));
        } catch (Throwable t) {
            stop(t);
        }
    }

    /** Right before the frame's CommandEncoder.submit. */
    public static void beforeSubmit() {
        if (renderThread == null) {
            renderThread = Thread.currentThread();
            LOGGER.info("Lads input: frames are submitted on thread '{}'", renderThread.getName());
        }
        if (qaCopies > 0) gpuLoad();
        if (qaMeter != null) qaMeter.fence();
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

    /** Right after the frame's present (QA only). */
    public static void afterPresent() {
        if (qaMeter != null) qaMeter.poll();
    }

    private static void gpuLoad() {
        var device = RenderSystem.getDevice();
        int size = 4096, usage = GpuTexture.USAGE_COPY_SRC | GpuTexture.USAGE_COPY_DST;
        if (loadA == null) {
            loadA = device.createTexture("Lads QA GPU load A", usage, GpuFormat.RGBA8_UNORM, size, size, 1, 1);
            loadB = device.createTexture("Lads QA GPU load B", usage, GpuFormat.RGBA8_UNORM, size, size, 1, 1);
        }
        var encoder = device.createCommandEncoder();
        for (int i = 0; i < qaCopies; i++) encoder.copyTextureToTexture(i % 2 == 0 ? loadA : loadB, i % 2 == 0 ? loadB : loadA, 0, 0, 0, 0, 0, size, size);
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
