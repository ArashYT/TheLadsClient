package com.thelads.core.modules;

import com.thelads.core.config.BoolOption;
import com.thelads.core.config.Module;

/**
 * Lads Badge module: controls whether the Lads Client badge appears next to Lads players in tab and nametags,
 * and whether the local player's own badge is shown.
 */
public final class LadsBadgeModule extends Module {
    public static final String NAME = "Lads Badge";

    public final BoolOption tab;
    public final BoolOption nametag;
    public final BoolOption showMine;

    public LadsBadgeModule() {
        super(NAME, "Show the Lads Client badge next to Lads players in tab and above their heads.");
        this.tab = addOption(new BoolOption("Tab", true));
        this.nametag = addOption(new BoolOption("Nametag", true));
        this.showMine = addOption(new BoolOption("Show mine", true));
        setCategory(Category.HUD);
        setEnabled(true);
    }
}
