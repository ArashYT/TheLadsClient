package com.thelads.core.mods;

/** GoodMC (retired in 1.2.3) saved its no-cooldown attack_speed base into the player data of every world it ran in. */
public final class GoodMcResidue {
    public static final String MOD_ID = "goodmc";
    /** AttackCooldownMixin.increaseAttackSpeed sets exactly this every tick while its old combat is on. */
    public static final double ATTACK_SPEED_BASE = 32767.0;

    private GoodMcResidue() {}

    /** A loaded GoodMC keeps setting the value itself, so only a world it left behind is reset. */
    public static boolean isResidue(double attackSpeedBase, boolean goodMcLoaded) {
        return !goodMcLoaded && attackSpeedBase == ATTACK_SPEED_BASE;
    }
}
