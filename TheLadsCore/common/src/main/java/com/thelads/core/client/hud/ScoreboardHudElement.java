package com.thelads.core.client.hud;

import com.thelads.core.client.bridge.LadsGraphics;
import com.thelads.core.client.bridge.LadsGameBridge;
import com.thelads.core.client.bridge.LadsGameBridge.ScoreLine;
import com.thelads.core.client.bridge.LadsGameBridge.ScoreboardSnapshot;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.SliderOption;
import java.util.List;

public class ScoreboardHudElement extends HudElement {
    private static final int MAX_ROWS = 15;
    private static final int PADDING = 4;
    private static final int VALUE_GAP = 10;
    private static final ScoreboardSnapshot EDITOR_SAMPLE = new ScoreboardSnapshot("Scoreboard preview (sample)",
            List.of(new ScoreLine("Sample player", "12"), new ScoreLine("Sample team", "8")));
    private final int[] valueWidths = new int[MAX_ROWS];
    private int titleWidth;
    private boolean measuredHideValues;
    private LadsGraphics preparedGraphics;
    /** What the widths above were measured for: the snapshot (bridges keep one per tick) and the font metrics. */
    private ScoreboardSnapshot measuredSnapshot;
    private Object measuredKey;

    public ScoreboardHudElement() {
        this.x = 200;
        this.y = 80;
        this.width = 120;
        this.height = 100;
    }

    @Override
    public void render(LadsGraphics g) {
        renderSnapshot(g, false);
    }

    @Override
    public void renderEditor(LadsGraphics g) {
        renderSnapshot(g, true);
    }

    /** Native sidebar hooks must use this guard, never the enabled flag by itself. */
    public static boolean shouldReplaceVanillaScoreboard() {
        var module = ModuleManager.getInstance().getModule("Scoreboard");
        if (module == null || !module.isEnabled()) return false;
        LadsGameBridge bridge = LadsGameBridge.get();
        return bridge != null && !bridge.isHudHidden() && hasObjective(bridge.getScoreboard());
    }

    private static boolean hasObjective(ScoreboardSnapshot snapshot) {
        return snapshot != null && !snapshot.lines().isEmpty();
    }

    private static ScoreboardSnapshot liveSnapshot(LadsGraphics g) {
        return g.getGame() == null || g.getGame().isHudHidden() ? null : g.getGame().getScoreboard();
    }

    @Override
    public void prepareRender(LadsGraphics g, boolean editor) {
        ScoreboardSnapshot snapshot = liveSnapshot(g);
        ensureMeasured(g, editor && !hasObjective(snapshot) ? EDITOR_SAMPLE : snapshot);
        preparedGraphics = g;
    }

    /** Measures again only for another snapshot, font or Hide setting: the same lines are not re-measured every frame. */
    private void ensureMeasured(LadsGraphics g, ScoreboardSnapshot snapshot) {
        Object key = g.textMetricsKey();
        if (snapshot == measuredSnapshot && measuredHideValues == shouldHideValues(snapshot)
                && (g == preparedGraphics || key != null && key == measuredKey)) return;
        measure(g, snapshot);
        measuredSnapshot = snapshot;
        measuredKey = key;
    }

    private boolean shouldHideValues(ScoreboardSnapshot snapshot) {
        if (optBool("Hide Red Numbers", false)) return true;
        if (optBool("Hide Sequential Only", false) && snapshot != null && isSequential(snapshot.lines())) return true;
        return false;
    }

    private static boolean isSequential(List<ScoreLine> lines) {
        if (lines == null || lines.size() < 2) return false;
        int count = Math.min(MAX_ROWS, lines.size());
        if (count < 2) return false;
        int[] vals = new int[count];
        for (int i = 0; i < count; i++) {
            String v = lines.get(i).value();
            if (v == null || v.trim().isEmpty()) return false;
            try {
                // 26.x values carry the sidebar's section-sign colour codes ("§r§c15").
                vals[i] = Integer.parseInt(stripCodes(v).trim());
            } catch (NumberFormatException e) {
                return false;
            }
        }
        int diff = vals[1] - vals[0];
        if (diff != 1 && diff != -1) return false;
        for (int i = 2; i < count; i++) {
            if (vals[i] - vals[i - 1] != diff) return false;
        }
        return true;
    }

