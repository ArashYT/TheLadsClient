package com.thelads.core.v1_8_9.render;

import net.minecraft.client.Minecraft;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Fast Render with Ionium (1.9.13): the world is drawn straight into the window instead of Minecraft's main framebuffer, which then
 * had to be copied to the window every frame. It is what OptiFine's Fast Render does, on by default in Lads since 1.7.3, so every
 * Lads feature already runs this way on the OptiFine path; with Ionium the copy was ~39 us of a ~480 us GPU frame on a GTX 1660 Ti
 * (Legion bench: +10% FPS with the framebuffer off). OpenGlHelper.isFramebufferEnabled answers false while it is on
 * (IoniumFastRenderMixin189), as under OptiFine's Fast Render; vanilla's shader effects (spectating a creeper, Super Secret
 * Settings, spectator outlines) are skipped meanwhile, as they need the framebuffer. Better Resolution at a scale other than native
 * needs it too: then the framebuffer is made again and Fast Render steps aside, as RenderScale189 does with OptiFine's.
 * Kill switch -Dlads.fastRender=false.
 */
public final class IoniumFastRender189 {
    private static final Logger LOGGER = LogManager.getLogger("TheLadsCore-1.8.9");
    public static final boolean ENABLED = IoniumFastRender189.class.getClassLoader().getResource("org/taumc/ionium/launch/IoniumTweaker.class") != null
        && !"false".equalsIgnoreCase(System.getProperty("lads.fastRender"));
    private static boolean needed;

    private IoniumFastRender189() {}

    /** Whether the main framebuffer is off now. */
    public static boolean on() {
        return ENABLED && !needed;
    }

    /** RenderScale189, every frame: Better Resolution scaling needs the main framebuffer. */
    public static void betterResolution(boolean scaling) {
        if (!ENABLED || scaling == needed) return;
        needed = scaling;
        Minecraft mc = Minecraft.getMinecraft();
        if (!scaling) mc.entityRenderer.stopUseShader(); // a shader effect made meanwhile would draw from a framebuffer that is gone
        mc.getFramebuffer().createBindFramebuffer(mc.displayWidth, mc.displayHeight); // made again, or left unused
        LOGGER.info("Lads Fast Render (Ionium) " + (scaling ? "off for Better Resolution" : "back on"));
    }
}
