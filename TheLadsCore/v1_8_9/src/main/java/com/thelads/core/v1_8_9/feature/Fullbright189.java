package com.thelads.core.v1_8_9.feature;

import com.thelads.core.config.ModuleManager;
import com.thelads.core.modules.FullbrightModule;
import com.thelads.core.v1_8_9.mixin.EntityRendererAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.GameSettings;

/**
 * Fullbright: while it is on, the game's Brightness (gammaSetting) is Minecraft's brightest (1.0) and the lightmap is lifted by the Brightness %, set every
 * client tick (the tick runs under a menu too), and put back the moment it goes off. The lightmap is rebuilt only when
 * EntityRenderer is told it is out of date, which the game does from a tick that does not run while a menu pauses a singleplayer
 * world, so a change here marks it out of date itself and shows in the next frame, menu open or not. options.txt keeps the
 * player's own Brightness (saving): before 1.9.1 a save while Fullbright was on stored its gamma, which after a restart was taken
 * for the player's own, so turning Fullbright off left the world bright (a value above vanilla's 1.0 is now taken as that).
 */
public final class Fullbright189 {
    private static float original = Float.NaN, shown = Float.NaN;
    /** QA only (Probe172Ui's control run): the lightmap is left to the game's own tick again, as before 1.7.2. */
    static volatile boolean qaDefer;
    private static float lastFactor = -1.0f;

    private Fullbright189() {}

    private static FullbrightModule fullbrightModule;
    public static FullbrightModule module() {
        if (fullbrightModule == null) {
            com.thelads.core.config.Module m = ModuleManager.getInstance().getModule("Fullbright");
            if (m instanceof FullbrightModule) fullbrightModule = (FullbrightModule) m;
        }
        return fullbrightModule;
    }

    public static boolean isEnabled() {
        FullbrightModule module = module();
        return module != null && module.isEnabled() && Minecraft.getMinecraft().thePlayer != null;
    }

    public static float factor() {
        FullbrightModule module = module();
        return (module != null && Minecraft.getMinecraft().thePlayer != null) ? module.lightmapFactor() : 0.0f;
    }

    public static void tick(Minecraft mc) {
        FullbrightModule module = module();
        float currentFactor = factor();
        boolean factorChanged = Float.compare(currentFactor, lastFactor) != 0;
        lastFactor = currentFactor;

        if (module != null && module.isEnabled() && mc.thePlayer != null) {
            float wanted = (float) module.effectiveGamma();
            if (Float.isNaN(original)) original = Math.min(1F, mc.gameSettings.gammaSetting);
            if (mc.gameSettings.gammaSetting != wanted || factorChanged) set(mc, wanted);
        } else if (!Float.isNaN(original)) {
            float saved = original;
            original = Float.NaN;
            set(mc, saved);
        } else if (factorChanged && mc.entityRenderer != null) {
            if (!qaDefer) ((EntityRendererAccessor) mc.entityRenderer).ladsLightmapDirty(true);
        }
    }

    /** GameSettings.saveOptions (GameSettingsMixin): the player's own Brightness is written while Fullbright's shows. */
    public static void saving(GameSettings settings, boolean start) {
        if (start && !Float.isNaN(original)) {
            shown = settings.gammaSetting;
            settings.gammaSetting = original;
        } else if (!start && !Float.isNaN(shown)) {
            settings.gammaSetting = shown;
            shown = Float.NaN;
        }
    }

    private static void set(Minecraft mc, float gamma) {
        mc.gameSettings.gammaSetting = gamma;
        if (!qaDefer && mc.entityRenderer != null) ((EntityRendererAccessor) mc.entityRenderer).ladsLightmapDirty(true);
    }

    /**
     * Overrides the lightmap colors with Fullbright's factor.
     * Returns true if modified (requiring dynamic texture update).
     */
    public static boolean applyLightmap(int[] lightmapColors) {
        float f = factor();
        if (f <= 0 || lightmapColors == null) return false;
        for (int i = 0; i < lightmapColors.length; i++) {
            int color = lightmapColors[i];
            int a = (color >> 24) & 0xFF;
            int r = (color >> 16) & 0xFF;
            int g = (color >> 8) & 0xFF;
            int b = color & 0xFF;

            r = (int) (r + (255 - r) * f);
            g = (int) (g + (255 - g) * f);
            b = (int) (b + (255 - b) * f);

            lightmapColors[i] = (a << 24) | (r << 16) | (g << 8) | b;
        }
        return true;
    }
}