    /** The text without its section-sign codes ("§r§c15" is "15"), without a regex per line per frame. */
    private static String stripCodes(String value) {
        if (value.indexOf('§') < 0) return value;
        StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) == '§') i++;
            else out.append(value.charAt(i));
        }
        return out.toString();
    }

    private void measure(LadsGraphics g, ScoreboardSnapshot snapshot) {
        measuredHideValues = shouldHideValues(snapshot);
        if (!hasObjective(snapshot)) {
            width = 120;
            height = 16;
            return;
        }
        boolean hideValues = measuredHideValues;
        int nameWidth = 0;
        int valueWidth = 0;
        int count = Math.min(MAX_ROWS, snapshot.lines().size());
        for (int i = 0; i < count; i++) {
            ScoreLine line = snapshot.lines().get(i);
            nameWidth = Math.max(nameWidth, g.textWidth(line.name()));
            valueWidths[i] = hideValues ? 0 : g.textWidth(line.value());
            valueWidth = Math.max(valueWidth, valueWidths[i]);
        }
        int rowWidth = nameWidth + (valueWidth == 0 ? 0 : VALUE_GAP + valueWidth);
        titleWidth = g.textWidth(snapshot.title());
        width = Math.max(titleWidth, rowWidth) + PADDING * 2;
        height = (count + 1) * (g.fontHeight() + 2) + PADDING * 2;
    }

    private void renderSnapshot(LadsGraphics g, boolean editor) {
        // Clearing/replacing an objective must be visible even between preparation and drawing.
        // Native bridges cache this snapshot per tick; only repeat measurement if it changed.
        ScoreboardSnapshot snapshot = liveSnapshot(g);
        if (editor && !hasObjective(snapshot)) snapshot = EDITOR_SAMPLE;
        ensureMeasured(g, snapshot);
        preparedGraphics = null;
        if (!hasObjective(snapshot)) return;
        drawBackground(g);
        // Its own shadow option, on by default, whatever the global HUD shadow (1.7.0).
        boolean shadow = optBool("Text Shadow", true);
        boolean hideValues = measuredHideValues;
        boolean light = optCycle("Background", 0) == 2;
        // The sidebar's own colours (formatting codes over white names and red scores), as vanilla draws them, unless Custom Text Color.
        int textColor = light ? 0xFF202020 : optBool("Custom Text Color", false) ? resolveColor() : 0xFFFFFFFF;
        int valueColor = light ? 0xFFAA0000 : 0xFFFF5555;
        int lineHeight = g.fontHeight() + 2;
        g.drawText(snapshot.title(), x + (width - titleWidth) / 2, y + PADDING, textColor, shadow);
        int count = Math.min(MAX_ROWS, snapshot.lines().size());
        for (int i = 0; i < count; i++) {
            ScoreLine line = snapshot.lines().get(i);
            int lineY = y + PADDING + (i + 1) * lineHeight;
            g.drawText(line.name(), x + PADDING, lineY, textColor, shadow);
            if (!hideValues && !line.value().isEmpty()) {
                g.drawText(line.value(), x + width - PADDING - valueWidths[i], lineY, valueColor, shadow);
            }
        }
    }

    @Override
    protected int resolveBackground() {
        if (!com.thelads.core.config.HudSettings.getInstance().isBackgrounds()) return 0;
        return switch (optCycle("Background", 0)) {
            case 1 -> 0xC0000000;
            case 2 -> 0xC0FFFFFF;
            case 3 -> 0x00000000;
            default -> super.resolveBackground();
        };
    }

    private int offset(String name) {
        var module = ModuleManager.getInstance().getModule(moduleName);
        if (module != null && module.getOption(name) instanceof SliderOption slider) {
            double value = slider.getValue();
            return Double.isFinite(value) ? (int) Math.max(-40, Math.min(40, Math.round(value))) : 0;
        }
        return 0;
    }

    @Override
    public int getDisplayX(LadsGraphics g) { return x + offset("X Offset"); }

    @Override
    public int getDisplayY(LadsGraphics g) { return y + offset("Y Offset"); }

    @Override
    public void setDisplayPosition(int displayX, int displayY) {
        setPosition(displayX - offset("X Offset"), displayY - offset("Y Offset"));
    }
}
