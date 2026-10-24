package com.thelads.core.client;

import com.thelads.core.config.ModuleManager;

/**
 * Suppresses the Elder Guardian ghost apparition animation from covering the player's screen
 * when Mining Fatigue is inflicted.
 */
public final class ElderGuardianTweaks {
    private static volatile boolean hideCurse = true;

    private ElderGuardianTweaks() {}

    public static void setHideCurse(boolean hide) {
        hideCurse = hide;
    }

    public static boolean isCurseHidden() {
        return RenderTweaks.isElderGuardianCurseHidden() || hideCurse;
    }
}
