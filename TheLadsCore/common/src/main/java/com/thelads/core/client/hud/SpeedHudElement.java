package com.thelads.core.client.hud;

import com.thelads.core.client.bridge.LadsGraphics;
import java.util.Locale;

public class SpeedHudElement extends TextHudElement {
    private String cachedText;
    private long cachedSpeed;
    private int cachedUnit, cachedPrecision;
    public SpeedHudElement() {
        super(60);
        this.x = 5;
        this.y = 145;
        this.width = 75;
        this.height = 16;
    }

    @Override
    protected String updateText(LadsGraphics g) {
        double spd = g.getGame().getSpeed();
        int unit = optCycle("Unit", 0); // 0 = b/s, 1 = km/h
        double val = (unit == 1) ? spd * 3.6 : spd;
        String uStr = (unit == 1) ? " km/h" : " b/s";
        int precision = Math.max(0, Math.min(2, optCycle("Precision", 1)));
        long speedBits = Double.doubleToLongBits(spd);
        if (cachedText == null || cachedSpeed != speedBits || cachedUnit != unit || cachedPrecision != precision) {
            cachedText = String.format(Locale.ROOT, "%." + precision + "f%s", val, uStr);
            cachedSpeed = speedBits;
            cachedUnit = unit;
            cachedPrecision = precision;
        }
        return cachedText;
    }
}
