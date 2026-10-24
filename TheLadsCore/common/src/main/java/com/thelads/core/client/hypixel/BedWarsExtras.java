package com.thelads.core.client.hypixel;

import com.thelads.core.client.bridge.LadsGameBridge;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * BedWarsExtras: Bed Wars quality of life features (hardcore hearts upon bed loss, bed destruction alerts).
 */
public final class BedWarsExtras {
    public static final String NAME = "Bed Wars Extras";
    private static final Pattern BED_DESTROYED = Pattern.compile("(?i)^BED DESTRUCTION > (.+ Bed was destroyed by (?:(?:\\[[^\\]]+\\]\\s*)*(\\w{1,16}))[!.]*)");
    private static final Pattern YOUR_BED = Pattern.compile("(?i).*Your Bed was destroyed.*");

    private static boolean bedLost = false;
    private static String lastAlert = null;
    private static long alertUntil = 0;

    private BedWarsExtras() {}

    public static boolean isEnabled() {
        return HypixelMods.isActiveOnCurrentServer() && HypixelMods.on(NAME);
    }

    public static boolean isHardcoreHearts() {
        if (!isEnabled()) return false;
        String[] loc = HypixelMods.location();
        if (loc != null && !"BEDWARS".equalsIgnoreCase(loc[0])) return false;
        return bedLost && HypixelMods.bedWarsHardcoreHearts();
    }

    public static void setBedLostForTest(boolean lost) {
        bedLost = lost;
    }

    public static void onChat(String message, long nowNanos) {
        if (!isEnabled() || message == null) return;
        String plain = HypixelChat.plain(message);

        if (YOUR_BED.matcher(plain).matches()) {
            bedLost = true;
            triggerAlert("§4§lYOUR BED WAS DESTROYED!", nowNanos);
            return;
        }

        Matcher m = BED_DESTROYED.matcher(plain);
        if (m.find()) {
            String details = m.group(1);
            triggerAlert("§c§l" + details, nowNanos);
        }
    }

    private static void triggerAlert(String alertText, long nowNanos) {
        if (!HypixelMods.bedWarsAlerts()) return;
        lastAlert = alertText;
        alertUntil = nowNanos + 4_000_000_000L; // 4s alert display
        LadsGameBridge bridge = LadsGameBridge.get();
        if (bridge != null) {
            bridge.displayChatMessage(alertText);
        }
    }

    public static String getActiveAlert(long nowNanos) {
        if (lastAlert != null && nowNanos < alertUntil) {
            return lastAlert;
        }
        return null;
    }

    public static void reset() {
        bedLost = false;
        lastAlert = null;
        alertUntil = 0;
    }
}
