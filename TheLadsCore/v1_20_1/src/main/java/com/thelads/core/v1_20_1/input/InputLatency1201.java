package com.thelads.core.v1_20_1.input;

import com.mojang.blaze3d.platform.Window;
import com.thelads.core.perf.FramePacer;
import com.thelads.core.perf.PreciseWait;
import com.thelads.core.perf.RawMouseBatch;
import com.thelads.core.v1_20_1.input.mixin.MouseHandlerInvoker1201;
import com.thelads.core.v1_20_1.input.mixin.RenderSystemInvoker1201;
import java.nio.ByteBuffer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWNativeWin32;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL32C;
import org.lwjgl.system.JNI;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.system.Platform;
import org.lwjgl.system.windows.User32;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Two built-in input remakes for 1.20.1 (plain Fabric and Forge + Sinytra Connector), always on, no setting, each with a JVM kill
 * switch, each standing down while the original mod is installed and falling back to vanilla on any failure.
 *
 * <p>Buffered raw mouse input (Ixeris' safe part, Windows): with Raw Input on and the cursor grabbed, GLFW handles every WM_INPUT
 * message alone, with a GetRawInputData call and a JNI upcall into MouseHandler.onMove, which turns the player each time: thousands
 * of times a second with a 1000-8000 Hz mouse. Before each event poll this reads every queued raw input record in one
 * GetRawInputBuffer call, sums the motion (RawMouseBatch) and hands it to onMove once. The frame limiter no longer waits inside
 * glfwWaitEventsTimeout (which ran each event through GLFW as it arrived): it sleeps precisely (PreciseWait) and polls afterwards.
 * Kill switch -Dlads.ixeris=false; stands down for the session if anything but GLFW's mouse registration shares the raw input
 * queue, or a mouse reports absolute positions (tablets, remote desktop), so GLFW handles those as before.
 *
 * <p>Input-latency pacing (Reflex AntiLag): FramePacer with a GL fence per frame; when it waited, the frame polls input again so
 * the ticks and the camera see the freshest input. Kill switch -Dlads.reflex=false.
 */
public final class InputLatency1201 {
    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore-1.20.1");
    /** Mod ids the originals may use, on Fabric or Forge. */
    private static final String[] IXERIS_IDS = {"ixeris"}, REFLEX_IDS = {"reflex", "reflexantilag", "reflex_antilag", "antilag"};
    private static final int BUFFER_BYTES = 16 * 1024, MAX_DEVICES = 8, RAWINPUTDEVICE_BYTES = 16;
    private static final long RECHECK_NS = 1_000_000_000L;

    static boolean ixeris, reflex;
    private static long getRawInputBuffer, getRegisteredDevices;
    private static ByteBuffer records, devices;
    private static long sizeAddress;
    private static final RawMouseBatch batch = new RawMouseBatch();
    private static long recheckAt;
    private static boolean ownQueue;
    private static long lastFrameEnd;
    /** QA counters: polls that read the raw input queue, mouse records summed, onMove calls made from them. */
    public static long batchedPolls, batchedRecords, batchedMoves;

    static final FramePacer PACER = new FramePacer();
    private static boolean glChecked;
    private static long previous, older;
    /** QA: Probe pauses the pacing for its with/without comparison. */
    public static boolean qaReflexPaused;

    private InputLatency1201() {}

    /** From the client initializer. */
    public static void init() {
        boolean windows = Platform.get() == Platform.WINDOWS;
        ixeris = windows && !killed("lads.ixeris") && !installed("Ixeris", IXERIS_IDS);
        reflex = !killed("lads.reflex") && !installed("Reflex AntiLag", REFLEX_IDS);
        if (ixeris) {
            try {
                getRawInputBuffer = User32.getLibrary().getFunctionAddress("GetRawInputBuffer");
                getRegisteredDevices = User32.getLibrary().getFunctionAddress("GetRegisteredRawInputDevices");
                if (getRawInputBuffer == 0 || getRegisteredDevices == 0) throw new IllegalStateException("user32 lacks the raw input buffer functions");
                records = MemoryUtil.memAlloc(BUFFER_BYTES);
                devices = MemoryUtil.memAlloc(MAX_DEVICES * RAWINPUTDEVICE_BYTES);
                sizeAddress = MemoryUtil.nmemAlloc(4);
            } catch (Throwable t) {
                ixeris = false;
                LOGGER.warn("Buffered raw mouse input is off: " + t);
            }
        }
        LOGGER.info("Lads input: buffered raw mouse input {}, input-latency pacing {} (GLFW {})", ixeris ? "on" : "off",
            reflex ? "on" : "off", GLFW.glfwGetVersionString());
    }

    private static boolean killed(String property) {
        return "false".equalsIgnoreCase(System.getProperty(property));
    }

    /** The original mod's jar: FabricLoader, and Forge's ModList by reflection (Connector does not list Forge mods to Fabric). */
    private static boolean installed(String name, String[] ids) {
        for (String id : ids) {
            boolean loaded = FabricLoader.getInstance().isModLoaded(id);
            if (!loaded) {
                try {
                    Class<?> modList = Class.forName("net.minecraftforge.fml.ModList");
                    Object list = modList.getMethod("get").invoke(null);
                    loaded = list != null && (Boolean) modList.getMethod("isLoaded", String.class).invoke(list, id);
                } catch (Throwable ignored) {} // not Forge
            }
            if (loaded) {
                LOGGER.info("Lads input: {} is installed ({}); its built-in remake stands down.", name, id);
                return true;
            }
        }
        return false;
    }

    /** Head of RenderSystem.pollEvents: the queued raw mouse motion, summed, before GLFW sees it. */
    public static void beforePoll() {
        if (!ixeris) return;
        try {
            Minecraft mc = Minecraft.getInstance();
            Window window = mc.getWindow();
            long handle = window == null ? 0 : window.getWindow();
            if (handle == 0 || !mc.isWindowActive() || GLFW.glfwGetInputMode(handle, GLFW.GLFW_CURSOR) != GLFW.GLFW_CURSOR_DISABLED
                || GLFW.glfwGetInputMode(handle, GLFW.GLFW_RAW_MOUSE_MOTION) != GLFW.GLFW_TRUE) {
                recheckAt = 0; // GLFW registers raw input when the cursor is grabbed: check again then
                return;
            }
            long now = System.nanoTime();
            if (now >= recheckAt) {
                boolean own = onlyGlfwMouse(GLFWNativeWin32.glfwGetWin32Window(handle));
                if (own != ownQueue) LOGGER.info(own ? "Lads input: buffered raw mouse input active."
                    : "Lads input: something besides GLFW's mouse registration reads raw input; GLFW handles it alone for now.");
                ownQueue = own;
                recheckAt = now + RECHECK_NS;
            }
            if (!ownQueue) return;
            batch.clear();
            long address = MemoryUtil.memAddress(records);
            for (int reads = 0; reads < 64; reads++) {
                MemoryUtil.memPutInt(sizeAddress, BUFFER_BYTES);
                int count = JNI.invokePPI(address, sizeAddress, RawMouseBatch.HEADER_BYTES, getRawInputBuffer);
                if (count <= 0) break; // 0: queue empty, -1: error (GLFW keeps whatever is left)
                batch.add(records, count);
            }
            batchedPolls++;
            batchedRecords += batch.mouse;
            if (batch.absolute > 0) {
                ixeris = false;
                LOGGER.info("Lads input: a mouse reports absolute positions (tablet or remote desktop); buffered raw mouse input is off for this session.");
            }
            if (batch.dx == 0 && batch.dy == 0) return;
            MouseHandler mouse = mc.mouseHandler;
            double x = mouse.xpos() + batch.dx, y = mouse.ypos() + batch.dy;
            ((MouseHandlerInvoker1201) mouse).lads$onMove(handle, x, y);
            GLFW.glfwSetCursorPos(handle, x, y); // GLFW's own virtual cursor follows, as after its WM_INPUT handling
            batchedMoves++;
        } catch (Throwable t) {
            ixeris = false;
            LOGGER.warn("Buffered raw mouse input failed; GLFW handles the mouse alone from now: " + t);
        }
    }

    /** True when the only raw input registration is GLFW's mouse for this window, so reading the queue takes nothing from others. */
    private static boolean onlyGlfwMouse(long hwnd) {
        long address = MemoryUtil.memAddress(devices);
        MemoryUtil.memPutInt(sizeAddress, MAX_DEVICES);
        int count = JNI.invokePPI(address, sizeAddress, RAWINPUTDEVICE_BYTES, getRegisteredDevices);
        if (count <= 0) return false;
        for (int i = 0; i < count; i++) {
            long device = address + (long) i * RAWINPUTDEVICE_BYTES;
            int page = MemoryUtil.memGetShort(device) & 0xFFFF, usage = MemoryUtil.memGetShort(device + 2) & 0xFFFF;
            if (page != 1 || usage != 2 || MemoryUtil.memGetAddress(device + 8) != hwnd) return false;
        }
        return true;
    }

    /** RenderSystem.limitDisplayFPS: true when this waited instead (no event pumping during the wait, input polled after it). */
    public static boolean limit(int fps) {
        if (!ixeris) return false;
        lastFrameEnd = PreciseWait.until(lastFrameEnd + 1_000_000_000L / Math.max(1, fps), () -> false);
        RenderSystemInvoker1201.lads$pollEvents();
        return true;
    }

    /** Head of Minecraft.runTick: wait for the GPU, then read input once more. */
    public static void frameStart() {
        if (!reflex || qaReflexPaused) return;
        try {
            if (!glChecked) {
                glChecked = true;
                if (!GL.getCapabilities().OpenGL32) {
                    reflex = false;
                    LOGGER.info("Lads input: no OpenGL 3.2 fences; input-latency pacing is off.");
                    return;
                }
            }
            if (PACER.frameStart(() -> done(previous), () -> done(older)) > 0) RenderSystemInvoker1201.lads$pollEvents();
        } catch (Throwable t) {
            stopPacing(t);
        }
    }

    /** RenderSystem.flipFrame, right before glfwSwapBuffers: the feedback for the pacing, then this frame's fence. */
    public static void beforeSwap() {
        if (!reflex || qaReflexPaused || !glChecked) return;
        try {
            PACER.frameEnd(done(previous));
            if (older != 0) GL32C.glDeleteSync(older);
            older = previous;
            previous = GL32C.glFenceSync(GL32C.GL_SYNC_GPU_COMMANDS_COMPLETE, 0);
        } catch (Throwable t) {
            stopPacing(t);
        }
    }

    private static boolean done(long fence) {
        return fence == 0 || GL32C.glClientWaitSync(fence, 0, 0) != GL32C.GL_TIMEOUT_EXPIRED; // signalled, or failed: never wait on it
    }

    private static void stopPacing(Throwable t) {
        reflex = false;
        try {
            if (older != 0) GL32C.glDeleteSync(older);
            if (previous != 0) GL32C.glDeleteSync(previous);
        } catch (Throwable ignored) {}
        older = previous = 0;
        LOGGER.warn("Input-latency pacing failed; vanilla frame timing from now: " + t);
    }
}
