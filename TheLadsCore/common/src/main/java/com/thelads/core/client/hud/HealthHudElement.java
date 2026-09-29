package com.thelads.core.client.hud;

import com.thelads.core.client.bridge.LadsGraphics;
import java.util.Locale;

public class HealthHudElement extends TextHudElement {
    private String cachedText;
    private int cachedHealth, cachedMax, cachedAbsorption, cachedFormat;
    private boolean cachedShowAbsorption;
    public HealthHudElement() {
        super(50);
        this.x = 5;
        this.y = 205;
        this.width = 75;
        this.height = 16;
    }

    @Override
    protected String updateText(LadsGraphics g) {
        float hp = g.getGame().getHealth();
        float max = g.getGame().getMaxHealth();
        int fmt = optCycle("Format", 0);
        boolean showAbsorption = optBool("Show absorption", true);
        float absorption = showAbsorption ? g.getGame().getAbsorption() : -1;
        int healthBits = Float.floatToIntBits(hp), maxBits = Float.floatToIntBits(max);
        int absorptionBits = Float.floatToIntBits(absorption);
        if (cachedText == null || cachedHealth != healthBits || cachedMax != maxBits
                || cachedAbsorption != absorptionBits || cachedFormat != fmt || cachedShowAbsorption != showAbsorption) {
            cachedText = formatHealth(hp, max, fmt, absorption, showAbsorption);
            cachedHealth = healthBits;
            cachedMax = maxBits;
            cachedAbsorption = absorptionBits;
            cachedFormat = fmt;
            cachedShowAbsorption = showAbsorption;
        }
        return cachedText;
    }

    private static String formatHealth(float hp, float max, int fmt, float absorption, boolean showAbsorption) {
        String text;
        if (fmt == 3) {
            text = (int)(max > 0 ? (hp * 100 / max) : 0) + "%";
        } else if (fmt == 2) {
            text = String.valueOf((int) Math.ceil(hp));
        } else if (fmt == 1) {
            text = "HP " + (int) Math.ceil(hp) + "/" + (int) Math.ceil(max);
        } else {
            text = (int) Math.ceil(hp) + "/" + (int) Math.ceil(max);
        }

        if (showAbsorption && Float.isFinite(absorption) && absorption > 0) {
            text += String.format(Locale.ROOT, " (+%.1f absorption)", absorption);
        }
        return text;
    }
}
