package com.thelads.core.modules;

import com.thelads.core.client.sound.CustomSounds;
import com.thelads.core.config.BoolOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.SliderOption;
import com.thelads.core.config.SoundOption;

/**
 * Oof Mod (Feather's Oof Mod, Badlion's OofMod): players' hurt and death sounds play the classic oof instead (Minecraft's own
 * random/classic_hurt, the theladscore:oof sound event every version ships), or a sound file you choose from the sound library. Only
 * what this client plays changes. Each version's sound hook asks {@link #replaces} for a player hurt or death sound and, when it is
 * replaced, plays {@link #playFile} or else the built-in oof at {@link #volume}.
 */
public final class OofModule extends Module {
    public static final String NAME = "Oof Mod";
    public static final int HURT = 1, DEATH = 2;
    /** Vanilla's hurt sound fades out linearly over 16 blocks; an uploaded file (played without position) fades the same way. */
    static final double RANGE = 16;

    public final BoolOption hurt = addOption(new BoolOption("Hurt sounds", true));
    public final BoolOption death = addOption(new BoolOption("Death sounds", true));
    /** Empty: the built-in classic oof. */
    public final SoundOption sound = addOption(new SoundOption("Oof sound"));
    public final SliderOption volume = addOption(new SliderOption("Volume", 1, 0, 1, .05));

    public OofModule() {
        super(NAME, "Players' hurt and death sounds play the classic oof, or a sound you choose. Only your client hears it.");
    }

    private static OofModule active() {
        return ModuleManager.getInstance().getModule(NAME) instanceof OofModule module && module.isEnabled() ? module : null;
    }

    /** A player's hurt ({@link #HURT}) or death ({@link #DEATH}) sound is about to play: true when this module replaces it. */
    public static boolean replaces(int kind) {
        OofModule module = active();
        return module != null && (kind == HURT ? module.hurt.get() : kind == DEATH && module.death.get());
    }

    /**
     * Plays the chosen file for a replaced sound {@code distance} blocks away; false when no file is chosen (the version then plays
     * the built-in oof).
     */
    public static boolean playFile(double distance) {
        OofModule module = active();
        if (module == null || !module.sound.isSet()) return false;
        CustomSounds.play(module.sound.get(), module.volume.getValue() * falloff(distance));
        return true;
    }

    /** The built-in oof's volume factor (on the replaced sound's own volume). */
    public static float volume() {
        OofModule module = active();
        return module == null ? 1 : (float) module.volume.getValue();
    }

    /** Loudness of a sound {@code distance} blocks away: 1 at the listener down to 0 at {@link #RANGE}. */
    static double falloff(double distance) {
        return Math.max(0, 1 - distance / RANGE);
    }
}
