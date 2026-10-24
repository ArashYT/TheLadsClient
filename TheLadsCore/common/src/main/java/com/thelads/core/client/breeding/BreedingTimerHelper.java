package com.thelads.core.client.breeding;

import com.thelads.core.config.ModuleManager;
import com.thelads.core.modules.BreedingTimerModule;

public final class BreedingTimerHelper {
    private static BreedingTimerModule cachedModule;

    private BreedingTimerHelper() {}

    public static BreedingTimerModule module() {
        BreedingTimerModule m = cachedModule;
        if (m == null) {
            if (ModuleManager.getInstance().getModule(BreedingTimerModule.NAME) instanceof BreedingTimerModule found) {
                cachedModule = m = found;
            }
        }
        return m;
    }

    public static boolean isEnabled() {
        BreedingTimerModule m = module();
        return m != null && m.isEnabled();
    }

    public static String formatTimer(int ageInTicks) {
        if (ageInTicks == 0) return null;
        int totalSeconds = Math.abs(ageInTicks) / 20;
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        String prefix = ageInTicks < 0 ? "Baby: " : "Ready: ";
        return prefix + String.format("%d:%02d", minutes, seconds);
    }
}
