package com.thelads.core.modules;

import com.thelads.core.config.BoolOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.Option;
import com.thelads.core.config.SliderOption;

/**
 * Survival Guards (Badlion's Survival Anti-Breaking Tool, Anti-Strip Log and Anti-Amethyst Mining): the adapters' game-mode hooks drop
 * the player's own click before it is sent (no packet goes out) and say why in the action bar. Sneaking lets the click through. It only
 * ever sends less than vanilla, so it is not gated. 1.8.9 has no log stripping or amethyst: those two options are left out there.
 */
public final class SurvivalGuardsModule extends Module {
    public static final String NAME = "Survival Guards";
    public static final String TOOL_MESSAGE = "Survival Guards: this tool is almost broken (sneak to use it anyway)";
    public static final String STRIP_MESSAGE = "Survival Guards: stripping logs is off (sneak to strip)";
    public static final String AMETHYST_MESSAGE = "Survival Guards: budding amethyst is protected (sneak to mine it)";

    public final BoolOption antiToolBreak = addOption(new BoolOption("Anti Tool Break", true));
    public final SliderOption durabilityLeft = addOption(new SliderOption("Durability Left", 5, 1, 50, 1));
    public final BoolOption antiStripLog = addOption(new BoolOption("Anti Strip Log", true));
    public final BoolOption antiAmethyst = addOption(new BoolOption("Anti Budding Amethyst", true));
    private final java.util.Set<String> notHere = new java.util.HashSet<>();
    private static SurvivalGuardsModule instance;

    public SurvivalGuardsModule() {
        super(NAME, "Stops your own click when it would cost you: breaking blocks or hitting with a tool that has only Durability Left uses "
            + "left, stripping a log with an axe, or mining budding amethyst. Sneak to do it anyway.");
        setEnabled(false);
    }

    public static SurvivalGuardsModule get() {
        SurvivalGuardsModule m = instance;
        if (m == null && ModuleManager.getInstance().getModule(NAME) instanceof SurvivalGuardsModule found) instance = m = found;
        return m;
    }

    /** An adapter's game version has no such thing (1.8.9: no stripping, no amethyst): the menu leaves it out. */
    public void hideHere(String... options) { notHere.addAll(java.util.List.of(options)); }

    public boolean hidden(Option option) { return notHere.contains(option.getName()); }

    /** Breaking a block or hitting with this item now is stopped: it has maxDamage uses (0: it never wears) and has taken damage. */
    public boolean stopsTool(boolean sneaking, int maxDamage, int damage) {
        return isEnabled() && antiToolBreak.get() && !sneaking && maxDamage > 0 && maxDamage - damage <= (int) durabilityLeft.getValue();
    }

    /** Right-clicking a log or wood that is not stripped yet with an axe is stopped. */
    public boolean stopsStrip(boolean sneaking) { return isEnabled() && antiStripLog.get() && !sneaking; }

    /** Starting to mine budding amethyst is stopped (it never drops, even with Silk Touch). */
    public boolean stopsAmethyst(boolean sneaking) { return isEnabled() && antiAmethyst.get() && !sneaking; }
}
