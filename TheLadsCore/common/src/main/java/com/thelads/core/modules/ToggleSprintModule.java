package com.thelads.core.modules;

import com.thelads.core.config.ActionOption;
import com.thelads.core.config.BoolOption;
import com.thelads.core.config.DropdownOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.Option;

/**
 * Toggle Sprint and Toggle Sneak in one module (1.7.0; ConfigManager.migrateToggles keeps 1.6.0's two modules' settings).
 * Adapters feed {@link #sprintInput} and {@link #sneakInput} into Minecraft's own sprint and sneak key reads and never call
 * setSprinting(true): each version's own rules (walls, knockback, food, item use, blindness, water, flying) decide whether a
 * held sprint key sprints, so a toggled sprint behaves exactly like a held one and cannot fight them tick after tick.
 * Toggle Sprint and Toggle Sneak keys: unbound, the Sprint and Sneak keys toggle; bound, those keys toggle and Sprint and Sneak
 * are plain hold keys again. The toggled states are options, saved with the config, so death, another world or server,
 * disconnecting and restarting never clear them.
 */
public class ToggleSprintModule extends Module {
    public static final String NAME = "Toggle Sprint & Sneak";
    public static final int TOGGLE = 0, ALWAYS = 1, VANILLA = 2; // Sprint; Sneak is TOGGLE or 1 (Vanilla)
    private final DropdownOption sprintMode, sneakMode;
    private final BoolOption pauseWhileSneaking, sprintToggled, sneakToggled;
    /** Shows the key and opens Controls; adapters set both. */
    public final ActionOption sprintKey, sneakKey;
    private boolean owned, sprinting, sneaking, sprintKeyDown;

    public ToggleSprintModule() {
        super(NAME, "Sprint and sneak without holding the keys. With Toggle Sprint and Toggle Sneak unbound in Controls, "
            + "the Sprint and Sneak keys toggle; bind them to other keys to keep Sprint and Sneak for holding.");
        sprintMode = addOption(new DropdownOption("Sprint", TOGGLE, "Toggle", "Always", "Vanilla"));
        pauseWhileSneaking = addOption(new BoolOption("Pause sprint while sneaking", true));
        sneakMode = addOption(new DropdownOption("Sneak", 1, "Toggle", "Vanilla"));
        sprintKey = addOption(new ActionOption("Toggle Sprint key", "Controls"));
        sneakKey = addOption(new ActionOption("Toggle Sneak key", "Controls"));
        sprintToggled = addOption(new BoolOption("Sprint toggled", false));
        sneakToggled = addOption(new BoolOption("Sneak toggled", false));
    }

    /** The saved toggle states are not settings: the menu leaves them out. */
    public boolean hidden(Option option) { return option == sprintToggled || option == sneakToggled; }

    public boolean isSprintToggled() { return sprintToggled.get(); }
    public boolean isSneakToggled() { return sneakToggled.get(); }

    /** The toggle key went down (Sprint itself while Toggle Sprint is unbound). True when the state changed: save the config. */
    public boolean pressSprint() {
        if (!isEnabled() || sprintMode.getIndex() != TOGGLE) return false;
        sprintToggled.toggle();
        return true;
    }

    public boolean pressSneak() {
        if (!isEnabled() || sneakMode.getIndex() != TOGGLE) return false;
        sneakToggled.toggle();
        return true;
    }

    /** What Minecraft reads as the Sneak key: the toggle, plus the held key when Toggle Sneak has its own key. */
    public boolean sneakInput(boolean keyDown, boolean separateKey) {
        if (!isEnabled() || sneakMode.getIndex() != TOGGLE) return keyDown;
        return sneakToggled.get() || separateKey && keyDown;
    }

    /** What Minecraft reads as the Sprint key; Minecraft still decides whether the player may sprint. */
    public boolean sprintInput(boolean keyDown, boolean separateKey, boolean sneaking) {
        if (!isEnabled() || sprintMode.getIndex() == VANILLA) return keyDown;
        return owns(sneaking) || separateKey && keyDown;
    }

    private boolean owns(boolean sneaking) {
        return isEnabled() && sprintMode.getIndex() != VANILLA && (sprintMode.getIndex() == ALWAYS || sprintToggled.get())
            && !(sneaking && pauseWhileSneaking.get());
    }

    /**
     * Once per tick: true when the sprint this module asked for just ended (toggled off, paused by sneaking, module off). Minecraft
     * keeps a sprint going after its key is released, so the adapter then stops it, once; a sprint Minecraft itself stopped (a
     * wall, a hit, hunger) is never touched.
     */
    public boolean sprintEnded(boolean keyDown, boolean separateKey, boolean sneaking) {
        boolean now = owns(sneaking), ended = owned && !now && !(separateKey && keyDown);
        owned = now;
        return ended;
    }

    /** Each tick, the player's state for the HUD line. */
    public void observe(boolean sprinting, boolean sneaking, boolean sprintKeyDown) {
        this.sprinting = sprinting;
        this.sneaking = sneaking;
        this.sprintKeyDown = sprintKeyDown;
    }

    /** The HUD line: what is toggled or held right now, sprint then sneak; empty when neither. */
    public String status() {
        String sprint = isEnabled() && sprintMode.getIndex() == TOGGLE && sprintToggled.get() ? "Toggled"
            : !sprinting ? null : isEnabled() && sprintMode.getIndex() == ALWAYS ? "Always" : sprintKeyDown ? "Key Held" : "Vanilla";
        String sneak = isEnabled() && sneakMode.getIndex() == TOGGLE && sneakToggled.get() ? "Toggled" : sneaking ? "Key Held" : null;
        String text = sprint == null ? "" : "[Sprinting (" + sprint + ")]";
        if (sneak != null) text += (text.isEmpty() ? "" : " ") + "[Sneaking (" + sneak + ")]";
        return text;
    }
}
