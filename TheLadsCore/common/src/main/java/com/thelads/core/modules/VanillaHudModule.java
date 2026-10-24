package com.thelads.core.modules;

import com.thelads.core.config.BoolOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.SliderOption;

/**
 * Vanilla HUD (OneClient's VanillaHUD toggles): hardcore hearts everywhere, the XP bar and level each hidden, the held item's name kept
 * on screen or gone without its fade, and vanilla's effect icons (26.x and 1.20.1 only; 1.8.9 draws none) hidden or moved. Moving and
 * scaling the hotbar and its bars is Raised's. Visual only. The adapters read what {@link #refresh} cached.
 */
public final class VanillaHudModule extends Module {
    public static final String NAME = "VanillaHUD";

    public final BoolOption hardcore = addOption(new BoolOption("Always Hardcore Hearts", false));
    public final BoolOption xpBar = addOption(new BoolOption("Hide XP Bar", false));
    public final BoolOption xpLevel = addOption(new BoolOption("Hide XP Level", false));
    public final BoolOption keepName = addOption(new BoolOption("Persistent Item Name", false));
    public final BoolOption noFade = addOption(new BoolOption("Item Name No Fade", false));
    public final BoolOption effects = addOption(new BoolOption("Hide Effect Icons", false));
    public final SliderOption effectsX = addOption(new SliderOption("Effect Icons X Offset", 0, -300, 300, 1));
    public final SliderOption effectsY = addOption(new SliderOption("Effect Icons Y Offset", 0, -300, 300, 1));

    /** Cached each tick (all false and 0 while the module is off). */
    public boolean hardcoreHearts, hideXpBar, hideXpLevel, persistentName, nameNoFade, hideEffects;
    public int effectsDx, effectsDy;

    public VanillaHudModule() {
        super(NAME, "Hardcore hearts, hide the XP bar or level, keep the item name on screen, hide or move the effect icons.");
    }

    public void refresh() {
        boolean on = isEnabled();
        hardcoreHearts = on && hardcore.get();
        hideXpBar = on && xpBar.get();
        hideXpLevel = on && xpLevel.get();
        persistentName = on && keepName.get();
        nameNoFade = on && noFade.get();
        hideEffects = on && effects.get();
        effectsDx = on ? effectsX.getIntValue() : 0;
        effectsDy = on ? effectsY.getIntValue() : 0;
    }

    /**
     * The held item name's timer after vanilla's tick (it counts down from 40; the last 10 ticks fade): kept full while an item is
     * held with Persistent Item Name, cut before the fade with No Fade.
     */
    public int highlightTicks(int ticks, boolean holding) {
        if (persistentName && holding) return Math.max(ticks, 40);
        if (nameNoFade && ticks > 0 && ticks < 10) return 0;
        return ticks;
    }
}
