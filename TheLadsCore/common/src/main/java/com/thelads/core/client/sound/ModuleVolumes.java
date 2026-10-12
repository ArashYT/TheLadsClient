package com.thelads.core.client.sound;

import com.thelads.core.config.ConfigManager;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.SliderOption;
import com.thelads.core.modules.ComboSoundsModule;
import com.thelads.core.modules.KillBannerModule;

/**
 * The Kill Banners and Combo Sounds sliders every version adds to the game's Music &amp; Sounds screen: the very value of each
 * module's own Volume (read as the screen opens, written and saved as the slider moves), so the two places always agree.
 */
public final class ModuleVolumes {
    public static final String[] LABELS = {"Kill Banners", "Combo Sounds"};

    private ModuleVolumes() {}

    public static double get(int slider) {
        SliderOption volume = volume(slider);
        return volume == null ? 0 : volume.getValue();
    }

    /** Sets the module's Volume (to its 5% steps) and saves it shortly after (a drag is one write). */
    public static void set(int slider, double value) {
        SliderOption volume = volume(slider);
        if (volume == null) return;
        volume.setValue(value);
        module(slider).touch();
        ConfigManager.saveLater();
    }

    /** The whole percent the module keeps for {@code value} (its 5% steps), as the slider's caption shows it. */
    public static int percent(double value) {
        return (int) Math.round(Math.max(0, Math.min(1, value)) * 20) * 5;
    }

    private static Module module(int slider) {
        return ModuleManager.getInstance().getModule(slider == 0 ? "KillBanner" : ComboSoundsModule.NAME);
    }

    private static SliderOption volume(int slider) {
        Module module = module(slider);
        return module instanceof KillBannerModule kill ? kill.volume : module instanceof ComboSoundsModule combo ? combo.volume : null;
    }
}
