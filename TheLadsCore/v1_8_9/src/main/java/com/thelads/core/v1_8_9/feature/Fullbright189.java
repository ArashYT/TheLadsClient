package com.thelads.core.v1_8_9.feature;

import com.thelads.core.config.ModuleManager;
import com.thelads.core.modules.FullbrightModule;
import com.thelads.core.v1_8_9.mixin.EntityRendererAccessor;
import net.minecraft.client.Minecraft;

/**
 * Fullbright: while it is on, the game's Brightness (gammaSetting) is the Gamma slider times the Brightness Multiplier, set every
 * client tick (the tick runs under a menu too), and put back the moment it goes off. The lightmap is rebuilt only when
 * EntityRenderer is told it is out of date, which the game does from a tick that does not run while a menu pauses a singleplayer
 * world, so a change here marks it out of date itself and shows in the next frame, menu open or not.
 */
public final class Fullbright189 {
    private static float original = Float.NaN;
    /** QA only (Probe172Ui's control run): the lightmap is left to the game's own tick again, as before 1.7.2. */
    static volatile boolean qaDefer;

    private Fullbright189() {}

    public static void tick(Minecraft mc) {
        com.thelads.core.config.Module module = ModuleManager.getInstance().getModule("Fullbright");
        if (module instanceof FullbrightModule && module.isEnabled() && mc.thePlayer != null) {
            float wanted = (float) ((FullbrightModule) module).effectiveGamma();
            if (Float.isNaN(original)) original = mc.gameSettings.gammaSetting;
            if (mc.gameSettings.gammaSetting != wanted) set(mc, wanted);
        } else if (!Float.isNaN(original)) {
            float saved = original;
            original = Float.NaN;
            set(mc, saved);
        }
    }

    private static void set(Minecraft mc, float gamma) {
        mc.gameSettings.gammaSetting = gamma;
        if (!qaDefer) ((EntityRendererAccessor) mc.entityRenderer).ladsLightmapDirty(true);
    }
}
