package com.thelads.core.client.hud;

import com.thelads.core.client.DeathTracker;
import com.thelads.core.client.bridge.LadsGraphics;

public class DeathInfoHudElement extends TextHudElement {
    private String cachedText = "";
    private int lastDeaths = -1, lastX = 0, lastY = 0, lastZ = 0;
    private int lastDisplay = -1;
    private boolean lastShowDim = false;

    public DeathInfoHudElement() {
        super(70);
        this.x = 5;
        this.y = 290;
        this.width = 85;
        this.height = 16;
    }

    @Override
    protected String updateText(LadsGraphics g) {
        DeathTracker dt = DeathTracker.get();
        int deaths = dt.getDeathCount();
        int dx = dt.getLastX(), dy = dt.getLastY(), dz = dt.getLastZ();
        String dim = dt.getLastDimension();
        String cause = dt.getLastCause();

        if (editor && deaths == 0) {
            deaths = 2;
            dx = 120;
            dy = 64;
            dz = -200;
            dim = "minecraft:overworld";
            cause = "Fell from a high place";
        }

        int display = optCycle("Display", 0); // 0 = Deaths & Coords, 1 = Deaths only, 2 = Last Coords, 3 = Full
        boolean showDim = optBool("Show dimension", false);

        if (deaths == lastDeaths && dx == lastX && dy == lastY && dz == lastZ
                && display == lastDisplay && showDim == lastShowDim) {
            return cachedText;
        }

        lastDeaths = deaths;
        lastX = dx;
        lastY = dy;
        lastZ = dz;
        lastDisplay = display;
        lastShowDim = showDim;

        String coords = dx + ", " + dy + ", " + dz;
        if (showDim && !dim.isEmpty()) coords += " (" + dim + ")";

        return cachedText = switch (display) {
            case 1 -> "Deaths: " + deaths;
            case 2 -> "Last Death: " + coords;
            case 3 -> "Deaths: " + deaths + " [" + coords + (cause.isEmpty() ? "" : ": " + cause) + "]";
            default -> "Deaths: " + deaths + " · Last: " + coords;
        };
    }
}
