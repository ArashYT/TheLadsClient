package com.thelads.core.modules;

import com.thelads.core.config.BoolOption;
import com.thelads.core.config.Module;

/** Lads' own third-person player animations (1.20.1); where the Not Enough Animations mod is installed the card wraps it instead. */
public class NotEnoughAnimationsModule extends Module {
    public static final String NAME = "NotEnoughAnimations";

    /** The animation families, each switchable from the card. */
    public enum Family {
        TRANSITIONS("Smooth Transitions"),
        ITEMS("Item Poses"),
        RIDING("Riding Poses"),
        CLIMBING("Climbing"),
        MOVEMENT("Falling, Crawling and Gliding");

        public final String label;

        Family(String label) {
            this.label = label;
        }
    }

    private final BoolOption[] families = new BoolOption[Family.values().length];

    public NotEnoughAnimationsModule() {
        super(NAME, "Better player third-person animations.");
        setEnabled(true);
        for (Family family : Family.values()) families[family.ordinal()] = addOption(new BoolOption(family.label, true));
    }

    /** The module and this family are on. */
    public boolean on(Family family) {
        return isEnabled() && families[family.ordinal()].get();
    }
}
