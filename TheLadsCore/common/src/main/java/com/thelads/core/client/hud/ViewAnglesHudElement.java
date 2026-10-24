package com.thelads.core.client.hud;

import com.thelads.core.client.bridge.LadsGraphics;
import java.util.Locale;

public class ViewAnglesHudElement extends TextHudElement {
    private float lastYaw = Float.NaN;
    private float lastPitch = Float.NaN;
    private int lastFmt = -1;
    private int lastDecimals = -1;
    private String cachedText = "";

    public ViewAnglesHudElement() {
        super(60);
        this.x = 5;
        this.y = 230;
        this.width = 85;
        this.height = 16;
    }

    @Override
    protected String updateText(LadsGraphics g) {
        float rawYaw = g.getGame().getYaw();
        float rawPitch = g.getGame().getPitch();
        int fmt = optCycle("Format", 0);
        int decimals = (int) optSlider("Decimals", 1);

        if (editor || !Float.isFinite(rawYaw)) {
            rawYaw = 142.3f;
            rawPitch = -12.4f;
        }

        float yaw = ((rawYaw % 360.0f) + 360.0f) % 360.0f;
        float pitch = rawPitch;

        if (Float.compare(yaw, lastYaw) == 0 && Float.compare(pitch, lastPitch) == 0
                && fmt == lastFmt && decimals == lastDecimals) {
            return cachedText;
        }

        lastYaw = yaw;
        lastPitch = pitch;
        lastFmt = fmt;
        lastDecimals = decimals;

        String pattern = "%." + Math.max(0, Math.min(2, decimals)) + "f";
        String yawStr = String.format(Locale.ROOT, pattern, yaw);
        String pitchStr = String.format(Locale.ROOT, pattern, pitch);

        return cachedText = switch (fmt) {
            case 1 -> "Y: " + yawStr + "° P: " + pitchStr + "°";
            case 2 -> "(" + yawStr + ", " + pitchStr + ")";
            case 3 -> "Yaw: " + yawStr + "°";
            case 4 -> "Pitch: " + pitchStr + "°";
            default -> "Yaw: " + yawStr + "°  Pitch: " + pitchStr + "°";
        };
    }
}
