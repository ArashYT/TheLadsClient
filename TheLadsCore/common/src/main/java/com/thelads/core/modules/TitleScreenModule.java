package com.thelads.core.modules;

import com.thelads.core.config.ConfigManager;
import com.thelads.core.config.DropdownOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.SliderOption;

public class TitleScreenModule extends Module {
    public TitleScreenModule() {
        super("TitleScreen", "Customize the title screen elements.");
        addOption(new SliderOption("Account Card Scale", 100, 50, 150, 5));
        addOption(new DropdownOption("Layout", 0, "New", "Legacy"));
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

    private static Object layoutOption() {
        Module module = ModuleManager.getInstance().getModule("TitleScreen");
        return module == null ? null : module.getOption("Layout");
    }
}
