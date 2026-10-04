package com.thelads.core.v1_8_9.feature;

import static com.thelads.core.v1_8_9.feature.CoreProbe.after;
import static com.thelads.core.v1_8_9.feature.CoreProbe.check;
import static com.thelads.core.v1_8_9.feature.CoreProbe.retry;
import static com.thelads.core.v1_8_9.feature.CoreProbe.screenshot;

import com.thelads.core.client.RenderScalePolicy;
import com.thelads.core.modules.BetterResolutionModule;
import java.util.Arrays;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL33;

/**
 * QA only, run by CoreProbe in its QA world after Probe150e: Better Resolution photographed at native, 50 % with each Algorithm,
 * the Balanced preset and 200 %, at noon, each with its FPS (the QA cap is 120) and the GPU time of a whole frame (an OpenGL timer
 * query from RenderTickEvent START to END), with Autohide off so every photo has the HUD, looking 30 degrees down (ground,
 * horizon and sky in one view). The module settings, view and world time are put back as found.
 */
final class Probe170r {
    private static final Logger LOG = LogManager.getLogger("TheLadsCore");
    static final List<CoreProbe.Step> STEPS = Arrays.<CoreProbe.Step>asList(Probe170r::start, Probe170r::stage, Probe170r::done);
    private static final String[] NAMES = {"native", "50-linear", "50-nearest", "50-smooth", "50-sharp", "balanced-smooth", "200-smooth"};
    private static final int[][] STAGES = { // preset, scale, algorithm
        {0, 100, RenderScalePolicy.LINEAR}, {0, 50, RenderScalePolicy.LINEAR}, {0, 50, RenderScalePolicy.NEAREST}, {0, 50, RenderScalePolicy.SMOOTH},
        {0, 50, RenderScalePolicy.SHARP}, {2, 100, RenderScalePolicy.SMOOTH}, {0, 200, RenderScalePolicy.SMOOTH}};
    private static int stage, preset, method;
    private static double scale;
    private static boolean enabled, autohide;
    private static long time;
    private static float pitch;
    private static GpuTimer timer;

    private Probe170r() {}

    private static BetterResolutionModule module() { return (BetterResolutionModule) Options189.module(BetterResolutionModule.NAME); }

    private static boolean start(Minecraft mc) {
        BetterResolutionModule module = module();
        enabled = module.isEnabled();
        preset = module.preset.getIndex();
        scale = module.scale.getValue();
        method = module.method.getIndex();
        time = mc.theWorld.getWorldTime();
        autohide = Options189.enabled("Autohide");
        pitch = mc.thePlayer.rotationPitch;
        mc.thePlayer.rotationPitch = mc.thePlayer.prevRotationPitch = 30;
        Options189.module("Autohide").setEnabled(false);
        check(RenderScale189.program(RenderScalePolicy.SMOOTH) != 0 && RenderScale189.program(RenderScalePolicy.SHARP) != 0,
            "Better Resolution: the Smooth and Sharp programs compile and link on this GPU (with OptiFine loaded)");
        mc.getIntegratedServer().addScheduledTask(() -> mc.getIntegratedServer().worldServers[0].setWorldTime(6000));
        timer = new GpuTimer();
        MinecraftForge.EVENT_BUS.register(timer);
        stage = 0;
        apply(module);
        return after(60);
    }

    /** One stage per run: logged and photographed after 3 s at its settings, then the next is applied. */
    private static boolean stage(Minecraft mc) {
        int[] s = STAGES[stage];
        boolean scaled = RenderScale189.scaledFrames > 0 && (s[0] != 0 || s[1] != 100);
        LOG.info("Lads 1.8.9 core probe: Better Resolution {}: {} FPS, GPU {} ms per frame ({} frames), world {}, window {}x{}", NAMES[stage],
            Minecraft.getDebugFPS(), String.format("%.3f", timer.milliseconds()), timer.frames,
            scaled ? RenderScale189.scaledWidth + "x" + RenderScale189.scaledHeight : "native", mc.displayWidth, mc.displayHeight);
        screenshot(mc, "170-resolution-" + NAMES[stage]);
        if (++stage == STAGES.length) return after(1);
        apply(module());
        return retry(60);
    }

    private static boolean done(Minecraft mc) {
        MinecraftForge.EVENT_BUS.unregister(timer);
        timer.close();
        BetterResolutionModule module = module();
        module.preset.setIndex(preset);
        module.scale.setValue(scale);
        module.method.setIndex(method);
        module.setEnabled(enabled);
        Options189.module("Autohide").setEnabled(autohide);
        mc.thePlayer.rotationPitch = mc.thePlayer.prevRotationPitch = pitch;
        mc.getIntegratedServer().addScheduledTask(() -> mc.getIntegratedServer().worldServers[0].setWorldTime(time));
        check(true, "Better Resolution: " + STAGES.length + " stages photographed (lads-qa/screenshots/170-resolution-*.png); settings and time restored");
        return after(10);
    }

    private static void apply(BetterResolutionModule module) {
        int[] s = STAGES[stage];
        module.preset.setIndex(s[0]);
        module.scale.setValue(s[1]);
        module.method.setIndex(s[2]);
        module.setEnabled(true);
        timer.reset(); // the frames after this one are the new stage's
    }

    /** GPU time per frame, two queries in turn so a result is read a frame later without waiting for the GPU. */
    public static final class GpuTimer {
        private final int[] queries = {GL15.glGenQueries(), GL15.glGenQueries()};
        private int frame;
        private long nanos;
        int frames;

        @SubscribeEvent
        public void frame(TickEvent.RenderTickEvent event) {
            int query = queries[frame & 1];
            if (event.phase == TickEvent.Phase.START) {
                if (frame >= 2 && GL15.glGetQueryObjecti(query, GL15.GL_QUERY_RESULT_AVAILABLE) == GL11.GL_TRUE) {
                    nanos += GL33.glGetQueryObjecti64(query, GL15.GL_QUERY_RESULT);
                    frames++;
                }
                GL15.glBeginQuery(GL33.GL_TIME_ELAPSED, query);
            } else {
                GL15.glEndQuery(GL33.GL_TIME_ELAPSED);
                frame++;
            }
        }

        void reset() { nanos = 0; frames = 0; }
        double milliseconds() { return frames == 0 ? 0 : nanos / 1e6 / frames; }
        void close() { GL15.glDeleteQueries(queries[0]); GL15.glDeleteQueries(queries[1]); }
    }
}
