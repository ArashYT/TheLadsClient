package com.thelads.core.v1_8_9.feature;

import static com.thelads.core.v1_8_9.feature.CoreProbe.after;
import static com.thelads.core.v1_8_9.feature.CoreProbe.check;
import static com.thelads.core.v1_8_9.feature.CoreProbe.retry;

import com.thelads.core.perf.InputLatencyQa;
import com.thelads.core.v1_8_9.input.InputLatency189;
import java.util.Arrays;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * QA only (LADS_VERIFY_189_ONLY=input): the input-latency pacing's in-game evidence (InputLatencyQa): frame rate and
 * input-to-GPU-done latency with the pacing on and off, without and with a calibrated GPU load (full-screen nearly transparent
 * quads in one draw). The QA frame cap (120) stays.
 */
final class ProbeInput189 {
    private static final Logger LOG = LogManager.getLogger("TheLadsCore");
    static final List<CoreProbe.Step> STEPS = Arrays.<CoreProbe.Step>asList(ProbeInput189::start, ProbeInput189::run);
    private static InputLatencyQa qa;
    private static int quads;

    private ProbeInput189() {}

    private static boolean start(Minecraft mc) {
        MinecraftForge.EVENT_BUS.register(new ProbeInput189());
        qa = new InputLatencyQa(new InputLatencyQa.Target() {
            @Override public void paced(boolean on) { InputLatency189.qaPaused = !on; }
            @Override public void gpuLoad(int count) { quads = count; }
            @Override public void flood(int mode) {}
            @Override public void window(String action) { windowAction(Minecraft.getMinecraft(), action); }
            @Override public void log(String line) { LOG.info(line); }
        }, InputLatency189.meter(), InputLatency189.pacer(), 128);
        LOG.info("Lads input probe: 1.8.9, limit {}, vsync {}, pacing {}", mc.gameSettings.limitFramerate, mc.gameSettings.enableVsync, InputLatency189.active());
        return after(20);
    }

    private static int savedWidth, savedHeight;

    /** Resize and restore the window, and F11 on and off (Borderless189 makes it borderless with BorderlessFullscreen). */
    private static void windowAction(Minecraft mc, String action) {
        try {
            switch (action) {
                case "resize":
                    savedWidth = org.lwjgl.opengl.Display.getWidth();
                    savedHeight = org.lwjgl.opengl.Display.getHeight();
                    org.lwjgl.opengl.Display.setDisplayMode(new org.lwjgl.opengl.DisplayMode(800, 480));
                    break;
                case "restore":
                    org.lwjgl.opengl.Display.setDisplayMode(new org.lwjgl.opengl.DisplayMode(savedWidth, savedHeight));
                    break;
                default:
                    if (mc.isFullScreen() != "fullscreen".equals(action)) mc.toggleFullscreen();
            }
        } catch (org.lwjgl.LWJGLException e) {
            throw new IllegalStateException(e);
        }
        LOG.info("Lads input probe: window {} -> {}x{}, fullscreen {}", action, mc.displayWidth, mc.displayHeight, mc.isFullScreen());
    }

    private static boolean run(Minecraft mc) {
        if (!qa.tick()) return retry(qa.soaking() ? 20 : 1);
        check(true, "input probe finished (its END or FAILED line holds the verdict)");
        return after(1);
    }

    @SubscribeEvent
    public void overlay(RenderGameOverlayEvent.Post event) {
        if (event.type != RenderGameOverlayEvent.ElementType.ALL || quads <= 0) return;
        Minecraft mc = Minecraft.getMinecraft();
        float w = mc.displayWidth, h = mc.displayHeight;
        GlStateManager.pushMatrix();
        GlStateManager.disableTexture2D();
        GlStateManager.disableDepth();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.color(1, 1, 1, 1 / 255f);
        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer buffer = tessellator.getWorldRenderer();
        buffer.begin(7, DefaultVertexFormats.POSITION);
        for (int i = 0; i < quads; i++) {
            buffer.pos(0, h, 0).endVertex();
            buffer.pos(w, h, 0).endVertex();
            buffer.pos(w, 0, 0).endVertex();
            buffer.pos(0, 0, 0).endVertex();
        }
        tessellator.draw();
        GlStateManager.color(1, 1, 1, 1);
        GlStateManager.disableBlend();
        GlStateManager.enableDepth();
        GlStateManager.enableTexture2D();
        GlStateManager.popMatrix();
    }
}
