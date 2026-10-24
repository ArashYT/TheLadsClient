package com.thelads.core.client.hud;

import com.thelads.core.client.bridge.LadsGraphics;
import java.util.Locale;

public class TpsHudElement extends TextHudElement {
    private double lastTps = -1;
    private int lastFormat = -1;
    private int lastDecimals = -1;
    private String cachedText = "";

    public TpsHudElement() {
        super(50);
        this.x = 5;
        this.y = 80;
        this.width = 60;
        this.height = 16;
    }

    @Override
    protected String updateText(LadsGraphics g) {
        double tps = g.getGame().getTps();
        int format = optCycle("Format", 0); // 0 = 20.0 TPS, 1 = TPS: 20.0, 2 = Number only
        int decimals = (int) optSlider("Decimals", 1);

        if (editor && tps <= 0) {
            tps = 20.0;
        }

        if (Double.compare(tps, lastTps) == 0 && format == lastFormat && decimals == lastDecimals) {
            return cachedText;
        }

        lastTps = tps;
        lastFormat = format;
        lastDecimals = decimals;

        String pattern = "%." + Math.max(0, Math.min(2, decimals)) + "f";
        String valStr = String.format(Locale.ROOT, pattern, tps);

        return cachedText = switch (format) {
            case 1 -> "TPS: " + valStr;
            case 2 -> valStr;
            default -> valStr + " TPS";
        };
    }
}
