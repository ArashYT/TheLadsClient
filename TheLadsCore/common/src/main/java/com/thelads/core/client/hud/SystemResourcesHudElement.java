package com.thelads.core.client.hud;

import com.thelads.core.client.bridge.LadsGraphics;
import java.util.ArrayList;
import java.util.List;

public class SystemResourcesHudElement extends TextHudElement {
    private String cachedText = "";
    private long lastUpdate = 0;
    private int lastCpu = -1;
    private long lastUsedMem = -1;
    private int lastLayout = -1;

    public SystemResourcesHudElement() {
        super(70);
        this.x = 5;
        this.y = 190;
        this.width = 80;
        this.height = 16;
    }

    @Override
    protected String updateText(LadsGraphics g) {
        long now = System.currentTimeMillis();
        boolean showCpu = optBool("Show CPU", true);
        boolean showRam = optBool("Show RAM", true);
        int layout = optCycle("Layout", 0); // 0 = Single line, 1 = Multi-line

        if (!editor && now - lastUpdate < 500L && !cachedText.isEmpty() && layout == lastLayout) {
            return cachedText;
        }
        lastUpdate = now;

        double cpuVal = g.getGame().getCpuLoad();
        int cpu = cpuVal >= 0 ? (int) Math.round(cpuVal) : (editor ? 24 : -1); // -1: unknown, shown as n/a (never a made-up number)
        long used = g.getGame().getUsedMemoryMb();
        long max = g.getGame().getMaxMemoryMb();
        long pct = max > 0 ? (used * 100 / max) : 0;

        if (cpu == lastCpu && used == lastUsedMem && layout == lastLayout && !cachedText.isEmpty()) {
            return cachedText;
        }
        lastCpu = cpu;
        lastUsedMem = used;
        lastLayout = layout;

        List<String> parts = new ArrayList<>();
        if (showCpu) parts.add(cpu >= 0 ? "CPU: " + cpu + "%" : "CPU: n/a");
        if (showRam) parts.add("RAM: " + pct + "% (" + used + "MB)");

        if (parts.isEmpty()) parts.add("Resources");

        return cachedText = layout == 1 ? String.join("\n", parts) : String.join(" · ", parts);
    }
}
