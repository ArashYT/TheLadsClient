package com.thelads.core.client.hud;

import com.thelads.core.client.bridge.LadsGraphics;
import java.util.Locale;

public class SessionPlaytimeHudElement extends TextHudElement {
    private static final long SESSION_START = System.currentTimeMillis();

    private long lastSecond = -1;
    private int lastFmt = -1;
    private int lastPrefix = -1;
    private String cachedText = "";

    public SessionPlaytimeHudElement() {
        super(60);
        this.x = 5;
        this.y = 210;
        this.width = 75;
        this.height = 16;
    }

    @Override
    protected String updateText(LadsGraphics g) {
        long elapsedSec = Math.max(0, (System.currentTimeMillis() - SESSION_START) / 1000);
        int fmt = optCycle("Format", 0); // 0 = HH:mm:ss, 1 = Words (1h 24m), 2 = Minutes only
        int prefixIdx = optCycle("Prefix", 0); // 0 = Playtime: , 1 = Session: , 2 = Uptime: , 3 = None

        if (editor && elapsedSec < 10) {
            elapsedSec = 5025; // 1h 23m 45s
        }

        if (elapsedSec == lastSecond && fmt == lastFmt && prefixIdx == lastPrefix) {
            return cachedText;
        }

        lastSecond = elapsedSec;
        lastFmt = fmt;
        lastPrefix = prefixIdx;

        long hours = elapsedSec / 3600;
        long minutes = (elapsedSec % 3600) / 60;
        long seconds = elapsedSec % 60;

        String timeStr = switch (fmt) {
            case 1 -> hours > 0 ? String.format(Locale.ROOT, "%dh %dm", hours, minutes) : String.format(Locale.ROOT, "%dm %ds", minutes, seconds);
            case 2 -> String.format(Locale.ROOT, "%dm", elapsedSec / 60);
            default -> hours > 0 ? String.format(Locale.ROOT, "%02d:%02d:%02d", hours, minutes, seconds)
                                 : String.format(Locale.ROOT, "%02d:%02d", minutes, seconds);
        };

        String prefix = switch (prefixIdx) {
            case 1 -> "Session: ";
            case 2 -> "Uptime: ";
            case 3 -> "";
            default -> "Playtime: ";
        };

        return cachedText = prefix + timeStr;
    }
}
