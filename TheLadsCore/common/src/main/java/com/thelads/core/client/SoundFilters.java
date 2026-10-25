package com.thelads.core.client;

import com.thelads.core.config.BoolOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.SliderOption;

/**
 * Sound Filters (Lunar / Badlion / Feather Sound Filters): lowers volume and pitch underwater
 * when the player is submerged underwater.
 * Client-only, visual/audio, not gated.
 */
public final class SoundFilters {
    public static final String NAME = "Sound Filters";
    public static final String UNDERWATER_MUFFLE = "Underwater Muffle";
    public static final String MUFFLE_INTENSITY = "Muffle Intensity";

    public static boolean on = false;
    public static boolean underwaterMuffle = true;
    public static float muffleIntensity = 0.7f;

    public static boolean inWater;

    private static Module module;

    private SoundFilters() {}

    public static Module createModule() {
        Module m = new Module(NAME, "Lowers volume and pitch underwater.");
        m.setEnabled(false);
        m.addOption(new BoolOption(UNDERWATER_MUFFLE, true));
        m.addOption(new SliderOption(MUFFLE_INTENSITY, 0.7, 0.1, 1.0, 0.05));
        return m;
    }

    /** Called once per client tick. */
    public static void tick(boolean water) {
        if (module == null) module = ModuleManager.getInstance().getModule(NAME);
        on = module != null && module.isEnabled();
        if (!on) {
            inWater = false;
            return;
        }

        underwaterMuffle = !(module.getOption(UNDERWATER_MUFFLE) instanceof BoolOption b) || b.get();
        muffleIntensity = (float) (module.getOption(MUFFLE_INTENSITY) instanceof SliderOption s ? s.getValue() : 0.7);

        inWater = water;
    }

    /** Multiplies sound volume when underwater. UI sounds and music/records bypass filtering. */
    public static float filterVolume(float volume, String soundPath, String category) {
        if (!on || volume <= 0.0f) return volume;
        if (isUiSound(soundPath) || isMusicOrRecord(category)) return volume;

        float result = volume;
        if (inWater && underwaterMuffle) {
            // Muffle reduces overall amplitude of underwater sound
            float muffle = 1.0f - (muffleIntensity * 0.35f);
            result *= Math.max(0.1f, muffle);
        }
        return Math.max(0.0f, Math.min(2.0f, result));
    }

    /** Multiplies sound pitch when underwater. UI sounds and music/records bypass pitch shifting. */
    public static float filterPitch(float pitch, String soundPath, String category) {
        if (!on || pitch <= 0.0f) return pitch;
        if (isUiSound(soundPath) || isMusicOrRecord(category)) return pitch;

        float result = pitch;
        if (inWater && underwaterMuffle) {
            // Underwater muffling lowers pitch frequency
            float pitchMuffle = 1.0f - (muffleIntensity * 0.25f);
            result *= Math.max(0.3f, pitchMuffle);
        }
        return Math.max(0.2f, Math.min(2.0f, result));
    }

    public static boolean isUiSound(String path) {
        if (path == null) return false;
        return path.startsWith("gui.") || path.startsWith("random.click") || path.startsWith("ui.")
            || path.contains("menu") || path.startsWith("note.") || path.contains("button");
    }

    /** By the sound's category (SoundCategory / SoundSource name, the same on every version), not its id: a pack or mod can name music anything. */
    public static boolean isMusicOrRecord(String category) {
        return "MUSIC".equals(category) || "RECORDS".equals(category);
    }
}
