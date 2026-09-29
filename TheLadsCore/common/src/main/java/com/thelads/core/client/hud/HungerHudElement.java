package com.thelads.core.client.hud;

import com.thelads.core.client.bridge.LadsGraphics;
import java.util.Locale;

public class HungerHudElement extends TextHudElement {
    private String cachedText;
    private int cachedFood, cachedSaturation;
    private boolean cachedLabel, cachedShowSaturation;
    public HungerHudElement() {
        super(50);
        this.x = 5;
        this.y = 225;
        this.width = 65;
        this.height = 16;
    }

    @Override
    protected String updateText(LadsGraphics g) {
        int food = g.getGame().getFoodLevel();
        boolean label = optBool("Show label", true);
        boolean showSaturation = optBool("Show saturation", false);
        float saturation = showSaturation ? g.getGame().getSaturation() : -1;
        int saturationBits = Float.floatToIntBits(saturation);
        if (cachedText == null || cachedFood != food || cachedLabel != label
                || cachedSaturation != saturationBits || cachedShowSaturation != showSaturation) {
            cachedText = formatFood(food, label, saturation, showSaturation);
            cachedFood = food;
            cachedLabel = label;
            cachedSaturation = saturationBits;
            cachedShowSaturation = showSaturation;
        }
        return cachedText;
    }

    private static String formatFood(int food, boolean label, float saturation, boolean showSaturation) {
        String text = (label ? "Food: " : "") + food + "/20";
        if (showSaturation) {
            text += Float.isFinite(saturation) && saturation >= 0
                    ? String.format(Locale.ROOT, " | Sat: %.1f", saturation) : " | Sat: unavailable";
        }
        return text;
    }
}
