package com.thelads.core.client.hud;

import com.thelads.core.client.bridge.LadsGraphics;
import java.util.Arrays;

public class FPSHudElement extends TextHudElement {
    private static final long[] INTERVALS = { 0L, 150L, 400L, 1000L };
    private static final int HISTORY_SIZE = 120;

    private double displayed = 0;
    private double target = 0;
    private long lastSample = 0;

    private final int[] history = new int[HISTORY_SIZE];
    private int historyHead = 0;
    private int historyCount = 0;
    private long lastHistorySample = 0;

    public FPSHudElement() {
        super(60);
        this.x = 5;
        this.y = 5;
        this.width = 60;
        this.height = 16;
    }

    private int lastGameVal = -1;
    private int lastHudVal = -1;
    private int lastMode = -1;
    private int lastStats = -1;
    private String cachedText = "";

    @Override
    protected String updateText(LadsGraphics g) {
        int fps = g.getGame().getFps();

        int rate = optCycle("Update rate", 1);
        boolean smooth = optBool("Smooth", true);
        long interval = INTERVALS[Math.max(0, Math.min(INTERVALS.length - 1, rate))];

        long now = System.currentTimeMillis();
        if (now - lastSample >= interval) {
            target = fps;
            lastSample = now;
        }
        // 10% of the way each time it is drawn; a capped build stands for several draws (HudFrameCap.steps), so it keeps its pace.
        displayed += (target - displayed) * (smooth ? 1 - Math.pow(0.9, HudFrameCap.steps()) : 1.0);

        if (now - lastHistorySample >= 100L) {
            lastHistorySample = now;
            history[historyHead] = fps;
            historyHead = (historyHead + 1) % HISTORY_SIZE;
            if (historyCount < HISTORY_SIZE) historyCount++;
        }

        int mode = optCycle("Display", 0);
        int stats = optCycle("Stats", 0); // 0 = Off, 1 = Min / Max, 2 = Average, 3 = 1% Low, 4 = Full
        int gameVal = (int) Math.round(displayed);
        int hudVal = HudManager.getInstance().getMeasuredHudFps();

        if (gameVal == lastGameVal && hudVal == lastHudVal && mode == lastMode && stats == lastStats) {
            return cachedText;
        }
        lastGameVal = gameVal;
        lastHudVal = hudVal;
        lastMode = mode;
        lastStats = stats;

        String base;
        if (mode == 1) base = hudVal + " HUD FPS";
        else if (mode == 2) base = gameVal + " FPS · " + hudVal + " HUD";
        else base = gameVal + " FPS";

        if (stats == 0 || historyCount < 5) {
            return cachedText = base;
        }

        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        long sum = 0;
        int[] sorted = new int[historyCount];
        for (int i = 0; i < historyCount; i++) {
            int v = history[i];
            if (v < min) min = v;
            if (v > max) max = v;
            sum += v;
            sorted[i] = v;
        }
        int avg = (int) (sum / historyCount);
        Arrays.sort(sorted);
        int low1Pct = sorted[Math.max(0, (int) (historyCount * 0.05))];

        return cachedText = switch (stats) {
            case 1 -> base + " (" + min + " min, " + max + " max)";
            case 2 -> base + " (avg " + avg + ")";
            case 3 -> base + " (1% " + low1Pct + ")";
            case 4 -> base + " [" + min + "-" + max + ", avg " + avg + "]";
            default -> base;
        };
    }
}
