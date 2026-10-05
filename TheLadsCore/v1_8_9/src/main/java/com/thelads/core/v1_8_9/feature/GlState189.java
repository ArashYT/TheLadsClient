package com.thelads.core.v1_8_9.feature;

import org.lwjgl.opengl.GL11;

/**
 * The blend, depth and alpha switches as GlStateManager tracks them, without a glIsEnabled round trip to the driver (1.7.3). Every
 * Lads GL change goes through GlStateManager, so its cache is what the GL context holds. BooleanStateMixin hands over the three
 * switches as the game creates them; a switch it did not see is asked of the driver as before.
 */
public final class GlState189 {
    /** Mixed into GlStateManager.BooleanState. */
    public interface Switch { boolean on(); }

    private static Switch blend, depth, alpha;

    private GlState189() {}

    /** From BooleanStateMixin's constructor hook, once per switch. */
    public static void track(int capability, Switch state) {
        if (capability == GL11.GL_BLEND && blend == null) blend = state;
        else if (capability == GL11.GL_DEPTH_TEST && depth == null) depth = state;
        else if (capability == GL11.GL_ALPHA_TEST && alpha == null) alpha = state;
    }

    /** QA (Probe173Cap): all three switches were handed over, and agree with the driver right now. */
    static boolean agreesWithDriver() {
        return blend != null && depth != null && alpha != null && blend.on() == GL11.glIsEnabled(GL11.GL_BLEND)
            && depth.on() == GL11.glIsEnabled(GL11.GL_DEPTH_TEST) && alpha.on() == GL11.glIsEnabled(GL11.GL_ALPHA_TEST);
    }

    public static boolean blend() { return blend != null ? blend.on() : GL11.glIsEnabled(GL11.GL_BLEND); }

    public static boolean depth() { return depth != null ? depth.on() : GL11.glIsEnabled(GL11.GL_DEPTH_TEST); }

    public static boolean alpha() { return alpha != null ? alpha.on() : GL11.glIsEnabled(GL11.GL_ALPHA_TEST); }
}
