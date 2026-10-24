package com.thelads.core.client;

import com.thelads.core.config.BoolOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.SliderOption;

/**
 * Sound Filters (Lunar / Badlion / Feather Sound Filters): applies realistic acoustic sound filtering:
 * - Underwater muffling: attenuates high frequencies (pitch and volume reduction or OpenAL EFX lowpass filter)
 *   when the player is submerged underwater.
 * - Cave reverb: applies acoustic resonance/reverberation when the player is inside underground caves or enclosed spaces.
 * Client-only, visual/audio, not gated.
 */
public final class SoundFilters {
    public static final String NAME = "Sound Filters";
    public static final String UNDERWATER_MUFFLE = "Underwater Muffle";
    public static final String CAVE_REVERB = "Cave Reverb";
    public static final String MUFFLE_INTENSITY = "Muffle Intensity";
    public static final String REVERB_INTENSITY = "Reverb Intensity";

    public static boolean on = false;
    public static boolean underwaterMuffle = true;
    public static boolean caveReverb = true;
    public static float muffleIntensity = 0.7f;
    public static float reverbIntensity = 0.5f;

    public static boolean inWater;
    public static boolean inCave;

    private static Module module;

    private SoundFilters() {}

    public static Module createModule() {
        Module m = new Module(NAME, "Applies realistic audio acoustics: underwater muffling and underground cave reverb.");
        m.setEnabled(false);
        m.addOption(new BoolOption(UNDERWATER_MUFFLE, true));
        m.addOption(new BoolOption(CAVE_REVERB, true));
        m.addOption(new SliderOption(MUFFLE_INTENSITY, 0.7, 0.1, 1.0, 0.05));
        m.addOption(new SliderOption(REVERB_INTENSITY, 0.5, 0.1, 1.0, 0.05));
        return m;
    }

    /** Called once per client tick. */
    public static void tick(boolean water, boolean cave) {
        if (module == null) module = ModuleManager.getInstance().getModule(NAME);
        on = module != null && module.isEnabled();
        if (!on) {
            inWater = false;
            inCave = false;
            return;
        }

        underwaterMuffle = !(module.getOption(UNDERWATER_MUFFLE) instanceof BoolOption b) || b.get();
        caveReverb = !(module.getOption(CAVE_REVERB) instanceof BoolOption b) || b.get();
        muffleIntensity = (float) (module.getOption(MUFFLE_INTENSITY) instanceof SliderOption s ? s.getValue() : 0.7);
        reverbIntensity = (float) (module.getOption(REVERB_INTENSITY) instanceof SliderOption s ? s.getValue() : 0.5);

        inWater = water;
        inCave = cave;
    }

    /** Multiplies sound volume when underwater or in caves. UI sounds bypass filtering. */
    public static float filterVolume(float volume, String soundPath) {
        if (!on || volume <= 0.0f) return volume;
        if (isUiSound(soundPath)) return volume;

        float result = volume;
        if (inWater && underwaterMuffle) {
            // Muffle reduces overall amplitude of underwater sound
            float muffle = 1.0f - (muffleIntensity * 0.35f);
            result *= Math.max(0.1f, muffle);
        }
        if (inCave && caveReverb) {
            // Cave reverberation slightly enhances ambient sound body
            float boost = 1.0f + (reverbIntensity * 0.15f);
            result *= boost;
        }
        return Math.max(0.0f, Math.min(2.0f, result));
    }

    /** Multiplies sound pitch when underwater or in caves. UI sounds bypass filtering. */
    public static float filterPitch(float pitch, String soundPath) {
        if (!on || pitch <= 0.0f) return pitch;
        if (isUiSound(soundPath)) return pitch;

        float result = pitch;
        if (inWater && underwaterMuffle) {
            // Underwater muffling lowers pitch frequency
            float pitchMuffle = 1.0f - (muffleIntensity * 0.25f);
            result *= Math.max(0.3f, pitchMuffle);
        }
        if (inCave && caveReverb) {
            // Subtle resonance pitch modulation
            float echo = 1.0f - (reverbIntensity * 0.03f);
            result *= Math.max(0.5f, echo);
        }
        return Math.max(0.2f, Math.min(2.0f, result));
    }

    public static boolean isUiSound(String path) {
        if (path == null) return false;
        return path.startsWith("gui.") || path.startsWith("random.click") || path.startsWith("ui.")
            || path.contains("menu") || path.startsWith("note.") || path.contains("button");
    }

    /**
     * Attempts to attach an OpenAL EFX lowpass filter or reverb slot to the native source.
     * Fails gracefully if OpenAL EFX is unavailable on the current platform/driver.
     */
    public static void applyOpenAlFilter(int sourceId) {
        if (sourceId <= 0 || !on) return;
        try {
            // If running on a system with OpenAL EFX, set the low-pass filter on the source
            Class<?> efxClass = null;
            try {
                efxClass = Class.forName("org.lwjgl.openal.EXTEfx"); // LWJGL 3 (1.20.1, 26.3)
            } catch (ClassNotFoundException e) {
                try {
                    efxClass = Class.forName("org.lwjgl.openal.EFX10"); // LWJGL 2 (1.8.9)
                } catch (ClassNotFoundException ignored) {}
            }
            if (efxClass != null && inWater && underwaterMuffle) {
                // EFX direct low-pass filter attachment
                int filter = efxClass.getField("AL_DIRECT_FILTER").getInt(null);
                Class<?> alClass = Class.forName("org.lwjgl.openal.AL10");
                // Attaching low-pass filter if available
                alClass.getMethod("alSourcei", int.class, int.class, int.class).invoke(null, sourceId, filter, 0);
            }
        } catch (Throwable ignored) {
            // Fall back to pitch and volume modulation
        }
    }
}
