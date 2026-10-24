package com.thelads.core.client.hud;

import com.thelads.core.client.bridge.LadsGraphics;
import com.thelads.core.config.HudSettings;
import java.util.ArrayList;
import java.util.List;

public class ItemTrackerHudElement extends HudElement {
    public record LogEntry(String name, int delta, long timestamp) {}

    private static final List<LogEntry> LOGS = new ArrayList<>();
    private static final int MAX_HISTORY = 16;

    public static synchronized void recordChange(String name, int delta) {
        if (name == null || name.isBlank() || delta == 0) return;
        LOGS.add(new LogEntry(name, delta, System.currentTimeMillis()));
        if (LOGS.size() > MAX_HISTORY) {
            LOGS.remove(0);
        }
    }

    public ItemTrackerHudElement() {
        this.x = 10;
        this.y = 80;
        this.width = 90;
        this.height = 40;
    }

    @Override
    public void prepareRender(LadsGraphics g, boolean editor) {
        int maxEntries = (int) optSlider("Max entries", 4);
        int lineHeight = g.fontHeight() + 3;
        width = 90;
        height = Math.max(16, maxEntries * lineHeight + 4);
    }

    @Override
    public void render(LadsGraphics g) {
        renderEntries(g, false);
    }

    @Override
    public void renderEditor(LadsGraphics g) {
        renderEntries(g, true);
    }

    private void renderEntries(LadsGraphics g, boolean editor) {
        int maxEntries = (int) optSlider("Max entries", 4);
        long durationMs = (long) (optSlider("Duration (seconds)", 3) * 1000);
        boolean showPickups = optBool("Show pickups", true);
        boolean showDrops = optBool("Show drops", true);

        List<LogEntry> visible = new ArrayList<>();
        long now = System.currentTimeMillis();

        if (editor) {
            visible.add(new LogEntry("Cobblestone", 64, now));
            visible.add(new LogEntry("Diamond", 1, now - 500));
            visible.add(new LogEntry("Ender Pearl", -1, now - 1000));
        } else {
            synchronized (ItemTrackerHudElement.class) {
                for (int i = LOGS.size() - 1; i >= 0 && visible.size() < maxEntries; i--) {
                    LogEntry entry = LOGS.get(i);
                    if (now - entry.timestamp > durationMs) continue;
                    if (entry.delta > 0 && !showPickups) continue;
                    if (entry.delta < 0 && !showDrops) continue;
                    visible.add(entry);
                }
            }
        }

        if (visible.isEmpty()) return;

        drawBackground(g);
        boolean shadow = HudSettings.getInstance().isTextShadow();
        int lineHeight = g.fontHeight() + 3;

        for (int i = 0; i < visible.size(); i++) {
            LogEntry entry = visible.get(i);
            long age = now - entry.timestamp;
            float alphaFactor = editor ? 1.0f : Math.max(0.1f, 1.0f - (float) age / durationMs);
            int alpha = (int) (255 * alphaFactor);

            String sign = entry.delta > 0 ? "+" : "";
            String text = sign + entry.delta + " " + entry.name;
            int baseRgb = entry.delta > 0 ? 0x55FF55 : 0xFF5555;
            int color = (alpha << 24) | baseRgb;

            g.drawText(text, x + 4, y + 3 + i * lineHeight, color, shadow);
        }
    }
}
