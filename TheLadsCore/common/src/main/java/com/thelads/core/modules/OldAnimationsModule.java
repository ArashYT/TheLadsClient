package com.thelads.core.modules;

import com.thelads.core.client.OldAnimations.Held;
import com.thelads.core.client.OldAnimations.Use;
import com.thelads.core.config.BoolOption;
import com.thelads.core.config.Module;
import java.util.EnumMap;
import java.util.Map;

/**
 * Minecraft 1.7 animations. Adapters ask {@link #active} (or the helpers below) and replay the recipes in
 * {@link com.thelads.core.client.OldAnimations}. Until an adapter registers it built in, the catalog lists it as pending.
 */
public final class OldAnimationsModule extends Module {
    public static final String NAME = "1.7 Animations";

    /** Where an option can work: Forge 1.8.9, or the modern Fabric versions (1.21.x and 26.x). */
    public enum Platform { V1_8_9, MODERN }

    /** One toggle each; the option name is the saved key. */
    public enum Feature {
        BLOCKHIT("Blockhitting", "Swinging while blocking shows the 1.7 swing on the blocking sword. On 1.21 and 26.x "
            + "this works while you block with a shield: the shield is hidden and the sword blocks.", null),
        BLOCK_POSE("1.7 sword block pose", "In first person the blocking sword sits where 1.7 held it. "
            + "On 1.21 and 26.x it uses the same shield trigger as Blockhitting.", null),
        BOW("1.7 bow position", "In first person the bow is held and drawn as in 1.7.", null),
        ROD("1.7 fishing rod position", "In first person fishing rods are held as in 1.7.", null),
        EAT_DRINK("1.7 eating and drinking", "In first person food and potions move as in 1.7 while you eat or drink.", null),
        SWING_WHILE_USING("Swing while using items", "Attacking while you eat, drink or draw a bow still shows the swing, as in 1.7.", null),
        RED_ARMOUR("Red armour on hurt", "Armour turns red with the player when hurt, as in 1.7.", null),
        INSTANT_SNEAK("Instant sneak camera", "The camera reaches sneak height within one tick, as in 1.7, and eases back up.", null),
        NO_HEART_FLASH("No heart flashing", "Health hearts do not flash when you take damage.", null),
        THIRD_PERSON("1.7 third-person items", "Held items and the blocking arm look as they did in 1.7, "
            + "in third person and on other players.", null),
        DROPPED_2D("2D dropped items", "Dropped items are flat icons that turn to face you, as in 1.7 on Fast graphics.", null),
        NO_COOLDOWN_DIP("No attack-cooldown dip", "The held item stays up after an attack instead of dipping while "
            + "the attack cooldown recharges.", "Minecraft 1.8.9 has no attack cooldown, so the item never dips."),
        LOW_FIRE("Low Fire", "Lowers the first-person fire overlay when burning so it blocks less of your screen.", null),
        LOW_SHIELD("Low Shield", "Lowers held shields in first person to improve visibility.", "Minecraft 1.8.9 has no shields.");

        public final String option;
        public final String tooltip;
        private final String notOn189;

        Feature(String option, String tooltip, String notOn189) {
            this.option = option;
            this.tooltip = tooltip;
            this.notOn189 = notOn189;
        }

        public boolean appliesTo(Platform platform) {
            return platform == Platform.MODERN || notOn189 == null;
        }

        /** Why the option does nothing on this platform (hide it or show it unavailable), or null when it applies. */
        public String unavailableReason(Platform platform) {
            return appliesTo(platform) ? null : notOn189;
        }
    }

    private final Map<Feature, BoolOption> toggles = new EnumMap<>(Feature.class);

    public OldAnimationsModule() {
        super(NAME, "Minecraft 1.7 animations: blockhitting, the 1.7 sword, bow, rod and eating poses, red armour, "
            + "2D dropped items and more.");
        for (Feature feature : Feature.values()) toggles.put(feature, addOption(new BoolOption(feature.option, true)));
    }

    public BoolOption option(Feature feature) {
        return toggles.get(feature);
    }

    /** The module is on, the option is on and it exists on this platform. */
    public boolean active(Feature feature, Platform platform) {
        return isEnabled() && feature.appliesTo(platform) && toggles.get(feature).get();
    }

    /** The swing to pass to OldAnimations.hand: 1.7 kept the swing turn while using items, 1.8 dropped it. */
    public float swingShown(Platform platform, Use use, float swing) {
        if (use == Use.NONE) return swing;
        return active(use == Use.BLOCK ? Feature.BLOCKHIT : Feature.SWING_WHILE_USING, platform) ? swing : 0;
    }

    /** First person: draw this flat item with OldAnimations.item (and no display transform) instead of vanilla's placement. */
    public boolean iconPlacement(Platform platform, Use use, Held held) {
        switch (use) {
            case BLOCK: return active(Feature.BLOCK_POSE, platform);
            case EAT_DRINK: return active(Feature.EAT_DRINK, platform);
            case BOW: return active(Feature.BOW, platform);
            default: return held == Held.BOW ? active(Feature.BOW, platform) : held == Held.ROD && active(Feature.ROD, platform);
        }
    }

    /** Red armour on hurt: give armour the body's hurt tint, which vanilla shows while hurtTime or deathTime is above 0. */
    public boolean tintArmour(Platform platform, int hurtTime, int deathTime) {
        return (hurtTime > 0 || deathTime > 0) && active(Feature.RED_ARMOUR, platform);
    }

    /** No heart flashing: vanilla's health-bar blink flag, cleared while the option is active. */
    public boolean heartsBlink(Platform platform, boolean vanillaBlink) {
        return vanillaBlink && !active(Feature.NO_HEART_FLASH, platform);
    }

    /** No attack-cooldown dip: the attack or swap scale the equip animation reads; 1 keeps the item up. */
    public float equipScale(Platform platform, float vanillaScale) {
        return active(Feature.NO_COOLDOWN_DIP, platform) ? 1 : vanillaScale;
    }
}
