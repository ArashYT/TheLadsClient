package com.thelads.core.modules;

import com.thelads.core.config.BoolOption;
import com.thelads.core.config.ConfigManager;
import com.thelads.core.config.DoubleOption;
import com.thelads.core.config.DropdownOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.SliderOption;

/** The title screen's settings, not a module to switch: the custom title screen is always on. */
public class TitleScreenModule extends Module {
    public static final String LOGO_SCALE = "Logo Scale";
    public static final String PANORAMA = "Panorama";
    public static final String PANORAMA_VERSION = "Panorama Version";

    public TitleScreenModule() {
        super("TitleScreen", "The title screen's layout, logo and account card. It is always on.");
        addOption(new DoubleOption(LOGO_SCALE, 1.0, 0.6, 1.6));
        addOption(new SliderOption("Account Card Scale", 100, 50, 150, 5));
        addOption(new DropdownOption("Layout", 0, "New", "Legacy"));
        addOption(new BoolOption(PANORAMA, true));
        // Default: the normal panorama (resource packs included); a release's own panorama overrides any pack. The list of
        // releases loads from Mojang later (PanoramaPicker), so the pick is saved by name and kept until the list has it.
        addOption(new DropdownOption(PANORAMA_VERSION, 0, com.thelads.core.client.title.PanoramaPicker.DEFAULT) {
            @Override
            public com.google.gson.JsonElement save() {
                return new com.google.gson.JsonPrimitive(getChoices()[getOwnIndex()]);
            }

            @Override
            public void load(com.google.gson.JsonElement element) {
                try {
                    select(element.getAsString());
                } catch (RuntimeException ignored) {
                }
            }
        });
        setEnabled(true);
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(true);
    }

    /** How big the title branding is drawn; it still fits inside the screen. */
    public static float logoScale() {
        Module module = ModuleManager.getInstance().getModule("TitleScreen");
        return module != null && module.getOption(LOGO_SCALE) instanceof DoubleOption scale ? (float) scale.get() : 1.0f;
    }

    /** The player's chosen title layout: the legacy one (TitleScreenTheme) over the default (TitleNoir). */
    public static boolean legacyLayout() {
        return layoutOption() instanceof DropdownOption layout && layout.getOwnIndex() == 1;
    }

    /** The title screen's layout toggle: the screen follows on its next frame, and the choice is kept. */
    public static void toggleLayout() {
        if (layoutOption() instanceof DropdownOption layout) {
            layout.setIndex(layout.getOwnIndex() == 1 ? 0 : 1);
            ConfigManager.save();
        }
    }

    /** Whether the new layout renders a rotating 3D panorama (default true); when false, a simple backdrop is drawn. */
    public static boolean panorama() {
        Module module = ModuleManager.getInstance().getModule("TitleScreen");
        return !(module != null && module.getOption(PANORAMA) instanceof BoolOption opt) || opt.get();
    }

    public static void setPanorama(boolean enabled) {
        Module module = ModuleManager.getInstance().getModule("TitleScreen");
        if (module != null && module.getOption(PANORAMA) instanceof BoolOption opt) {
            opt.set(enabled);
            ConfigManager.save();
        }
    }

    private static Object layoutOption() {
        Module module = ModuleManager.getInstance().getModule("TitleScreen");
        return module == null ? null : module.getOption("Layout");
    }
}
